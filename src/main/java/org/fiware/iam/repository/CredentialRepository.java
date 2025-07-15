package org.fiware.iam.repository;

import io.micronaut.data.repository.PageableRepository;

/**
 * Extension of the base repository to support {@link Credential}
 */
public interface CredentialRepository extends PageableRepository<Credential, Integer> {

}
