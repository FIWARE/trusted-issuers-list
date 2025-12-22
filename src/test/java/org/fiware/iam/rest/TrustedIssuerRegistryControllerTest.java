package org.fiware.iam.rest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.RequiredArgsConstructor;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApiTestClient;
import org.fiware.iam.til.model.ClaimVOTestExample;
import org.fiware.iam.til.model.CredentialsVOTestExample;
import org.fiware.iam.til.model.TimeRangeVOTestExample;
import org.fiware.iam.til.model.TrustedIssuerVO;
import org.fiware.iam.til.model.TrustedIssuerVOTestExample;
import org.fiware.iam.tir.api.TirApiTestClient;
import org.fiware.iam.tir.api.TirApiTestSpec;
import org.fiware.iam.tir.model.IssuerVO;
import org.fiware.iam.tir.model.IssuersResponseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@RequiredArgsConstructor
@MicronautTest
public class TrustedIssuerRegistryControllerTest implements TirApiTestSpec {

	private static final String DID_TEMPLATE = "did:elsi:%s";
	private static final String DID_HAPPYPETS = String.format(DID_TEMPLATE, "happypets");

	private final TirApiTestClient testClient;
	private final IssuerApiTestClient insertionClient;
	private final TrustedIssuerRepository repository;

	private TrustedIssuerVO storedIssuer;
	private String didToRequest;
	private Integer pageSize = null;
	private Integer lastPage = null;

	@BeforeEach
	public void cleanUp() {
		repository.deleteAll();
		pageSize = null;
		lastPage = null;
		didToRequest = null;
		storedIssuer = null;
	}

	@Override
	public void getIssuerV4200() throws Exception {
		HttpResponse<IssuerVO> issuerResponse = testClient.getIssuerV4(DID_HAPPYPETS);
		assertEquals(HttpStatus.OK, issuerResponse.getStatus(), "The issuer should have been returned.");
		assertTrue(issuerResponse.getBody().isPresent(), "The issuerVO should have been returned.");
		assertEquals(storedIssuer.getCredentials().size(), issuerResponse.body().getAttributes().size(),
				"All credentials should be returned as attributes.");
	}

	@ParameterizedTest
	@MethodSource("validIssuers")
	public void getIssuerV4200(TrustedIssuerVO trustedIssuer) throws Exception {
		assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(trustedIssuer).getStatus(),
				"The issuer should have been initially created.");
		storedIssuer = trustedIssuer;
		getIssuerV4200();
	}

	private static Stream<Arguments> validIssuers() {
		return Stream.of(
				Arguments.of(
						TrustedIssuerVOTestExample.build()),
				Arguments.of(
						TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.credentials(List.of(CredentialsVOTestExample.build().validFor(
								TimeRangeVOTestExample.build())))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.credentials(List.of(CredentialsVOTestExample.build().validFor(
								TimeRangeVOTestExample.build().to(null))))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.credentials(List.of(CredentialsVOTestExample.build().validFor(
								TimeRangeVOTestExample.build().from(null))))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.credentials(List.of(CredentialsVOTestExample.build().claims(List.of(
								ClaimVOTestExample.build()))))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.credentials(List.of(CredentialsVOTestExample.build().claims(List.of(
								ClaimVOTestExample.build().allowedValues(List.of("test", 1)))))))
		);
	}

	@ParameterizedTest
	@ValueSource(strings = { "my-did", "did:something-incomplete", "did.wrong.seperator" })
	public void getIssuerV4400(String did) throws Exception {
		didToRequest = did;
		getIssuerV4400();
	}

	@Override
	public void getIssuerV4400() throws Exception {
		try {
			testClient.getIssuerV4(didToRequest);
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(),
					"If no valid did is provided, a 400 should be returned.");
			return;
		}
		fail("If no valid did is provided, a 400 should be returned.");
	}

	@Test
	@Override
	public void getIssuerV4404() throws Exception {
		assertEquals(HttpStatus.NOT_FOUND, testClient.getIssuerV4("did:elsi:does-not-exist").getStatus(),
				"Non existing issuers should result in a 404.");
	}

	@Test
	@Override
	public void getIssuersV4200() throws Exception {
		List<TrustedIssuerVO> issuers = new ArrayList<>();
		// start at 10, since sorting happens on string level(eg -> 1,11,2)
		for (int i = 10; i < 30; i++) {
			TrustedIssuerVO issuer = TrustedIssuerVOTestExample.build().did(String.format("did:elsi:%s", i));
			insertionClient.createTrustedIssuer(issuer);
			issuers.add(issuer);
		}
		HttpResponse<IssuersResponseVO> issuersResponse = testClient.getIssuersV4(null, null);
		assertEquals(HttpStatus.OK, issuersResponse.getStatus(), "The issuers should have been returned");
		assertIssuersResponse(20, 10, 10, 19, issuersResponse.body());

		issuersResponse = testClient.getIssuersV4(20, null);
		assertEquals(HttpStatus.OK, issuersResponse.getStatus(), "The issuers should have been returned");
		assertIssuersResponse(20, 20, 10, 29, issuersResponse.body());

		issuersResponse = testClient.getIssuersV4(10, 1);
		assertEquals(HttpStatus.OK, issuersResponse.getStatus(), "The issuers should have been returned");
		assertIssuersResponse(20, 10, 20, 29, issuersResponse.body());
	}

	private void assertIssuersResponse(int total, int pageSize, int startIndex, int endIndex,
			IssuersResponseVO responseVO) {

		assertEquals(total, responseVO.getTotal(), "The correct total should be returned");
		assertEquals(pageSize, responseVO.getPageSize(), "The correct page size should be returned.");
		assertEquals(endIndex - startIndex + 1, responseVO.getItems().size(),
				"All requested items should be included.");
		assertEquals(String.format("did:elsi:%s", startIndex), responseVO.getItems().getFirst().getDid(),
				"The correct start item should be returned.");
		assertEquals(String.format("did:elsi:%s", endIndex), responseVO.getItems().get(endIndex - startIndex).getDid(),
				"The correct end item should be returned.");
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0, 1001, 101 })
	public void getIssuersV4400(int pageSize) throws Exception {
		this.pageSize = pageSize;
		getIssuersV4400();
	}

    @ParameterizedTest
    @ValueSource(ints = {-1})
	public void getIssuersV4400(Integer lastPage) throws Exception {
		this.lastPage = lastPage;
		getIssuersV4400();
	}

	@Override
	public void getIssuersV4400() throws Exception {
		try {
			testClient.getIssuersV4(pageSize, lastPage);
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid arguments should result in a 400");
			return;
		}
		fail("Invalid arguments should result in a 400");
	}

}