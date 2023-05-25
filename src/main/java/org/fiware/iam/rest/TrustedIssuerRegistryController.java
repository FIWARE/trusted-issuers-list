package org.fiware.iam.rest;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TIRMapper;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.tir.api.TirApi;
import org.fiware.iam.tir.model.IssuerEntryVO;
import org.fiware.iam.tir.model.IssuerVO;
import org.fiware.iam.tir.model.IssuersResponseVO;
import org.fiware.iam.tir.model.LinksVO;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.fiware.iam.rest.TrustedIssuersListController.HREF_TEMPLATE;

/**
 * Implementation of the (EBSI-compatible) trusted issuers registry
 * {@see https://api-pilot.ebsi.eu/docs/apis/trusted-issuers-registry/v4#/}
 */
@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
public class TrustedIssuerRegistryController implements TirApi {

	private static final URI SELF_REF = URI.create("/v4/issuers/");
	private static final int DEFAULT_PAGE_SIZE = 10;

	private final TIRMapper trustedIssuerMapper;
	private final TrustedIssuerRepository trustedIssuerRepository;

	@Override
	public HttpResponse<IssuerVO> getIssuerV4(String did) {
		checkDidFormat(did);
		Optional<TrustedIssuer> optionalTrustedIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalTrustedIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}
		return HttpResponse.ok(trustedIssuerMapper.map(optionalTrustedIssuer.get()));
	}

	// checks the basic structure of a did, will not validate them!
	private void checkDidFormat(String did) {
		String[] didParts = did.split(":");
		if (didParts.length < 3 || !didParts[0].equals("did")) {
			throw new IllegalArgumentException("Provided string is not a valid did.");
		}
	}

	/**
	 * Implements anchor-based pagination on top of the offset-mechanism from the repository.
	 */
	@Override
	public HttpResponse<IssuersResponseVO> getIssuersV4(@Nullable Integer pageSize, @Nullable String lastIssuer) {
		Optional<String> optionalLastIssuer = Optional.ofNullable(lastIssuer);
		optionalLastIssuer.ifPresent(this::checkDidFormat);
		optionalLastIssuer.ifPresent(li -> {
			if (!trustedIssuerRepository.existsById(li)) {
				throw new IllegalArgumentException("The given page does not exist.");
			}
		});

		pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
		if (pageSize < 1 || pageSize > 100) {
			throw new IllegalArgumentException("The requested page size is not supported.");
		}

		List<IssuerEntryVO> issuerEntries = new ArrayList<>();
		URI nextPage = null;
		Sort didSort = Sort.unsorted().order("did");
		Iterable<TrustedIssuer> issuerIterator = trustedIssuerRepository.findAll(didSort);
		int issuerCount = -1;
		if (optionalLastIssuer.isEmpty()) {
			// we start with the first one.
			issuerCount = 0;
		}
		for (TrustedIssuer ti : issuerIterator) {
			if (issuerCount == -1 && ti.getDid().equals(lastIssuer)) {
				issuerCount = 0;
				continue;
			}
			if (issuerCount >= 0 && issuerCount < pageSize) {
				issuerEntries.add(
						new IssuerEntryVO()
								.did(ti.getDid())
								.href(URI.create(String.format(HREF_TEMPLATE, ti.getDid()))));
				issuerCount++;
				continue;
			}
			if (issuerCount == pageSize) {
				nextPage = URI.create(ti.getDid());
				break;
			}
		}

		if (issuerEntries.isEmpty()) {
			return HttpResponse.ok(new IssuersResponseVO()
					.items(List.of())
					.total(0)
					.pageSize(0)
					.self(SELF_REF));
		}

		return HttpResponse.ok(new IssuersResponseVO()
				.items(issuerEntries)
				.total((int) trustedIssuerRepository.count())
				.pageSize(issuerEntries.size())
				.self(SELF_REF)
				.links(new LinksVO()
						.prev(Optional.ofNullable(lastIssuer).map(URI::create).orElse(null))
						.first(URI.create(issuerEntries.get(0).getDid()))
						.next(nextPage)));
	}

}
