package org.fiware.iam.rest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.RequiredArgsConstructor;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApiTestClient;
import org.fiware.iam.til.api.IssuerApiTestSpec;
import org.fiware.iam.til.model.CapabilitiesVOTestExample;
import org.fiware.iam.til.model.ClaimVOTestExample;
import org.fiware.iam.til.model.TimeRangeVOTestExample;
import org.fiware.iam.til.model.TrustedIssuerVO;
import org.fiware.iam.til.model.TrustedIssuerVOTestExample;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@RequiredArgsConstructor
@MicronautTest
public class TrustedIssuersListControllerTest
		implements IssuerApiTestSpec {

	public final IssuerApiTestClient testClient;
	public final TrustedIssuerRepository repository;

	private TrustedIssuerVO issuerToCreate;
	private UpdatePair issuerUpdate;
	private String didToUpdate;

	@BeforeEach
	public void cleanUp() {
		repository.deleteAll();
	}

	@Override
	public void createTrustedIssuer201() throws Exception {
		HttpResponse<?> creationResponse = testClient.createTrustedIssuer(issuerToCreate);
		assertEquals(HttpStatus.CREATED, creationResponse.getStatus(), "The issuer should have been created.");
		assertTrue(repository.getByDid(issuerToCreate.getDid()).isPresent(),
				"The issuer should have been persisted to the repository.");
		Optional<String> locationHeader = creationResponse.getHeaders().findFirst("location");
		assertTrue(locationHeader.isPresent(), "A location header should be present.");
		assertEquals("/v4/issuers/did:elsi:happypets", locationHeader.get(),
				"The correct location should be returned.");
	}

	@ParameterizedTest
	@MethodSource("validIssuers")
	public void createTrustedIssuer201(TrustedIssuerVO trustedIssuer) throws Exception {
		issuerToCreate = trustedIssuer;
		createTrustedIssuer201();
	}

	private static Stream<Arguments> validIssuers() {
		return Stream.of(
				Arguments.of(
						TrustedIssuerVOTestExample.build()),
				Arguments.of(
						TrustedIssuerVOTestExample.build().capabilities(List.of(CapabilitiesVOTestExample.build()))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.capabilities(List.of(CapabilitiesVOTestExample.build().validFor(
								TimeRangeVOTestExample.build())))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.capabilities(List.of(CapabilitiesVOTestExample.build().validFor(
								TimeRangeVOTestExample.build().to(null))))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.capabilities(List.of(CapabilitiesVOTestExample.build().validFor(
								TimeRangeVOTestExample.build().from(null))))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.capabilities(List.of(CapabilitiesVOTestExample.build().claims(List.of(
								ClaimVOTestExample.build()))))),
				Arguments.of(TrustedIssuerVOTestExample.build()
						.capabilities(List.of(CapabilitiesVOTestExample.build().claims(List.of(
								ClaimVOTestExample.build().allowedValues(List.of("test", 1)))))))
		);
	}

	@Override
	@Test
	public void createTrustedIssuer400() throws Exception {
		try {
			testClient.createTrustedIssuer(TrustedIssuerVOTestExample.build().did(null));
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "The issuer should not have been created.");
			return;
		}
		fail("The creation attempt should fail for an invalid issuer.");
	}

	@Override
	@Test
	public void createTrustedIssuer409() throws Exception {
		TrustedIssuerVO theIssuer = TrustedIssuerVOTestExample.build();
		assertEquals(HttpStatus.CREATED, testClient.createTrustedIssuer(theIssuer).getStatus(),
				"The issuer should initially be created.");
		try {
			testClient.createTrustedIssuer(theIssuer);
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.CONFLICT, e.getStatus(), "The issuer should not have been created.");
			return;
		}
		fail("The creation attempt should fail for an already existing issuer.");
	}

	@Override
	@Test
	public void deleteIssuerById204() throws Exception {
		TrustedIssuerVO theIssuer = TrustedIssuerVOTestExample.build();
		assertEquals(HttpStatus.CREATED, testClient.createTrustedIssuer(theIssuer).getStatus(),
				"The issuer should initially be created.");
		HttpResponse<?> deletionResponse = testClient.deleteIssuerById(theIssuer.getDid());
		assertEquals(HttpStatus.NO_CONTENT, deletionResponse.getStatus(), "The deletion request should succeed.");
		assertTrue(repository.getByDid(theIssuer.getDid()).isEmpty(),
				"The issuer should not exist in the repository anymore.");
	}

	@Override
	@Test
	public void deleteIssuerById404() throws Exception {
		HttpResponse<?> deletionResponse = testClient.deleteIssuerById("did:web:nonexistent.org");
		assertEquals(HttpStatus.NOT_FOUND, deletionResponse.getStatus(), "The deletion request should succeed.");
	}

	@Override
	public void updateIssuer200() throws Exception {
		assertEquals(HttpStatus.CREATED, testClient.createTrustedIssuer(issuerUpdate.initialIssuer).getStatus(),
				"The issuer should initially be created.");
		HttpResponse<?> updateResponse = testClient.updateIssuer(issuerUpdate.issuerUpdate.getDid(),
				issuerUpdate.issuerUpdate);
		assertEquals(HttpStatus.OK, updateResponse.getStatus(), "The issuer should have been updated.");
	}

	@ParameterizedTest
	@MethodSource("validIssuerUpdates")
	public void updateIssuer200(UpdatePair updatePair) throws Exception {
		issuerUpdate = updatePair;
		updateIssuer200();
	}

	private static TrustedIssuerVO fromArgument(Arguments arg) {
		if (arg.get().length != 1) {
			throw new IllegalArgumentException("Only one argument expected.");
		}
		if (arg.get()[0] instanceof TrustedIssuerVO ti) {
			return ti;
		}
		throw new IllegalArgumentException("Provided argument does not contain a TrustedIssuerVO.");
	}

	private static Stream<Arguments> validIssuerUpdates() {
		return validIssuers().flatMap(initialIssuerArg ->
				validIssuers()
						.map(
								updateArg -> new UpdatePair(fromArgument(initialIssuerArg), fromArgument(updateArg)))
						.toList()
						.stream()
		).map(Arguments::of);
	}

	@Override
	@Test
	public void updateIssuer404() throws Exception {
		TrustedIssuerVO nonExistentIssuer = TrustedIssuerVOTestExample.build();
		HttpResponse<?> updateResponse = testClient.updateIssuer(nonExistentIssuer.getDid(), nonExistentIssuer);
		assertEquals(HttpStatus.NOT_FOUND, updateResponse.getStatus(), "The replacement should result in a 404.");
	}

	@Override
	public void updateIssuer400() throws Exception {
		assertEquals(HttpStatus.CREATED, testClient.createTrustedIssuer(issuerUpdate.initialIssuer).getStatus(),
				"The issuer should initially be created.");
		try {
			testClient.updateIssuer(didToUpdate, issuerUpdate.issuerUpdate);
		} catch (HttpClientResponseException e) {
			assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid updates should be rejected.");
			return;
		}
		fail("Invalid updates should be rejected.");
	}

	@ParameterizedTest
	@MethodSource("invalidUpdates")
	public void updateIssuer400(String did, UpdatePair invalidUpdate) throws Exception {
		issuerUpdate = invalidUpdate;
		didToUpdate = did;
		updateIssuer400();
	}

	private static Stream<Arguments> invalidUpdates() {
		return Stream.of(
				Arguments.of(
						"did:elsi:happypets",
						new UpdatePair(TrustedIssuerVOTestExample.build(),
								TrustedIssuerVOTestExample.build().did(null))),
				Arguments.of(
						"did:elsi:happypets",
						new UpdatePair(TrustedIssuerVOTestExample.build(),
								TrustedIssuerVOTestExample.build().did("did:web:somethingelse"))
				)
		);
	}

	record UpdatePair(TrustedIssuerVO initialIssuer, TrustedIssuerVO issuerUpdate) {
	}
}