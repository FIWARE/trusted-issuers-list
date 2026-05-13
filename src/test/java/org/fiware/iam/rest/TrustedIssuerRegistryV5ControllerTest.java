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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.fiware.iam.TIRv5Mapper;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApiTestClient;
import org.fiware.iam.til.model.ClaimVOTestExample;
import org.fiware.iam.til.model.CredentialsVO;
import org.fiware.iam.til.model.CredentialsVOTestExample;
import org.fiware.iam.til.model.TrustedIssuerVO;
import org.fiware.iam.til.model.TrustedIssuerVOTestExample;
import org.fiware.iam.tir.v5.api.Tirv5ApiTestClient;
import org.fiware.iam.tir.v5.api.Tirv5ApiTestSpec;
import org.fiware.iam.tir.v5.model.AttributeDetailsVO;
import org.fiware.iam.tir.v5.model.AttributesResponseVO;
import org.fiware.iam.tir.v5.model.IssuersResponseVO;
import org.fiware.iam.tir.v5.model.RevisionsResponseVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the TIR v5 controller endpoints.
 *
 * <p>Implements the generated {@link Tirv5ApiTestSpec} interface and uses the generated {@link
 * Tirv5ApiTestClient} for HTTP calls. Test data is inserted via the TIL management API ({@link
 * IssuerApiTestClient}).
 */
@RequiredArgsConstructor
@MicronautTest
public class TrustedIssuerRegistryV5ControllerTest implements Tirv5ApiTestSpec {

  /** Standard DID template used for generating test DIDs. */
  private static final String DID_TEMPLATE = "did:elsi:%s";

  /** Standard DID used in most single-issuer test cases. */
  private static final String DID_HAPPYPETS = String.format(DID_TEMPLATE, "happypets");

  /** A DID that is never inserted, used to test 404 responses. */
  private static final String DID_NON_EXISTENT = "did:elsi:does-not-exist";

  /** An attribute ID that will never match any credential. */
  private static final String NON_EXISTENT_ATTRIBUTE_ID =
      "0000000000000000000000000000000000000000000000000000000000000000";

  /** A revision ID that will never match any credential. */
  private static final String NON_EXISTENT_REVISION_ID = "rev-999999";

  /** The version query parameter value for deprecated (inline) responses. */
  private static final String VERSION_DEPRECATED = "deprecated";

  /** The version query parameter value for latest (link-based) responses. */
  private static final String VERSION_LATEST = "latest";

  /** Maximum allowed page size for v5 endpoints. */
  private static final int MAX_PAGE_SIZE = 50;

  /** SHA-256 algorithm name for computing attribute IDs in assertions. */
  private static final String SHA256_ALGORITHM = "SHA-256";

  /** Hex format string for byte-to-hex conversion. */
  private static final String HEX_FORMAT = "%02x";

  /** Prefix used for revision identifiers. */
  private static final String REVISION_PREFIX = "rev-";

  private final Tirv5ApiTestClient testClient;
  private final IssuerApiTestClient insertionClient;
  private final TrustedIssuerRepository repository;
  private final TIRv5Mapper tirV5Mapper;
  private final ObjectMapper objectMapper;

  /** Mutable state fields used by the generated test spec interface methods. */
  private String didToRequest;

  private Integer pageSizeParam;
  private String pageAfterParam;
  private String attributeIdParam;
  private String revisionIdParam;
  private String versionParam;

  @BeforeEach
  public void cleanUp() {
    repository.deleteAll();
    didToRequest = null;
    pageSizeParam = null;
    pageAfterParam = null;
    attributeIdParam = null;
    revisionIdParam = null;
    versionParam = null;
  }

  // ========================================================================
  // GET /v5/issuers — List issuers
  // ========================================================================

  @Test
  @Override
  public void getIssuersV5200() throws Exception {
    // Empty list when no issuers exist
    HttpResponse<IssuersResponseVO> response = testClient.getIssuersV5(null, null);
    assertEquals(HttpStatus.OK, response.getStatus(), "An empty list should be returned.");
    assertNotNull(response.body(), "Response body should not be null.");
    assertEquals(0, response.body().getTotal(), "Total should be 0 for empty list.");
    assertTrue(response.body().getItems().isEmpty(), "Items should be empty.");
  }

