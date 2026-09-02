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

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.transaction.Transactional;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

  private final TrustedIssuerRepository trustedIssuerRepository;
  private final CredentialRepository credentialRepository;
  private final TILMapper trustedIssuerMapper;

  /**
   * Returns a paginated list of DIDs of all trusted issuers, sorted alphabetically.
   *
   * @param pageSize maximum number of items per page (1-100, defaults to 10)
   * @param page zero-based page number (defaults to 0)
   * @return paginated response containing the issuer DIDs
   */
  @Override
  public HttpResponse<TrustedIssuersListResponseVO> getIssuers(
      @Nullable Integer pageSize, @Nullable Integer page) {
    pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
    page = Optional.ofNullable(page).orElse(0);

    if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("The requested page size is not supported.");
    }

    Sort didSort = Sort.unsorted().order(SORT_FIELD);
    Pageable pagination = Pageable.from(page, pageSize, didSort);
    Page<TrustedIssuer> result = trustedIssuerRepository.findAll(pagination);

    List<String> dids = result.getContent().stream().map(TrustedIssuer::getDid).toList();

    return HttpResponse.ok(
        new TrustedIssuersListResponseVO()
            .total((int) result.getTotalSize())
            .pageSize(result.getNumberOfElements())
            .page(page)
            .items(dids));
  }

  @Transactional
  @Override
  public HttpResponse<Object> createTrustedIssuer(TrustedIssuerVO trustedIssuerVO) {
    if (trustedIssuerRepository.existsById(trustedIssuerVO.getDid())) {
      throw new ConflictException("Issuer already exists.", trustedIssuerVO.getDid());
    }
    TrustedIssuer persistedIssuer =
        trustedIssuerRepository.save(trustedIssuerMapper.map(trustedIssuerVO));
    return HttpResponse.created(URI.create(String.format(HREF_TEMPLATE, persistedIssuer.getDid())));
  }

  @Override
  public HttpResponse<Object> deleteIssuerById(String did) {
    Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
    if (!trustedIssuerRepository.existsById(did)) {
      return HttpResponse.notFound();
    }
    trustedIssuerRepository.delete(optionalTrustedIssuer.get());
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
   * <p>Credentials granted by another scope, and credentials managed directly through the issuer
   * endpoints (which carry no scope), are left untouched. Replacing rather than appending makes the
   * operation idempotent: the callers react to notifications that are redelivered on failure, so the
   * same grant may well arrive twice.
   *
   * <p>The issuer is created when it is not known yet, which spares every caller a
   * create-or-update branch - and the race that comes with it.
   *
   * @param did the DID of the issuer
   * @param scope what grants the credentials, e.g. a ProductOrder id
   * @param credentialsVO the credentials this scope grants, possibly empty
   * @return the issuer including all of its credentials
   */
  @Transactional
  @Override
  public HttpResponse<TrustedIssuerVO> replaceCredentialsByScope(
      String did, String scope, List<CredentialsVO> credentialsVO) {

    requireScope(scope);
    TrustedIssuer trustedIssuer =
        trustedIssuerRepository
            .getByDid(did)
            .orElseGet(() -> trustedIssuerRepository.save(new TrustedIssuer().setDid(did)));

    credentialRepository.deleteByTrustedIssuerDidAndScope(did, scope);

    credentialsVO.stream()
        .map(trustedIssuerMapper::map)
        .map(credential -> credential.setScope(scope).setTrustedIssuer(trustedIssuer))
        .forEach(credentialRepository::save);

    return trustedIssuerRepository
        .getByDid(did)
        .map(trustedIssuerMapper::map)
        .map(HttpResponse::ok)
        .orElseGet(HttpResponse::notFound);
  }

  /**
   * Deletes the credentials granted by one scope and nothing else.
   *
   * <p>The issuer itself is kept even when no credential remains, so that an issuer somebody else
   * manages is not removed as a side effect of a revocation. An issuer without credentials is not
   * trusted for anything, which is the same outcome as before.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials, e.g. a ProductOrder id
   * @return no content, or not found if the issuer does not exist
   */
  @Transactional
  @Override
  public HttpResponse<Object> deleteCredentialsByScope(String did, String scope) {
    requireScope(scope);
    if (!trustedIssuerRepository.existsById(did)) {
      return HttpResponse.notFound();
    }
    credentialRepository.deleteByTrustedIssuerDidAndScope(did, scope);
    return HttpResponse.noContent();
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
   * Replaces the credentials an issuer is managed with directly.
   *
   * <p>Credentials that were granted by a scope keep their own lifecycle and are <b>not</b> touched:
   * they belong to whatever granted them, and only that grant - or the deletion of the whole issuer -
   * may remove them. Replacing them here would let an unrelated administrative update revoke access
   * that an order paid for.
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

    return trustedIssuerRepository
        .getByDid(did)
        .map(trustedIssuerMapper::map)
        .map(HttpResponse::ok)
        .orElseGet(HttpResponse::notFound);
  }
}
