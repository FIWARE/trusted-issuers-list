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

import io.micronaut.data.repository.PageableRepository;

/** Extension of the base repository to support {@link Credential} */
public interface CredentialRepository extends PageableRepository<Credential, Integer> {

  /**
   * Find the credentials an issuer was granted by one scope.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   * @return the credentials granted by that scope, empty if none were
   */
  java.util.List<Credential> findByTrustedIssuerDidAndScope(String did, String scope);

  /**
   * Delete the credentials an issuer was granted by one scope, leaving every other credential -
   * including the unscoped ones - in place.
   *
   * @param did the DID of the issuer
   * @param scope what granted the credentials
   */
  void deleteByTrustedIssuerDidAndScope(String did, String scope);
}
