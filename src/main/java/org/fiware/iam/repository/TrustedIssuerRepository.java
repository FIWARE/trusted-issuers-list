package org.fiware.iam.repository;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.PageableRepository;

import java.util.Optional;

/**
 * Extension of the base repository to support {@link TrustedIssuer}
 */
public interface TrustedIssuerRepository extends PageableRepository<TrustedIssuer, String> {

	/**
	 * Find issuers by their DID. All child values will be returned through left-joins to fill out the full entity.
	 *
	 * @param did of the issuer
	 * @return the complete issuer
	 */
	@Join(value = "capabilities", type = Join.Type.LEFT_FETCH)
	@Join(value = "capabilities.claims", type = Join.Type.LEFT_FETCH)
	@Join(value = "capabilities.claims.claimValues", type = Join.Type.LEFT_FETCH)
	Optional<TrustedIssuer> getByDid(String did);
}

