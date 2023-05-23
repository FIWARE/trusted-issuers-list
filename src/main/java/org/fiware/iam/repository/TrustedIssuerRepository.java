package org.fiware.iam.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

public interface TrustedIssuerRepository extends PageableRepository<TrustedIssuer, String> {

	@Join(value = "capabilities", type = Join.Type.LEFT_FETCH)
	@Join(value = "capabilities.claims", type = Join.Type.LEFT_FETCH)
	@Join(value = "capabilities.claims.claimValues", type = Join.Type.LEFT_FETCH)
	Optional<TrustedIssuer> getByDid(String did);
}

