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
import org.fiware.iam.TIRv5Mapper;
import org.fiware.iam.filter.ForwardedForFilter;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.TrustedIssuer;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.tir.v5.api.Tirv5Api;
import org.fiware.iam.tir.v5.model.AttributeDetailsVO;
import org.fiware.iam.tir.v5.model.AttributeEntryVO;
import org.fiware.iam.tir.v5.model.AttributeVO;
import org.fiware.iam.tir.v5.model.AttributesResponseVO;
import org.fiware.iam.tir.v5.model.IssuerEntryVO;
import org.fiware.iam.tir.v5.model.IssuersResponseVO;
import org.fiware.iam.tir.v5.model.LinksVO;
import org.fiware.iam.tir.v5.model.RevisionEntryVO;
import org.fiware.iam.tir.v5.model.RevisionsResponseVO;

import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of the EBSI Trusted Issuers Registry v5 API.
 *
 * <p>Provides read-only access to trusted issuers with attributes and revisions
 * as sub-resources. Supports a {@code version=deprecated} query parameter for
 * backward-compatible inline attribute responses.</p>
 *
 * <p>Shares the same database as the v4 TIR and TIL management APIs.</p>
 *
 * @see <a href="https://api-pilot.ebsi.eu/docs/apis/trusted-issuers-registry/v5">EBSI TIR v5</a>
 */
@Slf4j
@Controller("${general.basepath:/}")
@RequiredArgsConstructor
public class TrustedIssuerRegistryV5Controller implements Tirv5Api {

	/** Default number of items per page when not specified. */
	private static final int DEFAULT_PAGE_SIZE = 10;

	/** Maximum allowed page size for v5 endpoints. */
	private static final int MAX_PAGE_SIZE = 50;

	/** Root path fallback when no forwarded headers are present. */
	private static final String ROOT_PATH = "/";

	/** Query parameter name for cursor-based pagination. */
	private static final String AFTER_PARAM = "page[after]";

	/** Query parameter name for page size. */
	private static final String SIZE_PARAM = "page[size]";

	/** Default sort field for issuer listings. */
	private static final String DEFAULT_SORT = "did";

	/** Version parameter value for the latest (link-based) response format. */
	private static final String VERSION_LATEST = "latest";

	/** Version parameter value for the deprecated (inline) response format. */
	private static final String VERSION_DEPRECATED = "deprecated";

	/** Base path for v5 issuer endpoints. */
	private static final String V5_ISSUERS_PATH = "/v5/issuers";

	/** Path segment for attributes sub-resource. */
	private static final String ATTRIBUTES_SEGMENT = "attributes";

	/** Path segment for revisions sub-resource. */
	private static final String REVISIONS_SEGMENT = "revisions";

	/** Prefix for revision identifiers (combined with the credential's database ID). */
	private static final String REVISION_PREFIX = "rev-";

	/** Minimum number of DID parts (scheme, method, identifier). */
	private static final int MIN_DID_PARTS = 3;

	/** Index of the DID scheme component. */
	private static final int DID_SCHEME_INDEX = 0;

	/** Expected DID scheme value. */
	private static final String DID_SCHEME = "did";

	/** JSON response key for the issuer DID. */
	private static final String JSON_KEY_DID = "did";

	/** JSON response key for attributes (link or array, depending on version). */
	private static final String JSON_KEY_ATTRIBUTES = "attributes";

	/** JSON response key for the hasAttributes flag. */
	private static final String JSON_KEY_HAS_ATTRIBUTES = "hasAttributes";

	/** Single revision count (system does not track revision history). */
	private static final int SINGLE_REVISION_COUNT = 1;

	private final TIRv5Mapper tirV5Mapper;
	private final TrustedIssuerRepository trustedIssuerRepository;

