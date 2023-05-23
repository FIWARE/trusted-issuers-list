package org.fiware.iam.rest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
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
import org.graalvm.nativeimage.c.struct.RawPointerTo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiredArgsConstructor
@MicronautTest
public class TrustedIssuersListControllerTest
		implements IssuerApiTestSpec {

	public final IssuerApiTestClient testClient;
	public final TrustedIssuerRepository repository;

	private TrustedIssuerVO issuerToCreate;

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

	@Override public void createTrustedIssuer400() throws Exception {

	}

	@Override public void createTrustedIssuer409() throws Exception {

	}

	@Override public void deleteIssuerById204() throws Exception {

	}

	@Override public void deleteIssuerById404() throws Exception {

	}

	@Override public void replaceIssuer204() throws Exception {

	}

	@Override public void replaceIssuer404() throws Exception {

	}

	@Override public void replaceIssuer400() throws Exception {

	}
}