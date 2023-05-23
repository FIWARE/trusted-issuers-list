package org.fiware.iam.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.CrudRepository;

import java.util.Optional;

public interface TrustedIssuerRepository extends CrudRepository<TrustedIssuer, String> {

	@Join(value = "capabilities", type = Join.Type.LEFT_FETCH)
	@Join(value = "capabilities.claims", type = Join.Type.LEFT_FETCH)
	@Join(value = "capabilities.claims.claimValues", type = Join.Type.LEFT_FETCH)
	Optional<TrustedIssuer> getByDid(String did);

}

