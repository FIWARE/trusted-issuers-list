/*
 * Copyright 2023 FIWARE Foundation e.V. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fiware.iam.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TILMapper;
import org.fiware.iam.exception.ConflictException;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.CredentialRepository;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApi;
import org.fiware.iam.til.model.CredentialsVO;
import org.fiware.iam.til.model.IssuerScopeVO;
import org.fiware.iam.til.model.ScopeTypeVO;
import org.fiware.iam.til.model.TrustedIssuerVO;
import org.fiware.iam.til.model.TrustedIssuersListResponseVO;

/** Implementation of the (proprietary) trusted-list api to manage the issuers. */
@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
@Introspected
public class TrustedIssuersListController implements IssuerApi {

  public static final String HREF_TEMPLATE = "/v4/issuers/%s";

  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MIN_PAGE_SIZE = 1;
  private static final int MAX_PAGE_SIZE = 100;
  private static final String SORT_FIELD = "did";
  private static final String NO_SCOPE_PROVIDED =
      "A scope is required - credentials without one cannot be revoked without affecting others.";
  private static final String SCOPE_TYPE_WITHOUT_SCOPE =
      "A scope type requires a scope - unscoped credentials cannot be addressed by type.";

  private static final Comparator<Grant> GRANT_ORDER =
      Comparator.comparing(Grant::scope)
          .thenComparing(Grant::scopeType, Comparator.nullsFirst(Comparator.naturalOrder()));

  /** One grant, keyed null-safely so that untyped credentials group together. */
  private record Grant(String scope, @Nullable String scopeType) {}

  private final TrustedIssuerRepository trustedIssuerRepository;
  private final CredentialRepository credentialRepository;
  private final TILMapper trustedIssuerMapper;
  private final ObjectMapper objectMapper;

  /**
   * Returns a paginated list of DIDs of trusted issuers, sorted alphabetically. With a scope, only
   * the issuers holding that exact grant are listed, so that a reconciliation can find the grants
   * that should no longer exist.
   *
   * @param pageSize maximum number of items per page (1-100, defaults to 10)
   * @param page zero-based page number (defaults to 0)
   * @param scope lists only issuers granted credentials by it, or {@code null} for all issuers
   * @param scopeType the kind of thing the scope identifies, or {@code null} for an untyped grant
   * @return paginated response containing the issuer DIDs
   */
  @Override
  public HttpResponse<TrustedIssuersListResponseVO> getIssuers(
      @Nullable Integer pageSize,
      @Nullable Integer page,
      @Nullable String scope,
      @Nullable ScopeTypeVO scopeType) {
    pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
    page = Optional.ofNullable(page).orElse(0);

    if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("The requested page size is not supported.");
    }
    if (scope == null && scopeType != null) {
      throw new IllegalArgumentException(SCOPE_TYPE_WITHOUT_SCOPE);
    }
    if (scope != null) {
      requireScope(scope);
    }

    Sort didSort = Sort.unsorted().order(SORT_FIELD);
    Pageable pagination = Pageable.from(page, pageSize, didSort);
    Page<TrustedIssuer> result =
        scope == null
            ? trustedIssuerRepository.findAll(pagination)
            : trustedIssuerRepository.findByGrant(scope, toColumn(scopeType), pagination);

    List<String> dids = result.getContent().stream().map(TrustedIssuer::getDid).toList();