	/**
	 * Returns a paginated list of trusted issuers.
	 *
	 * @param pageSize  maximum items per page (1..50, default 10)
	 * @param pageAfter cursor string interpreted as a page number (default "0")
	 * @return paginated list of issuer entries with DID and href
	 */
	@Override
	public HttpResponse<IssuersResponseVO> getIssuersV5(@Nullable Integer pageSize, @Nullable String pageAfter) {
		pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
		validatePageSize(pageSize);

		int pageNumber = parsePageAfter(pageAfter);
		Sort sort = Sort.unsorted().order(DEFAULT_SORT);
		Pageable pageable = Pageable.from(pageNumber, pageSize, sort);
		Page<TrustedIssuer> result = trustedIssuerRepository.findAll(pageable);

		if (result.isEmpty()) {
			IssuersResponseVO response = new IssuersResponseVO();
			response.setSelf(getSelfUri());
			response.setItems(List.of());
			response.setTotal(0);
			response.setPageSize(0);
			response.setLinks(new LinksVO());
			return HttpResponse.ok(response);
		}

		List<IssuerEntryVO> entries = result.getContent().stream()
				.map(issuer -> {
					IssuerEntryVO entry = new IssuerEntryVO();
					entry.setDid(issuer.getDid());
					entry.setHref(buildHref(issuer.getDid()));
					return entry;
				})
				.toList();

		IssuersResponseVO response = new IssuersResponseVO();
		response.setSelf(getSelfUri());
		response.setItems(entries);
		response.setTotal((int) result.getTotalSize());
		response.setPageSize(result.getNumberOfElements());
		response.setLinks(buildPageLinks(result));
		return HttpResponse.ok(response);
	}

	/**
	 * Returns a trusted issuer identified by its DID.
	 *
	 * <p>When {@code version=latest} (default), returns a link-based response with
	 * an attributes URL and {@code hasAttributes} flag. When {@code version=deprecated},
	 * returns inline attributes for backward compatibility.</p>
	 *
	 * @param did     the issuer's DID
	 * @param version response format version ({@code latest} or {@code deprecated})
	 * @return the issuer details or 404 if not found
	 */
	@Override
	public HttpResponse<Object> getIssuerV5(String did, @Nullable String version) {
		checkDidFormat(did);
		Optional<TrustedIssuer> optionalIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}

		TrustedIssuer issuer = optionalIssuer.get();
		String effectiveVersion = Optional.ofNullable(version).orElse(VERSION_LATEST);