  /**
   * Tests that listing issuers returns a correctly paginated list with proper total, pageSize,
   * items, and links.
   */
  @Test
  public void getIssuersV5200WithData() throws Exception {
    int issuerCount = 15;
    for (int i = 10; i < 10 + issuerCount; i++) {
      TrustedIssuerVO issuer =
          TrustedIssuerVOTestExample.build().did(String.format(DID_TEMPLATE, i));
      assertEquals(
          HttpStatus.CREATED,
          insertionClient.createTrustedIssuer(issuer).getStatus(),
          "The issuer should have been created.");
    }

    // First page with default page size (10)
    HttpResponse<IssuersResponseVO> response = testClient.getIssuersV5(null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    IssuersResponseVO body = response.body();
    assertNotNull(body);
    assertEquals(issuerCount, body.getTotal(), "Total should reflect all issuers.");
    assertEquals(10, body.getPageSize(), "Default page size should be 10.");
    assertEquals(10, body.getItems().size(), "First page should contain 10 items.");
    assertNotNull(body.getLinks(), "Links should be present.");
    assertNotNull(body.getLinks().getNext(), "Next link should be present when more pages exist.");

    // Second page
    response = testClient.getIssuersV5(10, "1");
    assertEquals(HttpStatus.OK, response.getStatus());
    body = response.body();
    assertNotNull(body);
    assertEquals(issuerCount, body.getTotal());
    assertEquals(5, body.getPageSize(), "Second page should have remaining 5 items.");
    assertEquals(5, body.getItems().size());
  }

  /** Tests custom page size for issuer listing. */
  @Test
  public void getIssuersV5200WithCustomPageSize() throws Exception {
    for (int i = 0; i < 5; i++) {
      TrustedIssuerVO issuer =
          TrustedIssuerVOTestExample.build().did(String.format(DID_TEMPLATE, "issuer-" + i));
      insertionClient.createTrustedIssuer(issuer);
    }

    HttpResponse<IssuersResponseVO> response = testClient.getIssuersV5(3, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    IssuersResponseVO body = response.body();
    assertNotNull(body);
    assertEquals(5, body.getTotal());
    assertEquals(3, body.getPageSize(), "Custom page size should be respected.");
    assertEquals(3, body.getItems().size());
  }

  /** Tests that each issuer entry has both did and href fields populated. */
  @Test
  public void getIssuersV5200EntryStructure() throws Exception {
    insertionClient.createTrustedIssuer(TrustedIssuerVOTestExample.build());

    HttpResponse<IssuersResponseVO> response = testClient.getIssuersV5(null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    IssuersResponseVO body = response.body();
    assertNotNull(body);
    assertEquals(1, body.getItems().size());
    assertNotNull(body.getItems().getFirst().getDid(), "Each entry should have a DID.");
    assertNotNull(body.getItems().getFirst().getHref(), "Each entry should have an href.");
    assertEquals(DID_HAPPYPETS, body.getItems().getFirst().getDid());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 51, 100})
  public void getIssuersV5400(int pageSize) throws Exception {
    this.pageSizeParam = pageSize;
    getIssuersV5400();
  }

  @Override
  public void getIssuersV5400() throws Exception {
    try {
      testClient.getIssuersV5(pageSizeParam, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid page size should result in a 400.");
      return;
    }
    fail("Invalid page size should result in a 400.");
  }

  // ========================================================================
  // GET /v5/issuers/{did} — Get issuer
  // ========================================================================

  @Test
  @Override
  public void getIssuerV5200() throws Exception {
    // Latest version: issuer with no credentials → hasAttributes=false
    TrustedIssuerVO issuer = TrustedIssuerVOTestExample.build();
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<Object> response = testClient.getIssuerV5(DID_HAPPYPETS, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.body());

    Map<String, Object> body = objectMapper.convertValue(response.body(), Map.class);
    assertEquals(DID_HAPPYPETS, body.get("did"), "The DID should be returned.");
    assertNotNull(body.get("attributes"), "Attributes link should be present.");
    assertEquals(
        false,
        body.get("hasAttributes"),
        "hasAttributes should be false when no credentials exist.");
  }

  /**
   * Tests that getting an issuer with credentials returns hasAttributes=true in the latest version
   * format.
   */
  @Test
  public void getIssuerV5200LatestWithCredentials() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<Object> response = testClient.getIssuerV5(DID_HAPPYPETS, VERSION_LATEST);
    assertEquals(HttpStatus.OK, response.getStatus());

    Map<String, Object> body = objectMapper.convertValue(response.body(), Map.class);
    assertEquals(DID_HAPPYPETS, body.get("did"));
    assertEquals(
        true, body.get("hasAttributes"), "hasAttributes should be true when credentials exist.");
    assertTrue(
        body.get("attributes").toString().contains("/attributes"),
        "Attributes should be a link to the attributes sub-resource.");
  }

  /** Tests the deprecated version format: returns inline attributes array. */
  @Test
  public void getIssuerV5200Deprecated() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<Object> response = testClient.getIssuerV5(DID_HAPPYPETS, VERSION_DEPRECATED);
    assertEquals(HttpStatus.OK, response.getStatus());

    Map<String, Object> body = objectMapper.convertValue(response.body(), Map.class);
    assertEquals(DID_HAPPYPETS, body.get("did"));
    assertTrue(
        body.get("attributes") instanceof List,
        "Deprecated version should return attributes as an array.");
    List<?> attributes = (List<?>) body.get("attributes");
    assertEquals(1, attributes.size(), "There should be one attribute for the single credential.");

    // Verify attribute structure
    Map<String, Object> attribute = objectMapper.convertValue(attributes.getFirst(), Map.class);
    assertNotNull(attribute.get("hash"), "Attribute should have a hash.");
    assertNotNull(attribute.get("body"), "Attribute should have a body.");
  }

  /**
   * Tests the deprecated version with no credentials returns a response with no inline attributes.
   * The attributes field may be an empty array or absent (depending on Jackson serialization
   * config).
   */
  @Test
  public void getIssuerV5200DeprecatedNoCredentials() throws Exception {
    TrustedIssuerVO issuer = TrustedIssuerVOTestExample.build();
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<Object> response = testClient.getIssuerV5(DID_HAPPYPETS, VERSION_DEPRECATED);
    assertEquals(HttpStatus.OK, response.getStatus());

    Map<String, Object> body = objectMapper.convertValue(response.body(), Map.class);
    assertEquals(DID_HAPPYPETS, body.get("did"));
    // Verify this is a deprecated-style response (no hasAttributes flag)
    assertFalse(
        body.containsKey("hasAttributes"),
        "Deprecated version should not contain hasAttributes field.");
    // Attributes may be empty list or absent when no credentials exist
    Object attributesValue = body.get("attributes");
    if (attributesValue != null) {
      List<?> attributes = objectMapper.convertValue(attributesValue, List.class);
      assertTrue(
          attributes.isEmpty(),
          "Deprecated version with no credentials should return empty attributes.");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"my-did", "did:something-incomplete", "did.wrong.separator"})
  public void getIssuerV5400(String did) throws Exception {
    didToRequest = did;
    getIssuerV5400();
  }

  @Override
  public void getIssuerV5400() throws Exception {
    try {
      testClient.getIssuerV5(didToRequest, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid DID format should result in a 400.");
      return;
    }
    fail("Invalid DID format should result in a 400.");
  }

  @Test
  @Override
  public void getIssuerV5404() throws Exception {
    HttpResponse<Object> response = testClient.getIssuerV5(DID_NON_EXISTENT, null);
    assertEquals(
        HttpStatus.NOT_FOUND, response.getStatus(), "Non-existent issuer should result in a 404.");
  }

  // ========================================================================
  // GET /v5/issuers/{did}/attributes — List attributes
  // ========================================================================

  @Test
  @Override
  public void getIssuerAttributesV5200() throws Exception {
    // Issuer with no credentials → empty attributes list
    TrustedIssuerVO issuer = TrustedIssuerVOTestExample.build();
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<AttributesResponseVO> response =
        testClient.getIssuerAttributesV5(DID_HAPPYPETS, null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.body());
    assertEquals(0, response.body().getTotal(), "Total should be 0 when no credentials.");
    assertTrue(response.body().getItems().isEmpty(), "Items should be empty.");
  }

  /** Tests that listing attributes returns correct entries with SHA-256 IDs and hrefs. */
  @Test
  public void getIssuerAttributesV5200WithCredentials() throws Exception {
    CredentialsVO cred1 = CredentialsVOTestExample.build().credentialsType("TypeA");
    CredentialsVO cred2 = CredentialsVOTestExample.build().credentialsType("TypeB");
    TrustedIssuerVO issuer = TrustedIssuerVOTestExample.build().credentials(List.of(cred1, cred2));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<AttributesResponseVO> response =
        testClient.getIssuerAttributesV5(DID_HAPPYPETS, null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    AttributesResponseVO body = response.body();
    assertNotNull(body);
    assertEquals(2, body.getTotal(), "Total should match number of credentials.");
    assertEquals(2, body.getItems().size());

    // Verify each entry has a SHA-256 hex id and href
    body.getItems()
        .forEach(
            entry -> {
              assertNotNull(entry.getId(), "Attribute entry should have an ID.");
              assertNotNull(entry.getHref(), "Attribute entry should have an href.");
              // SHA-256 hex strings are 64 characters long
              assertEquals(
                  64,
                  entry.getId().length(),
                  "Attribute ID should be a 64-character SHA-256 hex string.");
            });
  }

  /** Tests pagination of attribute listing. */
  @Test
  public void getIssuerAttributesV5200Pagination() throws Exception {
    CredentialsVO cred1 = CredentialsVOTestExample.build().credentialsType("TypeA");
    CredentialsVO cred2 = CredentialsVOTestExample.build().credentialsType("TypeB");
    CredentialsVO cred3 = CredentialsVOTestExample.build().credentialsType("TypeC");
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(cred1, cred2, cred3));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    // Page 0 with size 2
    HttpResponse<AttributesResponseVO> response =
        testClient.getIssuerAttributesV5(DID_HAPPYPETS, 2, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    AttributesResponseVO body = response.body();
    assertNotNull(body);
    assertEquals(3, body.getTotal(), "Total should reflect all credentials.");
    assertEquals(2, body.getPageSize(), "Page size should match request.");
    assertEquals(2, body.getItems().size());
    assertNotNull(body.getLinks().getNext(), "Next link should be present.");

    // Page 1 with size 2 → 1 remaining item
    response = testClient.getIssuerAttributesV5(DID_HAPPYPETS, 2, "1");
    assertEquals(HttpStatus.OK, response.getStatus());
    body = response.body();
    assertNotNull(body);
    assertEquals(3, body.getTotal());
    assertEquals(1, body.getPageSize(), "Last page should have 1 remaining item.");
    assertEquals(1, body.getItems().size());
  }

  @Override
  public void getIssuerAttributesV5400() throws Exception {
    try {
      testClient.getIssuerAttributesV5(didToRequest, null, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid DID format should result in a 400.");
      return;
    }
    fail("Invalid DID format should result in a 400.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"my-did", "did:something-incomplete", "did.wrong.separator"})
  public void getIssuerAttributesV5400(String did) throws Exception {
    didToRequest = did;
    getIssuerAttributesV5400();
  }

  @Test
  @Override
  public void getIssuerAttributesV5404() throws Exception {
    HttpResponse<AttributesResponseVO> response =
        testClient.getIssuerAttributesV5(DID_NON_EXISTENT, null, null);
    assertEquals(
        HttpStatus.NOT_FOUND, response.getStatus(), "Non-existent issuer should result in a 404.");
  }

  // ========================================================================
  // GET /v5/issuers/{did}/attributes/{attributeId} — Get attribute
  // ========================================================================

  @Test
  @Override
  public void getIssuerAttributeV5200() throws Exception {
    CredentialsVO credential =
        CredentialsVOTestExample.build().claims(List.of(ClaimVOTestExample.build()));
    TrustedIssuerVO issuer = TrustedIssuerVOTestExample.build().credentials(List.of(credential));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    // First, get the attribute ID from the attributes list
    String attributeId = getFirstAttributeId(DID_HAPPYPETS);

    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeV5(DID_HAPPYPETS, attributeId);
    assertEquals(HttpStatus.OK, response.getStatus());
    AttributeDetailsVO body = response.body();
    assertNotNull(body);
    assertEquals(DID_HAPPYPETS, body.getDid(), "The DID should match.");
    assertNotNull(body.getAttribute(), "The attribute should be present.");
    assertNotNull(body.getAttribute().getHash(), "The attribute hash should be present.");
    assertNotNull(body.getAttribute().getBody(), "The attribute body should be present.");
    assertNotNull(body.getAttribute().getIssuerType(), "The issuer type should be present.");

    // Verify the body is valid Base64
    byte[] decodedBody = Base64.getDecoder().decode(body.getAttribute().getBody());
    assertTrue(decodedBody.length > 0, "Decoded body should not be empty.");

    // Verify the hash matches the SHA-256 of the body
    String expectedHash = computeSha256Hex(decodedBody);
    assertEquals(
        expectedHash,
        body.getAttribute().getHash(),
        "The hash should be the SHA-256 hex digest of the body bytes.");
  }

  /**
   * Tests that getting an attribute by ID correctly returns issuerType, tao, and rootTao. Since the
   * TIL API does not yet set these fields, they default to Undefined/null.
   */
  @Test
  public void getIssuerAttributeV5200DefaultFields() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    String attributeId = getFirstAttributeId(DID_HAPPYPETS);

    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeV5(DID_HAPPYPETS, attributeId);
    assertEquals(HttpStatus.OK, response.getStatus());
    AttributeDetailsVO body = response.body();
    assertNotNull(body.getAttribute().getIssuerType(), "IssuerType should have a default value.");
  }

  @Override
  public void getIssuerAttributeV5400() throws Exception {
    try {
      testClient.getIssuerAttributeV5(didToRequest, NON_EXISTENT_ATTRIBUTE_ID);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid DID format should result in a 400.");
      return;
    }
    fail("Invalid DID format should result in a 400.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"my-did", "did:something-incomplete", "did.wrong.separator"})
  public void getIssuerAttributeV5400(String did) throws Exception {
    didToRequest = did;
    getIssuerAttributeV5400();
  }

  @Test
  @Override
  public void getIssuerAttributeV5404() throws Exception {
    // Case 1: Non-existent DID
    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeV5(DID_NON_EXISTENT, NON_EXISTENT_ATTRIBUTE_ID);
    assertEquals(
        HttpStatus.NOT_FOUND, response.getStatus(), "Non-existent DID should result in a 404.");
  }

  /** Tests that a non-existent attribute ID on an existing issuer returns 404. */
  @Test
  public void getIssuerAttributeV5404NonExistentAttribute() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeV5(DID_HAPPYPETS, NON_EXISTENT_ATTRIBUTE_ID);
    assertEquals(
        HttpStatus.NOT_FOUND,
        response.getStatus(),
        "Non-existent attribute ID should result in a 404.");
  }

  // ========================================================================
  // GET /v5/issuers/{did}/attributes/{attributeId}/revisions — List revisions
  // ========================================================================

  @Test
  @Override
  public void getIssuerAttributeRevisionsV5200() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    String attributeId = getFirstAttributeId(DID_HAPPYPETS);

    HttpResponse<RevisionsResponseVO> response =
        testClient.getIssuerAttributeRevisionsV5(DID_HAPPYPETS, attributeId, null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    RevisionsResponseVO body = response.body();
    assertNotNull(body);
    assertEquals(
        1,
        body.getTotal(),
        "Since the system doesn't track revision history, there should be exactly one revision.");
    assertEquals(1, body.getItems().size());
    assertNotNull(body.getItems().getFirst().getId(), "Revision entry should have an ID.");
    assertTrue(
        body.getItems().getFirst().getId().startsWith(REVISION_PREFIX),
        "Revision ID should start with the revision prefix.");
    assertNotNull(body.getItems().getFirst().getHref(), "Revision entry should have an href.");
  }

  @Override
  public void getIssuerAttributeRevisionsV5400() throws Exception {
    try {
      testClient.getIssuerAttributeRevisionsV5(didToRequest, NON_EXISTENT_ATTRIBUTE_ID, null, null);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid DID format should result in a 400.");
      return;
    }
    fail("Invalid DID format should result in a 400.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"my-did", "did:something-incomplete", "did.wrong.separator"})
  public void getIssuerAttributeRevisionsV5400(String did) throws Exception {
    didToRequest = did;
    getIssuerAttributeRevisionsV5400();
  }

  @Test
  @Override
  public void getIssuerAttributeRevisionsV5404() throws Exception {
    // Non-existent DID
    HttpResponse<RevisionsResponseVO> response =
        testClient.getIssuerAttributeRevisionsV5(
            DID_NON_EXISTENT, NON_EXISTENT_ATTRIBUTE_ID, null, null);
    assertEquals(
        HttpStatus.NOT_FOUND, response.getStatus(), "Non-existent DID should result in a 404.");
  }

  /** Tests that a non-existent attribute on an existing issuer returns 404 for revisions. */
  @Test
  public void getIssuerAttributeRevisionsV5404NonExistentAttribute() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<RevisionsResponseVO> response =
        testClient.getIssuerAttributeRevisionsV5(
            DID_HAPPYPETS, NON_EXISTENT_ATTRIBUTE_ID, null, null);
    assertEquals(
        HttpStatus.NOT_FOUND,
        response.getStatus(),
        "Non-existent attribute should result in a 404.");
  }

  // ========================================================================
  // GET /v5/issuers/{did}/attributes/{attributeId}/revisions/{revisionId}
  //   — Get revision
  // ========================================================================

  @Test
  @Override
  public void getIssuerAttributeRevisionV5200() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    String attributeId = getFirstAttributeId(DID_HAPPYPETS);
    String revisionId = getFirstRevisionId(DID_HAPPYPETS, attributeId);

    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeRevisionV5(DID_HAPPYPETS, attributeId, revisionId);
    assertEquals(HttpStatus.OK, response.getStatus());
    AttributeDetailsVO body = response.body();
    assertNotNull(body);
    assertEquals(DID_HAPPYPETS, body.getDid(), "The DID should match.");
    assertNotNull(body.getAttribute(), "The attribute should be present.");
    assertNotNull(body.getAttribute().getHash(), "Hash should be present.");
    assertNotNull(body.getAttribute().getBody(), "Body should be present.");
  }

  @Override
  public void getIssuerAttributeRevisionV5400() throws Exception {
    try {
      testClient.getIssuerAttributeRevisionV5(
          didToRequest, NON_EXISTENT_ATTRIBUTE_ID, NON_EXISTENT_REVISION_ID);
    } catch (HttpClientResponseException e) {
      assertEquals(
          HttpStatus.BAD_REQUEST, e.getStatus(), "Invalid DID format should result in a 400.");
      return;
    }
    fail("Invalid DID format should result in a 400.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"my-did", "did:something-incomplete", "did.wrong.separator"})
  public void getIssuerAttributeRevisionV5400(String did) throws Exception {
    didToRequest = did;
    getIssuerAttributeRevisionV5400();
  }

  @Test
  @Override
  public void getIssuerAttributeRevisionV5404() throws Exception {
    // Non-existent DID
    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeRevisionV5(
            DID_NON_EXISTENT, NON_EXISTENT_ATTRIBUTE_ID, NON_EXISTENT_REVISION_ID);
    assertEquals(
        HttpStatus.NOT_FOUND, response.getStatus(), "Non-existent DID should result in a 404.");
  }

  /** Tests that a non-existent revision ID on an existing attribute returns 404. */
  @Test
  public void getIssuerAttributeRevisionV5404NonExistentRevision() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    String attributeId = getFirstAttributeId(DID_HAPPYPETS);

    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeRevisionV5(
            DID_HAPPYPETS, attributeId, NON_EXISTENT_REVISION_ID);
    assertEquals(
        HttpStatus.NOT_FOUND,
        response.getStatus(),
        "Non-existent revision ID should result in a 404.");
  }

  /** Tests that a non-existent attribute on an existing issuer returns 404 for a revision. */
  @Test
  public void getIssuerAttributeRevisionV5404NonExistentAttribute() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeRevisionV5(
            DID_HAPPYPETS, NON_EXISTENT_ATTRIBUTE_ID, NON_EXISTENT_REVISION_ID);
    assertEquals(
        HttpStatus.NOT_FOUND,
        response.getStatus(),
        "Non-existent attribute should result in a 404.");
  }

  // ========================================================================
  // Cross-endpoint consistency tests
  // ========================================================================

  /**
   * Tests that the attribute ID returned by the list endpoint matches the one accepted by the get
   * endpoint, verifying SHA-256 hash consistency.
   */
  @Test
  public void attributeIdConsistencyAcrossEndpoints() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build()
            .credentials(
                List.of(
                    CredentialsVOTestExample.build().claims(List.of(ClaimVOTestExample.build()))));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    // Get attribute ID from list
    String attributeId = getFirstAttributeId(DID_HAPPYPETS);

    // Use it to get the attribute details
    HttpResponse<AttributeDetailsVO> response =
        testClient.getIssuerAttributeV5(DID_HAPPYPETS, attributeId);
    assertEquals(
        HttpStatus.OK,
        response.getStatus(),
        "Attribute ID from list should be usable in get endpoint.");

    // The hash in the response should equal the attribute ID
    assertEquals(
        attributeId,
        response.body().getAttribute().getHash(),
        "The attribute hash should equal the attribute ID from the list endpoint.");
  }

  /**
   * Tests the full navigation chain: list issuers → get issuer → list attributes → get attribute →
   * list revisions → get revision.
   */
  @Test
  public void fullNavigationChain() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    // Step 1: List issuers
    HttpResponse<IssuersResponseVO> issuersResponse = testClient.getIssuersV5(null, null);
    assertEquals(HttpStatus.OK, issuersResponse.getStatus());
    assertEquals(1, issuersResponse.body().getItems().size());
    String did = issuersResponse.body().getItems().getFirst().getDid();

    // Step 2: Get issuer (latest)
    HttpResponse<Object> issuerResponse = testClient.getIssuerV5(did, VERSION_LATEST);
    assertEquals(HttpStatus.OK, issuerResponse.getStatus());
    Map<String, Object> issuerBody = objectMapper.convertValue(issuerResponse.body(), Map.class);
    assertEquals(true, issuerBody.get("hasAttributes"));

    // Step 3: List attributes
    HttpResponse<AttributesResponseVO> attrsResponse =
        testClient.getIssuerAttributesV5(did, null, null);
    assertEquals(HttpStatus.OK, attrsResponse.getStatus());
    assertEquals(1, attrsResponse.body().getItems().size());
    String attributeId = attrsResponse.body().getItems().getFirst().getId();

    // Step 4: Get attribute
    HttpResponse<AttributeDetailsVO> attrResponse =
        testClient.getIssuerAttributeV5(did, attributeId);
    assertEquals(HttpStatus.OK, attrResponse.getStatus());
    assertEquals(did, attrResponse.body().getDid());

    // Step 5: List revisions
    HttpResponse<RevisionsResponseVO> revisionsResponse =
        testClient.getIssuerAttributeRevisionsV5(did, attributeId, null, null);
    assertEquals(HttpStatus.OK, revisionsResponse.getStatus());
    assertEquals(1, revisionsResponse.body().getItems().size());
    String revisionId = revisionsResponse.body().getItems().getFirst().getId();

    // Step 6: Get revision
    HttpResponse<AttributeDetailsVO> revisionResponse =
        testClient.getIssuerAttributeRevisionV5(did, attributeId, revisionId);
    assertEquals(HttpStatus.OK, revisionResponse.getStatus());
    assertEquals(did, revisionResponse.body().getDid());

    // Attribute from get-attribute and get-revision should be consistent
    assertEquals(
        attrResponse.body().getAttribute().getHash(),
        revisionResponse.body().getAttribute().getHash(),
        "Attribute hash should be consistent across get-attribute and get-revision.");
  }

  /** Tests that issuers created via TIL are visible through both v4 and v5 endpoints. */
  @Test
  public void issuerVisibleInBothV4AndV5() throws Exception {
    TrustedIssuerVO issuer =
        TrustedIssuerVOTestExample.build().credentials(List.of(CredentialsVOTestExample.build()));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer).getStatus());

    // Visible in v5
    HttpResponse<Object> v5Response = testClient.getIssuerV5(DID_HAPPYPETS, null);
    assertEquals(HttpStatus.OK, v5Response.getStatus(), "Issuer should be visible in v5.");

    // The issuer count in v5 should also be 1
    HttpResponse<IssuersResponseVO> listResponse = testClient.getIssuersV5(null, null);
    assertEquals(HttpStatus.OK, listResponse.getStatus());
    assertEquals(1, listResponse.body().getTotal(), "Issuer should appear in v5 list.");
  }

  /** Tests that multiple issuers with different credentials have distinct attribute IDs. */
  @Test
  public void multipleIssuersWithDistinctAttributes() throws Exception {
    TrustedIssuerVO issuer1 =
        TrustedIssuerVOTestExample.build()
            .did("did:elsi:issuer1")
            .credentials(List.of(CredentialsVOTestExample.build().credentialsType("TypeA")));
    TrustedIssuerVO issuer2 =
        TrustedIssuerVOTestExample.build()
            .did("did:elsi:issuer2")
            .credentials(List.of(CredentialsVOTestExample.build().credentialsType("TypeB")));
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer1).getStatus());
    assertEquals(HttpStatus.CREATED, insertionClient.createTrustedIssuer(issuer2).getStatus());

    String attrId1 = getFirstAttributeId("did:elsi:issuer1");
    String attrId2 = getFirstAttributeId("did:elsi:issuer2");

    assertFalse(
        attrId1.equals(attrId2),
        "Different credential types should produce different attribute IDs.");
  }

