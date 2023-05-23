package org.fiware.iam.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TrustedIssuerMapper;
import org.fiware.iam.exception.ConflictException;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApi;
import org.fiware.iam.til.model.TrustedIssuerVO;

import javax.transaction.Transactional;
import java.net.URI;
import java.util.Optional;

@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
@Introspected
public class TrustedIssuersListController implements IssuerApi {

	private static final String LOCATION_HEADER_TEMPLATE = "/v4/issuers/%s";

	private final TrustedIssuerRepository trustedIssuerRepository;
	private final TrustedIssuerMapper trustedIssuerMapper;

	@Transactional
	@Override
	public HttpResponse<Object> createTrustedIssuer(TrustedIssuerVO trustedIssuerVO) {
		if (trustedIssuerRepository.getByDid(trustedIssuerVO.getDid()).isPresent()) {
			throw new ConflictException("Issuer already exists.", trustedIssuerVO.getDid());
		}
		TrustedIssuer persistedIssuer = trustedIssuerRepository.save(trustedIssuerMapper.map(trustedIssuerVO));
		return HttpResponse.created(URI.create(
				String.format(LOCATION_HEADER_TEMPLATE, persistedIssuer.getDid())));
	}

	@Override
	public HttpResponse<Object> deleteIssuerById(String did) {
		Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalTrustedIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}
		trustedIssuerRepository.delete(optionalTrustedIssuer.get());
		return HttpResponse.noContent();
	}

	@Override
	public HttpResponse<Object> replaceIssuer(String did, TrustedIssuerVO trustedIssuerVO) {
		Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalTrustedIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}
		if (!did.equals(trustedIssuerVO.getDid())) {
			throw new IllegalArgumentException("Did does not match the issuer object.");
		}
		TrustedIssuer trustedIssuer = trustedIssuerMapper.map(trustedIssuerVO);
		trustedIssuerRepository.update(trustedIssuer);
		return HttpResponse.noContent();
	}
}