		if (VERSION_DEPRECATED.equals(effectiveVersion)) {
			return HttpResponse.ok(buildDeprecatedIssuerResponse(issuer));
		}
		return HttpResponse.ok(buildLatestIssuerResponse(issuer));
	}

	/**
	 * Returns a paginated list of attributes for a trusted issuer.
	 *
	 * @param did       the issuer's DID
	 * @param pageSize  maximum items per page (1..50, default 10)
	 * @param pageAfter cursor string interpreted as a page number (default "0")
	 * @return paginated list of attribute entries or 404 if issuer not found
	 */
	@Override
	public HttpResponse<AttributesResponseVO> getIssuerAttributesV5(
			String did, @Nullable Integer pageSize, @Nullable String pageAfter) {
		checkDidFormat(did);
		Optional<TrustedIssuer> optionalIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}

		TrustedIssuer issuer = optionalIssuer.get();
		pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
		validatePageSize(pageSize);
		int pageNumber = parsePageAfter(pageAfter);

		List<Credential> allCredentials = issuer.getCredentials() != null
				? issuer.getCredentials().stream()
						.sorted(Comparator.comparing(Credential::getId))
						.toList()
				: List.of();
		int total = allCredentials.size();
		int fromIndex = Math.min(pageNumber * pageSize, total);
		int toIndex = Math.min(fromIndex + pageSize, total);
		List<Credential> pageCredentials = allCredentials.subList(fromIndex, toIndex);

		List<AttributeEntryVO> entries = pageCredentials.stream()
				.map(cred -> {
					String attrId = tirV5Mapper.computeAttributeId(cred);
					AttributeEntryVO entry = new AttributeEntryVO();
					entry.setId(attrId);
					entry.setHref(buildHref(attrId));
					return entry;
				})
				.toList();

		AttributesResponseVO response = new AttributesResponseVO();
		response.setSelf(getSelfUri());
		response.setItems(entries);
		response.setTotal(total);
		response.setPageSize(entries.size());
		response.setLinks(buildInMemoryPageLinks(pageNumber, pageSize, total));
		return HttpResponse.ok(response);
	}

	/**
	 * Returns a specific attribute for a trusted issuer, identified by its SHA-256 hex hash.
	 *
	 * @param did         the issuer's DID
	 * @param attributeId the SHA-256 hex-encoded attribute identifier
	 * @return the attribute details or 404 if not found
	 */
	@Override
	public HttpResponse<AttributeDetailsVO> getIssuerAttributeV5(String did, String attributeId) {
		checkDidFormat(did);
		Optional<TrustedIssuer> optionalIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}

		TrustedIssuer issuer = optionalIssuer.get();
		Optional<Credential> matchingCredential = findCredentialByAttributeId(issuer, attributeId);
		if (matchingCredential.isEmpty()) {
			return HttpResponse.notFound();
		}

		return HttpResponse.ok(tirV5Mapper.credentialToAttributeDetailsVO(did, matchingCredential.get()));
	}

	/**
	 * Returns a list of revisions for an attribute.
	 *
	 * <p>Since the system does not track revision history, this endpoint always
	 * returns a single-item list containing the current (latest) revision.</p>
	 *
	 * @param did         the issuer's DID
	 * @param attributeId the SHA-256 hex-encoded attribute identifier
	 * @param pageSize    maximum items per page (1..50, default 10)
	 * @param pageAfter   cursor string interpreted as a page number (default "0")
	 * @return paginated revisions list or 404 if issuer/attribute not found
	 */
	@Override
	public HttpResponse<RevisionsResponseVO> getIssuerAttributeRevisionsV5(
			String did, String attributeId,
			@Nullable Integer pageSize, @Nullable String pageAfter) {
		checkDidFormat(did);
		Optional<TrustedIssuer> optionalIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}

		Optional<Credential> matchingCredential = findCredentialByAttributeId(optionalIssuer.get(), attributeId);
		if (matchingCredential.isEmpty()) {
			return HttpResponse.notFound();
		}

		pageSize = Optional.ofNullable(pageSize).orElse(DEFAULT_PAGE_SIZE);
		validatePageSize(pageSize);

		String revisionId = REVISION_PREFIX + matchingCredential.get().getId();

		RevisionEntryVO entry = new RevisionEntryVO();
		entry.setId(revisionId);
		entry.setHref(buildHref(revisionId));

		RevisionsResponseVO response = new RevisionsResponseVO();
		response.setSelf(getSelfUri());
		response.setItems(List.of(entry));
		response.setTotal(SINGLE_REVISION_COUNT);
		response.setPageSize(SINGLE_REVISION_COUNT);
		response.setLinks(new LinksVO());
		return HttpResponse.ok(response);
	}

	/**
	 * Returns a specific revision of an attribute.
	 *
	 * <p>Since the system does not track revision history, only the current
	 * revision (matching the credential's database ID) is available.</p>
	 *
	 * @param did         the issuer's DID
	 * @param attributeId the SHA-256 hex-encoded attribute identifier
	 * @param revisionId  the revision identifier (format: {@code rev-{credentialId}})
	 * @return the attribute details or 404 if not found
	 */
	@Override
	public HttpResponse<AttributeDetailsVO> getIssuerAttributeRevisionV5(
			String did, String attributeId, String revisionId) {
		checkDidFormat(did);
		Optional<TrustedIssuer> optionalIssuer = trustedIssuerRepository.getByDid(did);
		if (optionalIssuer.isEmpty()) {
			return HttpResponse.notFound();
		}

		Optional<Credential> matchingCredential = findCredentialByAttributeId(optionalIssuer.get(), attributeId);
		if (matchingCredential.isEmpty()) {
			return HttpResponse.notFound();
		}

		String expectedRevisionId = REVISION_PREFIX + matchingCredential.get().getId();
		if (!expectedRevisionId.equals(revisionId)) {
			return HttpResponse.notFound();
		}

		return HttpResponse.ok(tirV5Mapper.credentialToAttributeDetailsVO(did, matchingCredential.get()));
	}

	// --- Validation helpers ---

	/**
	 * Validates the basic structure of a DID string (must have at least 3 colon-separated
	 * parts and start with "did").
	 *
	 * @param did the DID string to validate
	 * @throws IllegalArgumentException if the DID format is invalid
	 */
	private void checkDidFormat(String did) {
		String[] didParts = did.split(":");
		if (didParts.length < MIN_DID_PARTS || !didParts[DID_SCHEME_INDEX].equals(DID_SCHEME)) {
			throw new IllegalArgumentException("Provided string is not a valid did.");
		}
	}

	/**
	 * Validates that the page size is within the allowed range (1 to {@value MAX_PAGE_SIZE}).
	 *
	 * @param pageSize the requested page size
	 * @throws IllegalArgumentException if the page size is out of range
	 */
	private void validatePageSize(int pageSize) {
		if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException(
					"The requested page size is not supported. Maximum is " + MAX_PAGE_SIZE + ".");
		}
	}

	/**
	 * Parses the {@code page[after]} cursor string as a page number.
	 *
	 * @param pageAfter the cursor string (nullable)
	 * @return the page number (0-based), defaults to 0 for null, empty, or invalid values
	 */
	private int parsePageAfter(String pageAfter) {
		if (pageAfter == null || pageAfter.isEmpty()) {
			return 0;
		}
		try {
			int value = Integer.parseInt(pageAfter);
			return Math.max(0, value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// --- Credential lookup ---

	/**
	 * Finds a credential within an issuer's credentials by matching the SHA-256 hex-encoded
	 * attribute ID.
	 *
	 * @param issuer      the trusted issuer entity
	 * @param attributeId the hex-encoded SHA-256 hash to match
	 * @return the matching credential, or empty if not found
	 */
	private Optional<Credential> findCredentialByAttributeId(TrustedIssuer issuer, String attributeId) {
		if (issuer.getCredentials() == null) {
			return Optional.empty();
		}
		return issuer.getCredentials().stream()
				.filter(cred -> attributeId.equals(tirV5Mapper.computeAttributeId(cred)))
				.findFirst();
	}

	// --- URI building helpers ---

	/**
	 * Returns the base URI builder initialized with the forwarded URI and the current
	 * request's path. Mirrors the approach used by the v4 TIR controller.
	 *
	 * @return a UriBuilder for constructing response URIs
	 */
	private UriBuilder getBaseUriBuilder() {
		Optional<HttpRequest<Object>> httpRequest = ServerRequestContext.currentRequest();
		if (httpRequest.isPresent()) {
			HttpRequest<?> request = httpRequest.get();
			URI baseUri = (URI) request.getAttribute(ForwardedForFilter.REQ_ATTR)
					.orElse(URI.create(ROOT_PATH));
			return UriBuilder.of(baseUri).replacePath(request.getPath());
		}
		return UriBuilder.of(ROOT_PATH);
	}

	/**
	 * Returns the URI for the current request (used as the {@code self} link in responses).
	 *
	 * @return the current request's URI
	 */
	private URI getSelfUri() {
		return getBaseUriBuilder().build();
	}

	/**
	 * Builds a URI by appending additional path segments to the current request's base URI.
	 *
	 * @param pathSegments additional path segments to append
	 * @return the constructed URI
	 */
	private URI buildHref(String... pathSegments) {
		UriBuilder builder = getBaseUriBuilder();
		for (String segment : pathSegments) {
			if (segment != null && !segment.isEmpty()) {
				builder.path(segment);
			}
		}
		return builder.build();
	}

	// --- Issuer response builders ---

	/**
	 * Builds the "latest" version response for an issuer with link-based attributes.
	 *
	 * @param issuer the trusted issuer entity
	 * @return a map representing the JSON response with {@code did}, {@code attributes} (URI),
	 *         and {@code hasAttributes}
	 */
	private Map<String, Object> buildLatestIssuerResponse(TrustedIssuer issuer) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put(JSON_KEY_DID, issuer.getDid());
		URI attributesUri = buildHref(ATTRIBUTES_SEGMENT);
		response.put(JSON_KEY_ATTRIBUTES, attributesUri.toString());
		boolean hasAttributes = issuer.getCredentials() != null && !issuer.getCredentials().isEmpty();
		response.put(JSON_KEY_HAS_ATTRIBUTES, hasAttributes);
		return response;
	}

	/**
	 * Builds the "deprecated" version response for an issuer with inline attributes.
	 *
	 * @param issuer the trusted issuer entity
	 * @return a map representing the JSON response with {@code did} and
	 *         {@code attributes} (array of {@link AttributeVO})
	 */
	private Map<String, Object> buildDeprecatedIssuerResponse(TrustedIssuer issuer) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put(JSON_KEY_DID, issuer.getDid());
		List<AttributeVO> attributes = issuer.getCredentials() != null
				? issuer.getCredentials().stream()
						.map(tirV5Mapper::credentialToAttributeVO)
						.toList()
				: List.of();
		response.put(JSON_KEY_ATTRIBUTES, attributes);
		return response;
	}

	// --- Pagination helpers ---

	/**
	 * Builds pagination links from a Micronaut {@link Page} result.
	 *
	 * @param page the paginated query result
	 * @return the navigation links (first, prev, next, last)
	 */
	private LinksVO buildPageLinks(Page<?> page) {
		LinksVO links = new LinksVO();
		URI baseUri = getSelfUri();
		int size = page.getSize();

		if (page.hasPrevious()) {
			links.setPrev(UriBuilder.of(baseUri)
					.queryParam(AFTER_PARAM, page.getPageable().previous().getNumber())
					.queryParam(SIZE_PARAM, size).build());
		}
		if (page.hasNext()) {
			links.setNext(UriBuilder.of(baseUri)
					.queryParam(AFTER_PARAM, page.getPageable().next().getNumber())
					.queryParam(SIZE_PARAM, size).build());
		}

		links.setFirst(UriBuilder.of(baseUri)
				.queryParam(AFTER_PARAM, 0)
				.queryParam(SIZE_PARAM, size).build());
		links.setLast(UriBuilder.of(baseUri)
				.queryParam(AFTER_PARAM, page.getTotalPages() - 1)
				.queryParam(SIZE_PARAM, size).build());
		return links;
	}

	/**
	 * Builds pagination links for in-memory paginated collections (used for attributes).
	 *
	 * @param currentPage the current page number (0-based)
	 * @param pageSize    the page size
	 * @param totalItems  the total number of items
	 * @return the navigation links (first, prev, next, last)
	 */
	private LinksVO buildInMemoryPageLinks(int currentPage, int pageSize, int totalItems) {
		LinksVO links = new LinksVO();
		URI baseUri = getSelfUri();
		int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));

		if (currentPage > 0) {
			links.setPrev(UriBuilder.of(baseUri)
					.queryParam(AFTER_PARAM, currentPage - 1)
					.queryParam(SIZE_PARAM, pageSize).build());
		}
		if (currentPage < totalPages - 1) {
			links.setNext(UriBuilder.of(baseUri)
					.queryParam(AFTER_PARAM, currentPage + 1)
					.queryParam(SIZE_PARAM, pageSize).build());
		}

		links.setFirst(UriBuilder.of(baseUri)
				.queryParam(AFTER_PARAM, 0)
				.queryParam(SIZE_PARAM, pageSize).build());
		links.setLast(UriBuilder.of(baseUri)
				.queryParam(AFTER_PARAM, totalPages - 1)
				.queryParam(SIZE_PARAM, pageSize).build());
		return links;
	}
}
