package org.fiware.iam.rest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TILMapper;
import org.fiware.iam.exception.ConflictException;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.CredentialRepository;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApi;
import org.fiware.iam.til.model.TrustedIssuerVO;

import java.net.URI;
import java.util.Collection;
import java.util.Optional;

/**
 * Implementation  of the (proprietary) trusted-list api to manage the issuers.
 */
@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
@Introspected
public class TrustedIssuersListController implements IssuerApi {

	public static final String HREF_TEMPLATE = "/v4/issuers/%s";

	private final TrustedIssuerRepository trustedIssuerRepository;
	private final CredentialRepository credentialRepository;
	private final TILMapper trustedIssuerMapper;

	@Transactional
	@Override
	public HttpResponse<Object> createTrustedIssuer(TrustedIssuerVO trustedIssuerVO) {
		if (trustedIssuerRepository.existsById(trustedIssuerVO.getDid())) {
			throw new ConflictException("Issuer already exists.", trustedIssuerVO.getDid());
		}
		TrustedIssuer persistedIssuer = trustedIssuerRepository.save(trustedIssuerMapper.map(trustedIssuerVO));
		return HttpResponse.created(URI.create(
				String.format(HREF_TEMPLATE, persistedIssuer.getDid())));
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

	@Override
	public HttpResponse<TrustedIssuerVO> updateIssuer(String did, TrustedIssuerVO trustedIssuerVO) {
		Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalTrustedIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}
		if (!did.equals(trustedIssuerVO.getDid())) {
			throw new IllegalArgumentException("Did does not match the issuer object.");
		}

		Collection<Credential> credentials = optionalTrustedIssuer.get().getCredentials();
		credentialRepository.deleteAll(credentials);

		return HttpResponse.ok(
				trustedIssuerMapper.map(trustedIssuerRepository.update(trustedIssuerMapper.map(trustedIssuerVO))));
	}
}
