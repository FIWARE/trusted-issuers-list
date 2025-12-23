package org.fiware.iam.rest;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.uri.UriBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.TIRMapper;
import org.fiware.iam.filter.ForwardedForFilter;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.tir.api.TirApi;
import org.fiware.iam.tir.model.IssuerEntryVO;
import org.fiware.iam.tir.model.IssuerVO;
import org.fiware.iam.tir.model.IssuersResponseVO;
import org.fiware.iam.tir.model.LinksVO;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of the (EBSI-compatible) trusted issuers registry
 * {@see https://api-pilot.ebsi.eu/docs/apis/trusted-issuers-registry/v4#/}
 */
@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
public class TrustedIssuerRegistryController implements TirApi {

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
	public HttpResponse<IssuersResponseVO> getIssuersV4(@Nullable Integer pageSize, @Nullable Integer page) {

		pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
		if (pageSize < 1 || pageSize > 100) {
			throw new IllegalArgumentException("The requested page size is not supported.");
		}

		Sort didSort = Sort.unsorted().order("did");
        Pageable pagination = Pageable.from( page, pageSize, didSort);
		Page<TrustedIssuer> result = trustedIssuerRepository.findAll(pagination);

		if (result.isEmpty()) {
			return HttpResponse.ok(new IssuersResponseVO()
					.items(List.of())
					.total(0)
					.pageSize(0)
					.self(getHrefUri("")));
		}

        List<IssuerEntryVO> issuerEntries = result.getContent().stream()
                .map(entry -> new IssuerEntryVO()
                        .did(entry.getDid())
                        .href(getHrefUri(entry.getDid()))
                ).toList();
		return HttpResponse.ok(new IssuersResponseVO()
				.items(issuerEntries)
				.total((int) result.getTotalSize())
				.pageSize(result.getNumberOfElements())
				.self(getHrefUri(""))
				.links(getLinks(result)));
	}

    private URI getHrefUri(String path) {

        UriBuilder builder = getBaseUriBuilder();
        if (path != null && !path.isEmpty()) {
            builder.path(path);
        }
        return builder.build();
    }

    private UriBuilder getBaseUriBuilder() {
        Optional<HttpRequest<Object>> httpRequest = ServerRequestContext.currentRequest();
        if (httpRequest.isPresent()) {
            HttpRequest<?> request = httpRequest.get();
            URI baseUri = (URI) request.getAttribute(ForwardedForFilter.REQ_ATTR).orElse(URI.create("/"));
            return UriBuilder.of(baseUri).replacePath(request.getPath());
        }
        return UriBuilder.of("/");
    }

    private LinksVO getLinks(Page<?> page) {
        LinksVO links = new LinksVO();
        URI baseUri = getHrefUri("");
        int pageSize = page.getSize();

        if (page.hasPrevious()) {
            links.prev(UriBuilder.of(baseUri)
                    .queryParam("page[after]", page.getPageable().previous().getNumber())
                    .queryParam("page[size]", page.getSize()).build());

        }
        if (page.hasNext()) {
            links.next(UriBuilder.of(baseUri)
                    .queryParam("page[after]", page.getPageable().next().getNumber())
                    .queryParam("page[size]", pageSize).build());
        }

        links.first(UriBuilder.of(baseUri)
                .queryParam("page[after]", 0)
                .queryParam("page[size]", pageSize).build());
        links.last(UriBuilder.of(baseUri)
                .queryParam("page[after]", page.getTotalPages() - 1)
                .queryParam("page[size]", pageSize).build());
        return links;
    }
}
