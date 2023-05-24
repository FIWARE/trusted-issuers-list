package org.fiware.iam.rest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TILMapper;
import org.fiware.iam.exception.ConflictException;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApi;
import org.fiware.iam.til.model.TrustedIssuerVO;

import javax.transaction.Transactional;
import java.net.URI;
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
	public HttpResponse<TrustedIssuerVO> updateIssuer(String did, TrustedIssuerVO trustedIssuerVO) {
		if (!trustedIssuerRepository.existsById(did)) {
			return HttpResponse.notFound();
		}
		if (!did.equals(trustedIssuerVO.getDid())) {
			throw new IllegalArgumentException("Did does not match the issuer object.");
		}
		return HttpResponse.ok(
				trustedIssuerMapper.map(trustedIssuerRepository.update(trustedIssuerMapper.map(trustedIssuerVO))));
	}
}