    return HttpResponse.ok(
        new TrustedIssuersListResponseVO()
            .total((int) result.getTotalSize())
            .pageSize(result.getNumberOfElements())
            .page(page)
            .items(dids));
  }

  /**
   * Creates an issuer together with its credentials.
   *
   * <p>An issuer is usually created because something granted it a credential in the first place,
   * so the scope of that grant can be passed along and the credentials are attributed to it.
   * Without a scope the credentials are managed directly through the issuer endpoints, which means
   * no revocation will ever remove them - so a caller acting on behalf of a grant has to pass one.
   *
   * @param trustedIssuerVO the issuer and the credentials to create it with
   * @param scope what grants those credentials, or {@code null} if they are managed directly
   * @param scopeType the kind of thing the scope identifies, or {@code null} for an untyped grant
   * @return created, with the location of the issuer in the registry
   */
  @Transactional
  @Override
  public HttpResponse<Object> createTrustedIssuer(
      TrustedIssuerVO trustedIssuerVO, @Nullable String scope, @Nullable ScopeTypeVO scopeType) {
    if (scope == null && scopeType != null) {
      // no scoped operation could ever address an unscoped credential by its type
      throw new IllegalArgumentException(SCOPE_TYPE_WITHOUT_SCOPE);
    }
    if (scope != null) {
      requireScope(scope);
    }
    if (trustedIssuerRepository.existsById(trustedIssuerVO.getDid())) {
      throw new ConflictException("Issuer already exists.", trustedIssuerVO.getDid());
    }
    String type = toColumn(scopeType);
    TrustedIssuer trustedIssuer = trustedIssuerMapper.map(trustedIssuerVO);
    if (scope != null) {
      Optional.ofNullable(trustedIssuer.getCredentials())
          .orElseGet(List::of)
          .forEach(credential -> credential.setScope(scope).setScopeType(type));
    }
    TrustedIssuer persistedIssuer = trustedIssuerRepository.save(trustedIssuer);
    if (scope == null) {
      log.info("Issuer {} created", persistedIssuer.getDid());
    } else {
      log.info("Issuer {} created for scope {}", persistedIssuer.getDid(), describe(scope, type));
    }
    if (log.isDebugEnabled()) {
      log.debug("Issuer {} created with {}", persistedIssuer.getDid(), toJson(trustedIssuerVO));
    }
    return HttpResponse.created(URI.create(String.format(HREF_TEMPLATE, persistedIssuer.getDid())));
  }

  @Override
  public HttpResponse<Object> deleteIssuerById(String did) {
    Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
    if (!trustedIssuerRepository.existsById(did)) {
      log.debug("Issuer {} not deleted, it does not exist", did);
      return HttpResponse.notFound();
    }
    trustedIssuerRepository.delete(optionalTrustedIssuer.get());
    log.info("Issuer {} deleted", did);
    return HttpResponse.noContent();
  }

  @Override
  public HttpResponse<TrustedIssuerVO> getIssuer(String did) {
    return trustedIssuerRepository
        .getByDid(did)
        .map(trustedIssuerMapper::map)
        .map(HttpResponse::ok)
        .orElseGet(HttpResponse::notFound);
  }

  /**
   * Sets the credentials granted by one scope to exactly the ones provided.
   *
   * <p>Credentials granted by another (scope, scopeType) pair, and credentials managed directly
   * through the issuer endpoints (which carry no scope), are left untouched. Replacing rather than
   * appending makes the operation idempotent: the callers react to notifications that are
   * redelivered on failure, so the same grant may well arrive twice.
   *
   * <p>The issuer is created when it is not known yet, which spares every caller a create-or-update
   * branch - and the race that comes with it.
   *
   * @param did the DID of the issuer
   * @param scope what grants the credentials, e.g. a ProductOrder id
   * @param credentialsVO the credentials this scope grants, possibly empty
   * @param scopeType the kind of thing the scope identifies, {@code null} addresses untyped grants
   * @return the issuer including all of its credentials
   */
  @Transactional
  @Override
  public HttpResponse<TrustedIssuerVO> replaceCredentialsByScope(
      String did,
      String scope,
      List<CredentialsVO> credentialsVO,
      @Nullable ScopeTypeVO scopeType) {

    requireScope(scope);
    String type = toColumn(scopeType);
    String grant = describe(scope, type);
    TrustedIssuer trustedIssuer =
        trustedIssuerRepository
            .getByDid(did)
            .orElseGet(
                () -> {
                  log.info("Issuer {} created for scope {}", did, grant);
                  return trustedIssuerRepository.save(new TrustedIssuer().setDid(did));
                });

    credentialRepository.deleteByTrustedIssuerDidAndScopeAndScopeType(did, scope, type);

    credentialsVO.stream()
        .map(trustedIssuerMapper::map)
        .map(
            credential ->
                credential.setScope(scope).setScopeType(type).setTrustedIssuer(trustedIssuer))
        .forEach(credentialRepository::save);
    if (credentialsVO.isEmpty()) {
      // nothing granted means the scope no longer grants anything
      log.info("Issuer {} revoked credentials of scope {}", did, grant);
    } else {
      log.info("Issuer {} granted credentials for scope {}", did, grant);
    }
    if (log.isDebugEnabled()) {
      log.debug("Issuer {} granted for scope {}: {}", did, grant, toJson(credentialsVO));
    }

    return trustedIssuerRepository
        .getByDid(did)
        .map(trustedIssuerMapper::map)
        .map(HttpResponse::ok)
        .orElseGet(HttpResponse::notFound);
  }

  /**
   * Deletes the credentials granted by one (scope, scopeType) pair and nothing else.
   *
   * <p>The issuer itself is kept even when no credential remains, so that an issuer somebody else
   * manages is not removed as a side effect of a revocation. An issuer without credentials is not
   * trusted for anything, which is the same outcome as before.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials, e.g. a ProductOrder id
   * @param scopeType the kind of thing the scope identifies, {@code null} addresses untyped grants
   * @return no content, or not found if the issuer does not exist
   */
  @Transactional
  @Override
  public HttpResponse<Object> deleteCredentialsByScope(
      String did, String scope, @Nullable ScopeTypeVO scopeType) {
    requireScope(scope);
    String type = toColumn(scopeType);
    String grant = describe(scope, type);
    if (!trustedIssuerRepository.existsById(did)) {
      log.debug("Issuer {} has nothing to revoke for scope {}, it does not exist", did, grant);
      return HttpResponse.notFound();
    }
    credentialRepository.deleteByTrustedIssuerDidAndScopeAndScopeType(did, scope, type);
    log.info("Issuer {} revoked credentials of scope {}", did, grant);
    return HttpResponse.noContent();
  }

  /**
   * Lists the distinct (scope, scopeType) grants an issuer holds credentials under.
   *
   * <p>This lets a caller tell whether something already granted the issuer its credentials, for
   * example to react only to new grants or to report what a sweep would revoke, without reading the
   * credentials themselves. Directly managed credentials carry no scope and are not listed.
   *
   * @param did the DID of the issuer
   * @return the grants with their credential count, ordered by scope with untyped grants first, or
   *     not found if the issuer does not exist
   */
  @Override
  public HttpResponse<List<IssuerScopeVO>> getIssuerScopes(String did) {
    if (!trustedIssuerRepository.existsById(did)) {
      log.debug("Issuer {} has no scopes to list, it does not exist", did);
      return HttpResponse.notFound();
    }
    Map<Grant, Long> credentialsPerGrant =
        credentialRepository.findByTrustedIssuerDidAndScopeIsNotNull(did).stream()
            .collect(
                Collectors.groupingBy(
                    credential -> new Grant(credential.getScope(), credential.getScopeType()),
                    Collectors.counting()));
    return HttpResponse.ok(
        credentialsPerGrant.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(GRANT_ORDER))
            .map(
                entry ->
                    new IssuerScopeVO()
                        .scope(entry.getKey().scope())
                        .scopeType(fromColumn(entry.getKey().scopeType()))
                        .credentialCount(Math.toIntExact(entry.getValue())))
            .toList());
  }

  /**
   * Reject a blank scope.
   *
   * <p>An empty scope would create a grant that no revocation can address precisely, which is the
   * problem the scope exists to solve.
   *
   * @param scope the scope to check
   */
  private static void requireScope(String scope) {
    if (scope == null || scope.isBlank()) {
      throw new IllegalArgumentException(NO_SCOPE_PROVIDED);
    }
  }

  /**
   * Store a scope type by its wire value, so the column reads the same as the API.
   *
   * @param scopeType the type of the grant, or {@code null} for an untyped grant
   * @return the wire value, or {@code null} for an untyped grant
   */
  @Nullable
  private static String toColumn(@Nullable ScopeTypeVO scopeType) {
    return scopeType == null ? null : scopeType.getValue();
  }

  /**
   * Read a scope type back from its stored wire value.
   *
   * @param scopeType the stored wire value, or {@code null} for an untyped grant
   * @return the scope type, or {@code null} for an untyped grant
   */
  @Nullable
  private static ScopeTypeVO fromColumn(@Nullable String scopeType) {
    return scopeType == null ? null : ScopeTypeVO.toEnum(scopeType);
  }

  /**
   * Render a grant for the log, keeping untyped grants as they were logged before types existed.
   *
   * @param scope the scope of the grant
   * @param scopeType the type of the grant, or {@code null}
   * @return the scope, followed by its type if there is one
   */
  private static String describe(String scope, @Nullable String scopeType) {
    return scopeType == null ? scope : scope + " of type " + scopeType;
  }

  /**
   * Render a request body for the debug log, the way it was received.
   *
   * @param body the issuer or credentials to render
   * @return the body as JSON, or its string form if it cannot be serialized
   */
  private String toJson(Object body) {
    try {
      return objectMapper.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      return String.valueOf(body);
    }
  }

  /**
   * Replaces the credentials an issuer is managed with directly.
   *
   * <p>Credentials that were granted by a scope keep their own lifecycle and are <b>not</b>
   * touched: they belong to whatever granted them, and only that grant - or the deletion of the
   * whole issuer - may remove them. Replacing them here would let an unrelated administrative
   * update revoke access that an order paid for. Typed grants always carry a scope, so they are
   * kept as well.
   *
   * @param did the DID of the issuer
   * @param trustedIssuerVO the issuer with the credentials it is managed with directly
   * @return the issuer including its granted credentials, or not found if it does not exist
   */
  @Transactional
  @Override
  public HttpResponse<TrustedIssuerVO> updateIssuer(String did, TrustedIssuerVO trustedIssuerVO) {
    Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
    if (optionalTrustedIssuer.isEmpty()) {
      log.debug("Issuer {} not updated, it does not exist", did);
      return HttpResponse.notFound();
    }
    if (!did.equals(trustedIssuerVO.getDid())) {
      throw new IllegalArgumentException("Did does not match the issuer object.");
    }

    List<Credential> directlyManaged =
        optionalTrustedIssuer.get().getCredentials().stream()
            .filter(credential -> credential.getScope() == null)
            .toList();
    credentialRepository.deleteAll(directlyManaged);
    trustedIssuerRepository.update(trustedIssuerMapper.map(trustedIssuerVO));
    log.info("Issuer {} updated", did);
    if (log.isDebugEnabled()) {
      log.debug("Issuer {} updated with {}", did, toJson(trustedIssuerVO));
    }

    return trustedIssuerRepository
        .getByDid(did)
        .map(trustedIssuerMapper::map)
        .map(HttpResponse::ok)
        .orElseGet(HttpResponse::notFound);
  }
}
