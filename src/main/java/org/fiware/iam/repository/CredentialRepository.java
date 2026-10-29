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
import io.micronaut.data.repository.PageableRepository;
import java.util.List;

/** Extension of the base repository to support {@link Credential} */
public interface CredentialRepository extends PageableRepository<Credential, Integer> {

  /**
   * Find the credentials an issuer was granted by one (scope, scopeType) pair. A {@code null}
   * scopeType matches only untyped grants, never "any type".
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   * @param scopeType the kind of scope, {@code null} for untyped
   * @return the credentials granted by that pair, empty if none were
   */
  default List<Credential> findByTrustedIssuerDidAndScopeAndScopeType(
      String did, String scope, @Nullable String scopeType) {
    // A derived query would bind null as "scope_type = NULL", which never matches.
    return scopeType == null
        ? findByTrustedIssuerDidAndScopeAndScopeTypeIsNull(did, scope)
        : findByTrustedIssuerDidAndScopeAndScopeTypeEquals(did, scope, scopeType);
  }

  /**
   * Delete the credentials an issuer was granted by one (scope, scopeType) pair, leaving every
   * other credential - including the unscoped ones - in place. A {@code null} scopeType matches
   * only untyped grants.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   * @param scopeType the kind of scope, {@code null} for untyped
   */
  default void deleteByTrustedIssuerDidAndScopeAndScopeType(
      String did, String scope, @Nullable String scopeType) {
    if (scopeType == null) {
      deleteByTrustedIssuerDidAndScopeAndScopeTypeIsNull(did, scope);
    } else {
      deleteByTrustedIssuerDidAndScopeAndScopeTypeEquals(did, scope, scopeType);
    }
  }

  /**
   * Find every credential an issuer was granted by any scope, so that its grants can be listed.
   * Credentials managed directly through the issuer endpoints carry no scope and are left out.
   *
   * @param did the DID of the issuer
   * @return the scoped credentials, empty if the issuer holds none
   */
  List<Credential> findByTrustedIssuerDidAndScopeIsNotNull(String did);

  /**
   * Typed half of {@link #findByTrustedIssuerDidAndScopeAndScopeType}, use that instead.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   * @param scopeType the kind of scope
   * @return the matching credentials
   */
  List<Credential> findByTrustedIssuerDidAndScopeAndScopeTypeEquals(
      String did, String scope, String scopeType);

  /**
   * Untyped half of {@link #findByTrustedIssuerDidAndScopeAndScopeType}, use that instead.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   * @return the matching credentials
   */
  List<Credential> findByTrustedIssuerDidAndScopeAndScopeTypeIsNull(String did, String scope);

  /**
   * Typed half of {@link #deleteByTrustedIssuerDidAndScopeAndScopeType}, use that instead.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   * @param scopeType the kind of scope
   */
  void deleteByTrustedIssuerDidAndScopeAndScopeTypeEquals(
      String did, String scope, String scopeType);

  /**
   * Untyped half of {@link #deleteByTrustedIssuerDidAndScopeAndScopeType}, use that instead.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   */
  void deleteByTrustedIssuerDidAndScopeAndScopeTypeIsNull(String did, String scope);
}
