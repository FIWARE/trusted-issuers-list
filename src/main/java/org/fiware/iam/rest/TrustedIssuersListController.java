package org.fiware.iam.rest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
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
import org.fiware.iam.til.model.TrustedIssuersListResponseVO;

import java.net.URI;
import java.util.Collection;
import java.util.List;
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

	private static final int DEFAULT_PAGE_SIZE = 10;
	private static final int MIN_PAGE_SIZE = 1;
	private static final int MAX_PAGE_SIZE = 100;
	private static final String SORT_FIELD = "did";

	private final TrustedIssuerRepository trustedIssuerRepository;
	private final CredentialRepository credentialRepository;
	private final TILMapper trustedIssuerMapper;

	/**
	 * Returns a paginated list of DIDs of all trusted issuers, sorted alphabetically.
	 *
	 * @param pageSize maximum number of items per page (1-100, defaults to 10)
	 * @param page     zero-based page number (defaults to 0)
	 * @return paginated response containing the issuer DIDs
	 */
	@Override
	public HttpResponse<TrustedIssuersListResponseVO> getIssuers(@Nullable Integer pageSize, @Nullable Integer page) {
		pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
		page = Optional.ofNullable(page).orElse(0);

		if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("The requested page size is not supported.");
		}

		Sort didSort = Sort.unsorted().order(SORT_FIELD);
		Pageable pagination = Pageable.from(page, pageSize, didSort);
		Page<TrustedIssuer> result = trustedIssuerRepository.findAll(pagination);

		List<String> dids = result.getContent().stream()
				.map(TrustedIssuer::getDid)
				.toList();

		return HttpResponse.ok(new TrustedIssuersListResponseVO()
				.total((int) result.getTotalSize())
				.pageSize(result.getNumberOfElements())
				.page(page)
				.items(dids));
	}

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
