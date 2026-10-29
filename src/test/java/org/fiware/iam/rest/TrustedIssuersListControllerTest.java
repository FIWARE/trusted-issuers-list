/*
 * Copyright 2023 FIWARE Foundation e.V. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fiware.iam.rest;

import static org.junit.jupiter.api.Assertions.*;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.fiware.iam.TILMapper;
import org.fiware.iam.repository.Credential;
import org.fiware.iam.repository.CredentialRepository;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApiTestClient;
import org.fiware.iam.til.api.IssuerApiTestSpec;
import org.fiware.iam.til.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@RequiredArgsConstructor
@MicronautTest
public class TrustedIssuersListControllerTest implements IssuerApiTestSpec {

  private static final String ISSUER_DID = "did:web:consumer.org";
  private static final String ORDER_SCOPE = "urn:ngsi-ld:product-order:first";
  private static final String OTHER_ORDER_SCOPE = "urn:ngsi-ld:product-order:second";
  private static final String MEMBERSHIP_SCOPE_TYPE = "dataspace-membership";
  private static final String OPERATOR_CREDENTIAL = "OperatorCredential";
  private static final String USER_CREDENTIAL = "UserCredential";
  private static final String READER_CREDENTIAL = "ReaderCredential";
  private static final String SCOPED_CREDENTIALS_PATH =
      "/issuer/%s/credential?scope=%s&scopeType=%s";
  private static final String CREATE_ISSUER_PATH = "/issuer?scope=%s&scopeType=%s";
  private static final String CREATE_ISSUER_TYPE_ONLY_PATH =
      "/issuer?scopeType=" + MEMBERSHIP_SCOPE_TYPE;
  private static final String ISSUER_SCOPES_PATH = "/issuer/%s/scopes";
  private static final String MEMBER_DID = "did:web:a-member.org";
  private static final String OTHER_ORDER_DID = "did:web:other-order.org";
  private static final String UNSCOPED_DID = "did:web:b-unscoped.org";
  private static final String UNKNOWN_SCOPE = "urn:ngsi-ld:product-order:unknown";
  private static final String GRANTED_DID_TEMPLATE = "did:web:granted-%d.org";
  private static final String UNSCOPED_DID_TEMPLATE = "did:web:direct-%d.org";
  private static final int GRANTED_ISSUERS = 5;
  private static final int GRANT_PAGE_SIZE = 2;
  private static final String SCOPE_TYPE_PROPERTY = "scopeType";
  private static final int ORDER_SCOPE_CREDENTIALS = 2;
  private static final int MEMBERSHIP_CREDENTIALS = 1;
  private static final int OTHER_ORDER_CREDENTIALS = 1;
  // a list rather than the enum's values, because the untyped grant (null) is one of them
  private static final List<ScopeTypeVO> SCOPE_TYPES =
      Arrays.asList(null, ScopeTypeVO.DATASPACE_MEMBERSHIP);

  public final IssuerApiTestClient testClient;
  public final TrustedIssuerRepository repository;
  public final CredentialRepository credentialRepository;
  public final TILMapper trustedIssuerMapper;

  @Inject
  @Client("/")
  HttpClient httpClient;

  private TrustedIssuerVO issuerToCreate;
  private UpdatePair issuerUpdate;
  private String didToUpdate;
  private Consumer<IssuerApiTestClient> grantsToSetUp;
  private List<IssuerScopeVO> expectedScopes;

  @BeforeEach
  public void cleanUp() {
    repository.deleteAll();
  }

  @Override
  public void createTrustedIssuer201() throws Exception {
    HttpResponse<?> creationResponse = testClient.createTrustedIssuer(issuerToCreate, null, null);
    assertEquals(
        HttpStatus.CREATED, creationResponse.getStatus(), "The issuer should have been created.");
    assertTrue(
        repository.getByDid(issuerToCreate.getDid()).isPresent(),
        "The issuer should have been persisted to the repository.");
    Optional<String> locationHeader = creationResponse.getHeaders().findFirst("location");
    assertTrue(locationHeader.isPresent(), "A location header should be present.");
    assertEquals(
        "/v4/issuers/did:elsi:happypets",
        locationHeader.get(),
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
        Arguments.of(TrustedIssuerVOTestExample.build()),
        Arguments.of(
            TrustedIssuerVOTestExample.build()
                .credentials(List.of(CredentialsVOTestExample.build()))),
        Arguments.of(
            TrustedIssuerVOTestExample.build()
                .credentials(
                    List.of(
                        CredentialsVOTestExample.build()
                            .validFor(TimeRangeVOTestExample.build())))),
        Arguments.of(
            TrustedIssuerVOTestExample.build()
                .credentials(
                    List.of(
                        CredentialsVOTestExample.build()
                            .validFor(TimeRangeVOTestExample.build().to(null))))),
        Arguments.of(
            TrustedIssuerVOTestExample.build()
                .credentials(
                    List.of(
                        CredentialsVOTestExample.build()
                            .validFor(TimeRangeVOTestExample.build().from(null))))),
        Arguments.of(
            TrustedIssuerVOTestExample.build()
                .credentials(
                    List.of(
                        CredentialsVOTestExample.build()
                            .claims(List.of(ClaimVOTestExample.build()))))),
        Arguments.of(
            TrustedIssuerVOTestExample.build()
                .credentials(
                    List.of(
                        CredentialsVOTestExample.build()
                            .claims(
                                List.of(
                                    ClaimVOTestExample.build()
                                        .allowedValues(List.of("test", 1))))))));
  }

  @Override
  @Test
  public void createTrustedIssuer400() throws Exception {
    try {
      testClient.createTrustedIssuer(TrustedIssuerVOTestExample.build().did(null), null, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "The issuer should not have been created.");
      return;
    }
    fail("The creation attempt should fail for an invalid issuer.");
  }

  @Override
  @Test
  public void createTrustedIssuer409() throws Exception {
    TrustedIssuerVO theIssuer = TrustedIssuerVOTestExample.build();
    assertEquals(
        HttpStatus.CREATED,
        testClient.createTrustedIssuer(theIssuer, null, null).getStatus(),
        "The issuer should initially be created.");
    try {
      testClient.createTrustedIssuer(theIssuer, null, null);
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
    assertEquals(
        HttpStatus.CREATED,
        testClient.createTrustedIssuer(theIssuer, null, null).getStatus(),
        "The issuer should initially be created.");
    HttpResponse<?> deletionResponse = testClient.deleteIssuerById(theIssuer.getDid());
    assertEquals(
        HttpStatus.NO_CONTENT,
        deletionResponse.getStatus(),
        "The deletion request should succeed.");
    assertTrue(
        repository.getByDid(theIssuer.getDid()).isEmpty(),
        "The issuer should not exist in the repository anymore.");
  }

  @Override
  @Test
  public void deleteIssuerById404() throws Exception {
    HttpResponse<?> deletionResponse = testClient.deleteIssuerById("did:web:nonexistent.org");
    assertEquals(
        HttpStatus.NOT_FOUND, deletionResponse.getStatus(), "The deletion request should succeed.");
  }

  @Test
  @Override
  public void getIssuer200() throws Exception {
    TrustedIssuerVO theIssuer = TrustedIssuerVOTestExample.build();
    assertEquals(
        HttpStatus.CREATED,
        testClient.createTrustedIssuer(theIssuer, null, null).getStatus(),
        "The issuer should initially be created.");
    HttpResponse<?> getResponse = testClient.getIssuer(theIssuer.getDid());
    assertEquals(HttpStatus.OK, getResponse.getStatus(), "The retrieval request should succeed.");
    assertEquals(theIssuer, getResponse.body(), "The issuer should be the same");
  }

  @Test
  @Override
  public void getIssuer404() throws Exception {
    HttpResponse<?> getResponse = testClient.getIssuer("notExistingDid");
    assertEquals(HttpStatus.NOT_FOUND, getResponse.getStatus(), "No issuer should have been found");
  }

  @Test
  @Override
  public void getIssuers200() throws Exception {
    List<TrustedIssuerVO> issuers = new ArrayList<>();
    for (int i = 10; i < 30; i++) {
      TrustedIssuerVO issuer =
          TrustedIssuerVOTestExample.build().did(String.format("did:elsi:%s", i));
      testClient.createTrustedIssuer(issuer, null, null);
      issuers.add(issuer);
    }

    // default pagination: page 0, size 10
    HttpResponse<TrustedIssuersListResponseVO> response =
        testClient.getIssuers(null, null, null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "The issuers should have been returned.");
    TrustedIssuersListResponseVO body = response.body();
    assertEquals(20, body.getTotal(), "Total count should include all issuers.");
    assertEquals(10, body.getPageSize(), "Default page size should be 10.");
    assertEquals(0, body.getPage(), "Default page should be 0.");
    assertEquals(10, body.getItems().size(), "First page should contain 10 items.");
    assertEquals("did:elsi:10", body.getItems().get(0), "Items should be sorted by DID.");

    // custom page size
    response = testClient.getIssuers(20, null, null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "The issuers should have been returned.");
    body = response.body();
    assertEquals(20, body.getTotal(), "Total count should include all issuers.");
    assertEquals(20, body.getPageSize(), "Requested page size should be applied.");
    assertEquals(20, body.getItems().size(), "All issuers should be returned in one page.");

    // second page
    response = testClient.getIssuers(10, 1, null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "The issuers should have been returned.");
    body = response.body();
    assertEquals(20, body.getTotal(), "Total count should include all issuers.");
    assertEquals(10, body.getPageSize(), "Page size should be 10.");
    assertEquals(1, body.getPage(), "Page number should be 1.");
    assertEquals(
        "did:elsi:20", body.getItems().get(0), "Second page should start after first page items.");
  }

  @Test
  public void getIssuersEmpty200() throws Exception {
    HttpResponse<TrustedIssuersListResponseVO> response =
        testClient.getIssuers(null, null, null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "An empty list should still return 200.");
    TrustedIssuersListResponseVO body = response.body();
    assertEquals(0, body.getTotal(), "Total should be 0 for an empty list.");
    assertTrue(body.getItems().isEmpty(), "Items should be empty.");
  }

  @Test
  public void getIssuersInvalidPageSize400() throws Exception {
    try {
      testClient.getIssuers(0, null, null, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST,
          e.getStatus(),
          "A page size below the minimum should be rejected.");
      return;
    }
    fail("A page size of 0 should be rejected.");
  }

  @Test
  @Override
  public void getIssuers400() throws Exception {
    try {
      testClient.getIssuers(null, null, null, ScopeTypeVO.DATASPACE_MEMBERSHIP);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST,
          e.getStatus(),
          "A scope type without a scope should be rejected.");
      return;
    }
    fail("A scope type without a scope should be rejected.");
  }

  @ParameterizedTest
  @MethodSource("issuersByGrant")
  public void getIssuers200_filteredByGrant(
      String scope, ScopeTypeVO scopeType, List<String> expectedDids) {
    grantSeveralPairsAndAnUnscopedCredential(testClient);
    grantToOtherIssuers();

    TrustedIssuersListResponseVO body = testClient.getIssuers(null, null, scope, scopeType).body();

    assertEquals(expectedDids, body.getItems(), "Exactly the issuers holding the pair, once each.");
    assertEquals(
        expectedDids.size(), body.getTotal(), "The total must count issuers, not credentials.");
    assertEquals(expectedDids.size(), body.getPageSize(), "The page holds every match.");
  }

  private static Stream<Arguments> issuersByGrant() {
    return Stream.of(
        // ISSUER_DID holds two credentials under each of the first two pairs
        Arguments.of(ORDER_SCOPE, null, List.of(ISSUER_DID)),
        Arguments.of(
            ORDER_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP, List.of(MEMBER_DID, ISSUER_DID)),
        Arguments.of(OTHER_ORDER_SCOPE, null, List.of(ISSUER_DID, OTHER_ORDER_DID)),
        Arguments.of(OTHER_ORDER_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP, List.of()),
        Arguments.of(UNKNOWN_SCOPE, null, List.of()),
        Arguments.of(UNKNOWN_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP, List.of()));
  }

  @ParameterizedTest
  @MethodSource("scopeTypes")
  public void getIssuers200_filteredByGrantIsPaged(ScopeTypeVO scopeType) {
    List<String> dids = new ArrayList<>();
    for (int i = 0; i < GRANTED_ISSUERS; i++) {
      String did = String.format(GRANTED_DID_TEMPLATE, i);
      testClient.replaceCredentialsByScope(
          did,
          ORDER_SCOPE,
          List.of(credential(OPERATOR_CREDENTIAL), credential(READER_CREDENTIAL)),
          scopeType);
      testClient.createTrustedIssuer(
          new TrustedIssuerVO()
              .did(String.format(UNSCOPED_DID_TEMPLATE, i))
              .credentials(List.of(credential(OPERATOR_CREDENTIAL))),
          null,
          null);
      dids.add(did);
    }

    TrustedIssuersListResponseVO body =
        testClient.getIssuers(GRANT_PAGE_SIZE, 1, ORDER_SCOPE, scopeType).body();

    assertEquals(GRANTED_ISSUERS, body.getTotal(), "The total must count the matching issuers.");
    assertEquals(1, body.getPage(), "The requested page should be returned.");
    assertEquals(GRANT_PAGE_SIZE, body.getPageSize(), "The page should be full.");
    assertEquals(
        dids.subList(GRANT_PAGE_SIZE, 2 * GRANT_PAGE_SIZE),
        body.getItems(),
        "The second page should continue in DID order.");
  }

  @ParameterizedTest
  @MethodSource("invalidIssuerFilters")
  public void getIssuers400_invalidFilter(String path) {
    grantEveryPairAndAnUnscopedCredential();

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class,
            () -> httpClient.toBlocking().exchange(HttpRequest.GET(path)));

    assertEquals(
        HttpStatus.BAD_REQUEST,
        e.getStatus(),
        "An invalid filter must be rejected rather than list every issuer.");
  }

  private static Stream<Arguments> invalidIssuerFilters() {
    return Stream.of(
        Arguments.of("/issuer?scope="),
        Arguments.of("/issuer?scope=%20"),
        Arguments.of("/issuer?scope=&scopeType=" + MEMBERSHIP_SCOPE_TYPE),
        Arguments.of("/issuer?scopeType=" + MEMBERSHIP_SCOPE_TYPE),
        Arguments.of("/issuer?scope=" + ORDER_SCOPE + "&scopeType="),
        Arguments.of("/issuer?scope=" + ORDER_SCOPE + "&scopeType=bogus"),
        Arguments.of("/issuer?scope=" + ORDER_SCOPE + "&scopeType=DATASPACE_MEMBERSHIP"));
  }

  /**
   * Grants to issuers besides {@link #ISSUER_DID}: a second membership with two credentials, a
   * second holder of {@link #OTHER_ORDER_SCOPE}, and an issuer with only unscoped credentials.
   */
  private void grantToOtherIssuers() {
    testClient.replaceCredentialsByScope(
        MEMBER_DID,
        ORDER_SCOPE,
        List.of(credential(OPERATOR_CREDENTIAL), credential(READER_CREDENTIAL)),
        ScopeTypeVO.DATASPACE_MEMBERSHIP);
    testClient.replaceCredentialsByScope(
        OTHER_ORDER_DID, OTHER_ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);
    testClient.createTrustedIssuer(
        new TrustedIssuerVO()
            .did(UNSCOPED_DID)
            .credentials(List.of(credential(OPERATOR_CREDENTIAL), credential(USER_CREDENTIAL))),
        null,
        null);
    // ISSUER_DID holds one membership credential from the shared fixture, make it two
    testClient.replaceCredentialsByScope(
        ISSUER_DID,
        ORDER_SCOPE,
        List.of(credential(OPERATOR_CREDENTIAL), credential(READER_CREDENTIAL)),
        ScopeTypeVO.DATASPACE_MEMBERSHIP);
  }

  @Override
  public void updateIssuer200() throws Exception {
    assertEquals(
        HttpStatus.CREATED,
        testClient.createTrustedIssuer(issuerUpdate.initialIssuer, null, null).getStatus(),
        "The issuer should initially be created.");
    HttpResponse<?> updateResponse =
        testClient.updateIssuer(issuerUpdate.issuerUpdate.getDid(), issuerUpdate.issuerUpdate);
    assertEquals(HttpStatus.OK, updateResponse.getStatus(), "The issuer should have been updated.");

    TrustedIssuerVO updatedIssuerVO =
        trustedIssuerMapper.map(repository.getByDid(issuerUpdate.issuerUpdate.getDid()).get());

    // Double map VO entity to avoid type mismatch due to use of List.of in builder
    assertEquals(
        updatedIssuerVO,
        trustedIssuerMapper.map(trustedIssuerMapper.map(issuerUpdate.issuerUpdate)),
        "The updated issuer should match.");
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
    return validIssuers()
        .flatMap(
            initialIssuerArg ->
                validIssuers()
                    .map(
                        updateArg ->
                            new UpdatePair(fromArgument(initialIssuerArg), fromArgument(updateArg)))
                    .toList()
                    .stream())
        .map(Arguments::of);
  }

  @Override
  @Test
  public void updateIssuer404() throws Exception {
    TrustedIssuerVO nonExistentIssuer = TrustedIssuerVOTestExample.build();
    HttpResponse<?> updateResponse =
        testClient.updateIssuer(nonExistentIssuer.getDid(), nonExistentIssuer);
    assertEquals(
        HttpStatus.NOT_FOUND,
        updateResponse.getStatus(),
        "The replacement should result in a 404.");
  }

  @Override
  public void updateIssuer400() throws Exception {
    assertEquals(
        HttpStatus.CREATED,
        testClient.createTrustedIssuer(issuerUpdate.initialIssuer, null, null).getStatus(),
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
            new UpdatePair(
                TrustedIssuerVOTestExample.build(), TrustedIssuerVOTestExample.build().did(null))),
        Arguments.of(
            "did:elsi:happypets",
            new UpdatePair(
                TrustedIssuerVOTestExample.build(),
                TrustedIssuerVOTestExample.build().did("did:web:somethingelse"))));
  }

  // --- credentials scoped to whatever granted them ---------------------------

  @Test
  @Override
  public void replaceCredentialsByScope200() throws Exception {
    HttpResponse<TrustedIssuerVO> response =
        testClient.replaceCredentialsByScope(
            ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    assertEquals(HttpStatus.OK, response.getStatus(), "The credentials should have been granted.");
    assertEquals(
        1,
        response.body().getCredentials().size(),
        "The granted credential should be returned with the issuer.");
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).size(),
        "The credential should be persisted with its scope.");
  }

  @Test
  @Override
  public void replaceCredentialsByScope400() throws Exception {
    try {
      testClient.replaceCredentialsByScope(
          ISSUER_DID, "", List.of(credential(OPERATOR_CREDENTIAL)), null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "A grant without a scope should be rejected.");
      return;
    }
    fail("A grant without a scope should be rejected.");
  }

  @ParameterizedTest
  @MethodSource("unknownScopeTypes")
  public void deleteCredentialsByScope400_unknownScopeType(String scopeType) {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);
    HttpRequest<?> revocation =
        HttpRequest.DELETE(
            String.format(SCOPED_CREDENTIALS_PATH, ISSUER_DID, ORDER_SCOPE, scopeType));

    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> httpClient.toBlocking().exchange(revocation));

    assertEquals(
        HttpStatus.BAD_REQUEST,
        e.getStatus(),
        "An unknown scope type should be rejected rather than read as untyped.");
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).size(),
        "The untyped grant must survive a revocation of an unknown type.");
  }

  private static Stream<Arguments> unknownScopeTypes() {
    return Stream.of(
        Arguments.of(""),
        Arguments.of("%20"),
        Arguments.of("bogus"),
        Arguments.of("DATASPACE_MEMBERSHIP"));
  }

  @Test
  public void replaceCredentialsByScope200_typedGrantIsStoredApart() {
    testClient.replaceCredentialsByScope(
        ISSUER_DID,
        ORDER_SCOPE,
        List.of(credential(OPERATOR_CREDENTIAL)),
        ScopeTypeVO.DATASPACE_MEMBERSHIP);

    assertEquals(
        1,
        credentialRepository
            .findByTrustedIssuerDidAndScopeAndScopeType(
                ISSUER_DID, ORDER_SCOPE, MEMBERSHIP_SCOPE_TYPE)
            .size(),
        "The typed grant should be stored under the wire value of its type.");
    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).isEmpty(),
        "A typed grant must not show up as an untyped one.");
  }

  @Test
  @Override
  public void deleteCredentialsByScope204() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    HttpResponse<?> response = testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, null);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatus(), "The revocation should succeed.");
    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).isEmpty(), "The credential should be gone.");
    assertTrue(
        repository.getByDid(ISSUER_DID).isPresent(),
        "The issuer itself should survive a revocation - somebody else may manage it.");
  }

  @Test
  @Override
  public void deleteCredentialsByScope404() throws Exception {
    HttpResponse<?> response =
        testClient.deleteCredentialsByScope("did:web:nonexistent.org", ORDER_SCOPE, null);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatus(), "There is nothing to revoke.");
  }

  @Test
  @Override
  public void deleteCredentialsByScope400() throws Exception {
    try {
      testClient.deleteCredentialsByScope(ISSUER_DID, "", null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST,
          e.getStatus(),
          "A revocation without a scope should be rejected rather than revoking everything.");
      return;
    }
    fail("A revocation without a scope should be rejected.");
  }

  @Test
  public void replaceCredentialsByScope_isIdempotent() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);
    HttpResponse<TrustedIssuerVO> response =
        testClient.replaceCredentialsByScope(
            ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    assertEquals(
        1,
        response.body().getCredentials().size(),
        "Granting the same thing twice - as a redelivered notification does - should not accumulate.");
  }

  @Test
  public void replaceCredentialsByScope_replacesOnlyItsOwnScope() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID, OTHER_ORDER_SCOPE, List.of(credential(USER_CREDENTIAL)), null);

    HttpResponse<TrustedIssuerVO> response =
        testClient.replaceCredentialsByScope(
            ISSUER_DID, ORDER_SCOPE, List.of(credential(READER_CREDENTIAL)), null);

    List<String> types =
        response.body().getCredentials().stream()
            .map(CredentialsVO::getCredentialsType)
            .sorted()
            .toList();
    assertEquals(
        List.of(READER_CREDENTIAL, USER_CREDENTIAL),
        types,
        "Replacing one scope should leave the other scope's grant untouched.");
  }

  @Test
  public void deleteCredentialsByScope_keepsWhatAnotherScopeGranted() throws Exception {
    // both orders grant the very same credential - the case that used to revoke both at once
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID, OTHER_ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, null);

    TrustedIssuerVO issuer = testClient.getIssuer(ISSUER_DID).body();
    assertEquals(
        1,
        issuer.getCredentials().size(),
        "The other order still requires that credential, so it has to survive.");
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, OTHER_ORDER_SCOPE).size(),
        "The surviving credential should still belong to the other scope.");
  }

  @Test
  public void deleteCredentialsByScope_keepsUnscopedCredentials() throws Exception {
    // a credential managed directly through the issuer endpoints carries no scope
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))),
        null,
        null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, null);

    TrustedIssuerVO issuer = testClient.getIssuer(ISSUER_DID).body();
    assertEquals(
        List.of(USER_CREDENTIAL),
        issuer.getCredentials().stream().map(CredentialsVO::getCredentialsType).toList(),
        "A manually managed credential must not be removed by a scoped revocation.");
  }

  @Test
  public void replaceCredentialsByScope_createsTheIssuerIfUnknown() throws Exception {
    assertTrue(repository.getByDid(ISSUER_DID).isEmpty(), "The issuer should not exist yet.");

    HttpResponse<TrustedIssuerVO> response =
        testClient.replaceCredentialsByScope(
            ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    assertEquals(HttpStatus.OK, response.getStatus());
    assertTrue(
        repository.getByDid(ISSUER_DID).isPresent(),
        "Granting to an unknown issuer should create it, so the callers need no create-or-update branch.");
  }

  @Test
  public void createTrustedIssuer_attributesTheCredentialsToTheScope() throws Exception {
    // an issuer is usually created because something granted it a credential in the first place
    HttpResponse<?> response =
        testClient.createTrustedIssuer(
            new TrustedIssuerVO()
                .did(ISSUER_DID)
                .credentials(List.of(credential(OPERATOR_CREDENTIAL))),
            ORDER_SCOPE,
            null);

    assertEquals(HttpStatus.CREATED, response.getStatus(), "The issuer should have been created.");
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).size(),
        "The credentials the issuer was created with should belong to the granting scope.");
  }

  @Test
  public void createTrustedIssuer_scopedCredentialsAreRevocable() throws Exception {
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(OPERATOR_CREDENTIAL))),
        ORDER_SCOPE,
        null);

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, null);

    TrustedIssuerVO issuer = testClient.getIssuer(ISSUER_DID).body();
    assertTrue(
        issuer.getCredentials() == null || issuer.getCredentials().isEmpty(),
        "A credential created together with the issuer has to be revocable through its scope - "
            + "without one it would grant access no revocation can ever take back.");
  }

  @Test
  public void createTrustedIssuer_withoutScopeStaysDirectlyManaged() throws Exception {
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(OPERATOR_CREDENTIAL))),
        null,
        null);

    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).isEmpty(),
        "Without a scope the credentials belong to no grant.");
    HttpResponse<TrustedIssuerVO> updated =
        testClient.updateIssuer(
            ISSUER_DID,
            new TrustedIssuerVO()
                .did(ISSUER_DID)
                .credentials(List.of(credential(READER_CREDENTIAL))));
    assertEquals(
        List.of(READER_CREDENTIAL),
        updated.body().getCredentials().stream().map(CredentialsVO::getCredentialsType).toList(),
        "Directly managed credentials stay replaceable through the issuer endpoint.");
  }

  @Test
  public void createTrustedIssuer_blankScopeIsRejected() throws Exception {
    try {
      testClient.createTrustedIssuer(
          new TrustedIssuerVO()
              .did(ISSUER_DID)
              .credentials(List.of(credential(OPERATOR_CREDENTIAL))),
          "",
          null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "A grant without a scope should be rejected.");
      return;
    }
    fail("A grant without a scope should be rejected.");
  }

  @Test
  public void updateIssuer_keepsWhatAScopeGranted() throws Exception {
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))),
        null,
        null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    // an administrative update of the directly managed credentials
    HttpResponse<TrustedIssuerVO> response =
        testClient.updateIssuer(
            ISSUER_DID,
            new TrustedIssuerVO()
                .did(ISSUER_DID)
                .credentials(List.of(credential(READER_CREDENTIAL))));

    List<String> types =
        response.body().getCredentials().stream()
            .map(CredentialsVO::getCredentialsType)
            .sorted()
            .toList();
    assertEquals(
        List.of(OPERATOR_CREDENTIAL, READER_CREDENTIAL),
        types,
        "The update should replace what it manages and leave the granted credential alone.");
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).size(),
        "The grant should still be attributed to its scope.");
  }

  @Test
  public void deleteIssuerById_removesGrantedCredentialsAsWell() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    assertEquals(
        HttpStatus.NO_CONTENT,
        testClient.deleteIssuerById(ISSUER_DID).getStatus(),
        "Deleting the issuer should succeed.");
    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).isEmpty(),
        "Deleting the subject of a grant removes the grant with it.");
  }

  // --- grants are identified by (scope, scopeType) ----------------------------

  /** Every scope type of {@link #ORDER_SCOPE}, {@code null} being the untyped grant. */
  private static Stream<Arguments> scopeTypes() {
    return SCOPE_TYPES.stream().map(Arguments::of);
  }

  @Test
  public void replaceCredentialsByScope_pairsOfOneScopeCoexist() {
    grantEveryPairAndAnUnscopedCredential();

    SCOPE_TYPES.forEach(
        scopeType ->
            assertEquals(
                1,
                scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(scopeType)).size(),
                "Every type of one scope should hold its own grant, type: " + scopeType));
    assertEquals(
        SCOPE_TYPES.size() + 1,
        testClient.getIssuer(ISSUER_DID).body().getCredentials().size(),
        "The grants of one scope must not overwrite each other or the unscoped credential.");
  }

  @ParameterizedTest
  @MethodSource("scopeTypes")
  public void replaceCredentialsByScope_replacesOnlyItsOwnPair(ScopeTypeVO scopeType) {
    grantEveryPairAndAnUnscopedCredential();

    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(READER_CREDENTIAL)), scopeType);

    assertEquals(
        List.of(READER_CREDENTIAL),
        credentialTypes(scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(scopeType))),
        "The replaced pair should hold exactly the new credentials.");
    assertOtherPairsAndUnscopedSurvive(scopeType);
  }

  @ParameterizedTest
  @MethodSource("scopeTypes")
  public void deleteCredentialsByScope_deletesOnlyItsOwnPair(ScopeTypeVO scopeType) {
    grantEveryPairAndAnUnscopedCredential();

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, scopeType);

    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(scopeType)).isEmpty(),
        "The revoked pair should be gone.");
    assertOtherPairsAndUnscopedSurvive(scopeType);
  }

  @ParameterizedTest
  @MethodSource("scopeTypes")
  public void createTrustedIssuer_attributesTheCredentialsToTheExactPair(ScopeTypeVO scopeType) {
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(OPERATOR_CREDENTIAL))),
        ORDER_SCOPE,
        scopeType);

    otherScopeTypes(scopeType)
        .forEach(
            otherType -> {
              assertTrue(
                  scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(otherType)).isEmpty(),
                  "The credentials must not be attributed to another type: " + otherType);
              testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, otherType);
            });
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(scopeType)).size(),
        "The credentials should belong to the exact pair and survive revoking any other type.");

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, scopeType);

    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(scopeType)).isEmpty(),
        "The credentials should be revocable through their own pair.");
  }

  @ParameterizedTest
  @MethodSource("invalidScopeTypeRequests")
  public void scopedRequest400_invalidScopeType(HttpRequest<?> request) {
    HttpClientResponseException e =
        assertThrows(
            HttpClientResponseException.class, () -> httpClient.toBlocking().exchange(request));

    assertEquals(HttpStatus.BAD_REQUEST, e.getStatus(), "The scope type should be rejected.");
    assertTrue(
        repository.getByDid(ISSUER_DID).isEmpty(),
        "A rejected request must not create the issuer.");
  }

  private static Stream<Arguments> invalidScopeTypeRequests() {
    String credentialsPath = String.format(SCOPED_CREDENTIALS_PATH, ISSUER_DID, ORDER_SCOPE, "");
    TrustedIssuerVO issuer =
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(OPERATOR_CREDENTIAL)));
    return Stream.of(
        Arguments.of(HttpRequest.PUT(credentialsPath, List.of(credential(OPERATOR_CREDENTIAL)))),
        Arguments.of(HttpRequest.DELETE(credentialsPath)),
        Arguments.of(HttpRequest.POST(String.format(CREATE_ISSUER_PATH, ORDER_SCOPE, ""), issuer)),
        Arguments.of(HttpRequest.POST(CREATE_ISSUER_TYPE_ONLY_PATH, issuer)));
  }

  @Test
  public void updateIssuer_keepsWhatATypedScopeGranted() {
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))),
        null,
        null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID,
        ORDER_SCOPE,
        List.of(credential(OPERATOR_CREDENTIAL)),
        ScopeTypeVO.DATASPACE_MEMBERSHIP);

    HttpResponse<TrustedIssuerVO> response =
        testClient.updateIssuer(
            ISSUER_DID,
            new TrustedIssuerVO()
                .did(ISSUER_DID)
                .credentials(List.of(credential(READER_CREDENTIAL))));

    assertEquals(
        List.of(OPERATOR_CREDENTIAL, READER_CREDENTIAL),
        response.body().getCredentials().stream()
            .map(CredentialsVO::getCredentialsType)
            .sorted()
            .toList(),
        "The update should leave the typed grant alone.");
    assertEquals(
        1,
        scopedCredentials(ISSUER_DID, ORDER_SCOPE, MEMBERSHIP_SCOPE_TYPE).size(),
        "The grant should still be attributed to its scope and type.");
  }

  @Test
  public void replaceCredentialsByScope_withoutTypeIsPersistedUntyped() {
    // the shape of every grant written before scope types existed
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    List<Credential> untyped = scopedCredentials(ISSUER_DID, ORDER_SCOPE);
    assertEquals(1, untyped.size(), "The untyped grant should be found by a null type.");
    assertNull(untyped.get(0).getScopeType(), "A grant without a type should store no type.");
  }

  // --- listing the grants of an issuer -----------------------------------------

  @Override
  public void getIssuerScopes200() throws Exception {
    grantsToSetUp.accept(testClient);

    HttpResponse<List<IssuerScopeVO>> response = testClient.getIssuerScopes(ISSUER_DID);

    assertEquals(HttpStatus.OK, response.getStatus(), "The grants should be listed.");
    assertEquals(
        expectedScopes,
        response.body(),
        "Exactly the distinct grants should be listed, in order, with their credential count.");
  }

  @ParameterizedTest
  @MethodSource("issuerScopes")
  public void getIssuerScopes200(Consumer<IssuerApiTestClient> grants, List<IssuerScopeVO> expected)
      throws Exception {
    grantsToSetUp = grants;
    expectedScopes = expected;
    getIssuerScopes200();
  }

  private static Stream<Arguments> issuerScopes() {
    IssuerScopeVO untypedOrder = issuerScope(ORDER_SCOPE, null, ORDER_SCOPE_CREDENTIALS);
    IssuerScopeVO membership =
        issuerScope(ORDER_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP, MEMBERSHIP_CREDENTIALS);
    IssuerScopeVO otherOrder = issuerScope(OTHER_ORDER_SCOPE, null, OTHER_ORDER_CREDENTIALS);
    return Stream.of(
        Arguments.of(
            (Consumer<IssuerApiTestClient>)
                TrustedIssuersListControllerTest::grantSeveralPairsAndAnUnscopedCredential,
            List.of(untypedOrder, membership, otherOrder)),
        Arguments.of(
            (Consumer<IssuerApiTestClient>)
                client ->
                    client.createTrustedIssuer(
                        new TrustedIssuerVO()
                            .did(ISSUER_DID)
                            .credentials(List.of(credential(USER_CREDENTIAL))),
                        null,
                        null),
            List.of()),
        Arguments.of(
            (Consumer<IssuerApiTestClient>)
                client -> {
                  grantSeveralPairsAndAnUnscopedCredential(client);
                  client.deleteCredentialsByScope(
                      ISSUER_DID, ORDER_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP);
                },
            List.of(untypedOrder, otherOrder)),
        Arguments.of(
            (Consumer<IssuerApiTestClient>)
                client -> {
                  grantSeveralPairsAndAnUnscopedCredential(client);
                  client.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE, null);
                },
            List.of(membership, otherOrder)),
        Arguments.of(
            (Consumer<IssuerApiTestClient>)
                client ->
                    client.createTrustedIssuer(
                        new TrustedIssuerVO()
                            .did(ISSUER_DID)
                            .credentials(
                                List.of(
                                    credential(OPERATOR_CREDENTIAL),
                                    credential(READER_CREDENTIAL))),
                        ORDER_SCOPE,
                        ScopeTypeVO.DATASPACE_MEMBERSHIP),
            List.of(
                issuerScope(
                    ORDER_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP, ORDER_SCOPE_CREDENTIALS))));
  }

  @Test
  @Override
  public void getIssuerScopes404() throws Exception {
    HttpResponse<List<IssuerScopeVO>> response =
        testClient.getIssuerScopes("did:web:nonexistent.org");

    assertEquals(HttpStatus.NOT_FOUND, response.getStatus(), "An unknown issuer has no grants.");
  }

  @Test
  public void getIssuerScopes200_untypedGrantCarriesNoType() {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), null);

    String body = httpClient.toBlocking().retrieve(String.format(ISSUER_SCOPES_PATH, ISSUER_DID));

    assertFalse(
        body.contains(SCOPE_TYPE_PROPERTY),
        "An untyped grant should omit the type rather than send null: " + body);
  }

  /**
   * One unscoped credential, two credentials under the untyped {@link #ORDER_SCOPE}, one under its
   * membership type, and one with two claims under {@link #OTHER_ORDER_SCOPE}, granted out of
   * order. The counts differ per pair and from the claim count.
   */
  private static void grantSeveralPairsAndAnUnscopedCredential(IssuerApiTestClient client) {
    client.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))),
        null,
        null);
    client.replaceCredentialsByScope(
        ISSUER_DID,
        OTHER_ORDER_SCOPE,
        List.of(
            new CredentialsVO()
                .credentialsType(OPERATOR_CREDENTIAL)
                .claims(
                    List.of(
                        new ClaimVO().name("roles").allowedValues(List.of("OPERATOR")),
                        new ClaimVO().name("level").allowedValues(List.of("GOLD"))))),
        null);
    client.replaceCredentialsByScope(
        ISSUER_DID,
        ORDER_SCOPE,
        List.of(credential(OPERATOR_CREDENTIAL)),
        ScopeTypeVO.DATASPACE_MEMBERSHIP);
    client.replaceCredentialsByScope(
        ISSUER_DID,
        ORDER_SCOPE,
        List.of(credential(OPERATOR_CREDENTIAL), credential(READER_CREDENTIAL)),
        null);
  }

  private static IssuerScopeVO issuerScope(
      String scope, ScopeTypeVO scopeType, int credentialCount) {
    return new IssuerScopeVO().scope(scope).scopeType(scopeType).credentialCount(credentialCount);
  }

  private void grantEveryPairAndAnUnscopedCredential() {
    testClient.createTrustedIssuer(
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))),
        null,
        null);
    SCOPE_TYPES.forEach(
        scopeType ->
            testClient.replaceCredentialsByScope(
                ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)), scopeType));
  }

  private void assertOtherPairsAndUnscopedSurvive(ScopeTypeVO changedType) {
    otherScopeTypes(changedType)
        .forEach(
            otherType ->
                assertEquals(
                    List.of(OPERATOR_CREDENTIAL),
                    credentialTypes(scopedCredentials(ISSUER_DID, ORDER_SCOPE, column(otherType))),
                    "Another type of the same scope must stay untouched: " + otherType));
    assertTrue(
        testClient.getIssuer(ISSUER_DID).body().getCredentials().stream()
            .map(CredentialsVO::getCredentialsType)
            .anyMatch(USER_CREDENTIAL::equals),
        "The unscoped credential must stay untouched.");
  }

  private static Stream<ScopeTypeVO> otherScopeTypes(ScopeTypeVO scopeType) {
    return SCOPE_TYPES.stream().filter(other -> !Objects.equals(other, scopeType));
  }

  private static String column(ScopeTypeVO scopeType) {
    return scopeType == null ? null : scopeType.getValue();
  }

  private static List<String> credentialTypes(List<Credential> credentials) {
    return credentials.stream().map(Credential::getCredentialsType).toList();
  }

  private List<Credential> scopedCredentials(String did, String scope) {
    return scopedCredentials(did, scope, null);
  }

  private List<Credential> scopedCredentials(String did, String scope, String scopeType) {
    return credentialRepository.findByTrustedIssuerDidAndScopeAndScopeType(did, scope, scopeType);
  }

  private static CredentialsVO credential(String credentialsType) {
    return new CredentialsVO()
        .credentialsType(credentialsType)
        .claims(List.of(new ClaimVO().name("roles").allowedValues(List.of("OPERATOR"))));
  }

  record UpdatePair(TrustedIssuerVO initialIssuer, TrustedIssuerVO issuerUpdate) {}
}
