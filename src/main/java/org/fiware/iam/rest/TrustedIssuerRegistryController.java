package org.fiware.iam.rest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TrustedIssuerMapper;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.tir.api.TirApi;
import org.fiware.iam.tir.model.IssuerVO;
import org.fiware.iam.tir.model.IssuersResponseVO;

import java.util.Optional;

@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
public class TrustedIssuerRegistryController implements TirApi {

	private final TrustedIssuerMapper trustedIssuerMapper;
	private final TrustedIssuerRepository trustedIssuerRepository;

	@Override
	public HttpResponse<IssuerVO> getIssuerV4(String did) {
		Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalTrustedIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}
		return HttpResponse.ok(trustedIssuerMapper.map(optionalTrustedIssuer.get()));
	}

	@Override public HttpResponse<IssuersResponseVO> getIssuersV4(Integer pageSize, String pageAfter) {
		return null;
	}

}
