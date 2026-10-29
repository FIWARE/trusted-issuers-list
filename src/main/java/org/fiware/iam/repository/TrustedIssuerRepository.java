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
package org.fiware.iam.repository;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.repository.PageableRepository;
import java.util.Optional;

/** Extension of the base repository to support {@link TrustedIssuer} */
public interface TrustedIssuerRepository extends PageableRepository<TrustedIssuer, String> {

  /**
   * Find issuers by their DID. All child values will be returned through left-joins to fill out the
   * full entity.
   *
   * @param did of the issuer
   * @return the complete issuer
   */
  @Join(value = "credentials", type = Join.Type.LEFT_FETCH)
  @Join(value = "credentials.claims", type = Join.Type.LEFT_FETCH)
  @Join(value = "credentials.claims.claimValues", type = Join.Type.LEFT_FETCH)
  Optional<TrustedIssuer> getByDid(String did);

  /**
   * Find the issuers holding credentials granted by one (scope, scopeType) pair, each listed once.
   * A {@code null} scopeType matches only untyped grants, never "any type".
   *
   * @param scope what granted the credentials
   * @param scopeType the kind of scope, {@code null} for untyped
   * @param pageable the page to return, including its sort
   * @return the page of matching issuers, without their credentials
   */
  default Page<TrustedIssuer> findByGrant(
      String scope, @Nullable String scopeType, Pageable pageable) {
    // A derived query would bind null as "scope_type = NULL", which never matches.
    return scopeType == null
        ? findDistinctByCredentialsScopeAndCredentialsScopeTypeIsNull(scope, pageable)
        : findDistinctByCredentialsScopeAndCredentialsScopeTypeEquals(scope, scopeType, pageable);
  }

  /**
   * Typed half of {@link #findByGrant}, use that instead.
   *
   * @param scope what granted the credentials
   * @param scopeType the kind of scope
   * @param pageable the page to return
   * @return the page of matching issuers
   */
  Page<TrustedIssuer> findDistinctByCredentialsScopeAndCredentialsScopeTypeEquals(
      String scope, String scopeType, Pageable pageable);

  /**
   * Untyped half of {@link #findByGrant}, use that instead.
   *
   * @param scope what granted the credentials
   * @param pageable the page to return
   * @return the page of matching issuers
   */
  Page<TrustedIssuer> findDistinctByCredentialsScopeAndCredentialsScopeTypeIsNull(
      String scope, Pageable pageable);
}
