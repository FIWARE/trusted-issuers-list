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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  private static final String OPERATOR_CREDENTIAL = "OperatorCredential";
  private static final String USER_CREDENTIAL = "UserCredential";
  private static final String READER_CREDENTIAL = "ReaderCredential";

  public final IssuerApiTestClient testClient;
  public final TrustedIssuerRepository repository;
  public final CredentialRepository credentialRepository;
  public final TILMapper trustedIssuerMapper;

  private TrustedIssuerVO issuerToCreate;
  private UpdatePair issuerUpdate;
  private String didToUpdate;

  @BeforeEach
  public void cleanUp() {
    repository.deleteAll();
  }

  @Override
  public void createTrustedIssuer201() throws Exception {
    HttpResponse<?> creationResponse = testClient.createTrustedIssuer(issuerToCreate, null);
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
      testClient.createTrustedIssuer(TrustedIssuerVOTestExample.build().did(null), null);
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
        testClient.createTrustedIssuer(theIssuer, null).getStatus(),
        "The issuer should initially be created.");
    try {
      testClient.createTrustedIssuer(theIssuer, null);
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
        testClient.createTrustedIssuer(theIssuer, null).getStatus(),
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
        testClient.createTrustedIssuer(theIssuer, null).getStatus(),
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
      testClient.createTrustedIssuer(issuer, null);
      issuers.add(issuer);
    }

    // default pagination: page 0, size 10
    HttpResponse<TrustedIssuersListResponseVO> response = testClient.getIssuers(null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "The issuers should have been returned.");
    TrustedIssuersListResponseVO body = response.body();
    assertEquals(20, body.getTotal(), "Total count should include all issuers.");
    assertEquals(10, body.getPageSize(), "Default page size should be 10.");
    assertEquals(0, body.getPage(), "Default page should be 0.");
    assertEquals(10, body.getItems().size(), "First page should contain 10 items.");
    assertEquals("did:elsi:10", body.getItems().get(0), "Items should be sorted by DID.");

    // custom page size
    response = testClient.getIssuers(20, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "The issuers should have been returned.");
    body = response.body();
    assertEquals(20, body.getTotal(), "Total count should include all issuers.");
    assertEquals(20, body.getPageSize(), "Requested page size should be applied.");
    assertEquals(20, body.getItems().size(), "All issuers should be returned in one page.");

    // second page
    response = testClient.getIssuers(10, 1);
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
    HttpResponse<TrustedIssuersListResponseVO> response = testClient.getIssuers(null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "An empty list should still return 200.");
    TrustedIssuersListResponseVO body = response.body();
    assertEquals(0, body.getTotal(), "Total should be 0 for an empty list.");
    assertTrue(body.getItems().isEmpty(), "Items should be empty.");
  }

  @Test
  public void getIssuersInvalidPageSize400() throws Exception {
    try {
      testClient.getIssuers(0, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST,
          e.getStatus(),
          "A page size below the minimum should be rejected.");
      return;
    }
    fail("A page size of 0 should be rejected.");
  }

  @Override
  public void updateIssuer200() throws Exception {
    assertEquals(
        HttpStatus.CREATED,
        testClient.createTrustedIssuer(issuerUpdate.initialIssuer, null).getStatus(),
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
        testClient.createTrustedIssuer(issuerUpdate.initialIssuer, null).getStatus(),
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
            ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

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
      testClient.replaceCredentialsByScope(ISSUER_DID, "", List.of(credential(OPERATOR_CREDENTIAL)));
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "A grant without a scope should be rejected.");
      return;
    }
    fail("A grant without a scope should be rejected.");
  }

  @Test
  @Override
  public void deleteCredentialsByScope204() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

    HttpResponse<?> response = testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE);

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
        testClient.deleteCredentialsByScope("did:web:nonexistent.org", ORDER_SCOPE);

    assertEquals(HttpStatus.NOT_FOUND, response.getStatus(), "There is nothing to revoke.");
  }

  @Test
  @Override
  public void deleteCredentialsByScope400() throws Exception {
    try {
      testClient.deleteCredentialsByScope(ISSUER_DID, "");
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
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));
    HttpResponse<TrustedIssuerVO> response =
        testClient.replaceCredentialsByScope(
            ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

    assertEquals(
        1,
        response.body().getCredentials().size(),
        "Granting the same thing twice - as a redelivered notification does - should not accumulate.");
  }

  @Test
  public void replaceCredentialsByScope_replacesOnlyItsOwnScope() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));
    testClient.replaceCredentialsByScope(
        ISSUER_DID, OTHER_ORDER_SCOPE, List.of(credential(USER_CREDENTIAL)));

    HttpResponse<TrustedIssuerVO> response =
        testClient.replaceCredentialsByScope(
            ISSUER_DID, ORDER_SCOPE, List.of(credential(READER_CREDENTIAL)));

    List<String> types =
        response.body().getCredentials().stream().map(CredentialsVO::getCredentialsType).sorted().toList();
    assertEquals(
        List.of(READER_CREDENTIAL, USER_CREDENTIAL),
        types,
        "Replacing one scope should leave the other scope's grant untouched.");
  }

  @Test
  public void deleteCredentialsByScope_keepsWhatAnotherScopeGranted() throws Exception {
    // both orders grant the very same credential - the case that used to revoke both at once
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));
    testClient.replaceCredentialsByScope(
        ISSUER_DID, OTHER_ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE);

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
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))), null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE);

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
            ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

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
            ORDER_SCOPE);

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
        ORDER_SCOPE);

    testClient.deleteCredentialsByScope(ISSUER_DID, ORDER_SCOPE);

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
        null);

    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).isEmpty(),
        "Without a scope the credentials belong to no grant.");
    HttpResponse<TrustedIssuerVO> updated =
        testClient.updateIssuer(
            ISSUER_DID,
            new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(READER_CREDENTIAL))));
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
          "");
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
        new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(USER_CREDENTIAL))), null);
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

    // an administrative update of the directly managed credentials
    HttpResponse<TrustedIssuerVO> response =
        testClient.updateIssuer(
            ISSUER_DID,
            new TrustedIssuerVO().did(ISSUER_DID).credentials(List.of(credential(READER_CREDENTIAL))));

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
        ISSUER_DID, ORDER_SCOPE, List.of(credential(OPERATOR_CREDENTIAL)));

    assertEquals(
        HttpStatus.NO_CONTENT,
        testClient.deleteIssuerById(ISSUER_DID).getStatus(),
        "Deleting the issuer should succeed.");
    assertTrue(
        scopedCredentials(ISSUER_DID, ORDER_SCOPE).isEmpty(),
        "Deleting the subject of a grant removes the grant with it.");
  }

  private List<Credential> scopedCredentials(String did, String scope) {
    return credentialRepository.findByTrustedIssuerDidAndScope(did, scope);
  }

  private static CredentialsVO credential(String credentialsType) {
    return new CredentialsVO()
        .credentialsType(credentialsType)
        .claims(List.of(new ClaimVO().name("roles").allowedValues(List.of("OPERATOR"))));
  }

  record UpdatePair(TrustedIssuerVO initialIssuer, TrustedIssuerVO issuerUpdate) {}
}