  // ========================================================================
  // Helper methods
  // ========================================================================

  /**
   * Retrieves the attribute ID of the first credential for the given issuer DID by calling the list
   * attributes endpoint.
   *
   * @param did the issuer's DID
   * @return the first attribute ID
   */
  private String getFirstAttributeId(String did) {
    HttpResponse<AttributesResponseVO> response = testClient.getIssuerAttributesV5(did, null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    assertFalse(response.body().getItems().isEmpty(), "At least one attribute should exist.");
    return response.body().getItems().getFirst().getId();
  }

  /**
   * Retrieves the revision ID of the first revision for the given attribute by calling the list
   * revisions endpoint.
   *
   * @param did the issuer's DID
   * @param attributeId the attribute ID
   * @return the first revision ID
   */
  private String getFirstRevisionId(String did, String attributeId) {
    HttpResponse<RevisionsResponseVO> response =
        testClient.getIssuerAttributeRevisionsV5(did, attributeId, null, null);
    assertEquals(HttpStatus.OK, response.getStatus());
    assertFalse(response.body().getItems().isEmpty(), "At least one revision should exist.");
    return response.body().getItems().getFirst().getId();
  }

  /**
   * Computes the SHA-256 hex digest of the given byte array, for use in test assertions.
   *
   * @param data the data to hash
   * @return the lowercase hex-encoded SHA-256 hash
   * @throws Exception if the SHA-256 algorithm is not available
   */
  private static String computeSha256Hex(byte[] data) throws Exception {
    MessageDigest digest = MessageDigest.getInstance(SHA256_ALGORITHM);
    byte[] hash = digest.digest(data);
    StringBuilder sb = new StringBuilder(hash.length * 2);
    for (byte b : hash) {
      sb.append(String.format(HEX_FORMAT, b));
    }
    return sb.toString();
  }
}
