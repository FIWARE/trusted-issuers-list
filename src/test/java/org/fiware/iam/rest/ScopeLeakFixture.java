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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.client.HttpClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.fiware.iam.til.api.IssuerApiTestClient;
import org.fiware.iam.til.model.CredentialsVO;
import org.fiware.iam.til.model.CredentialsVOTestExample;
import org.fiware.iam.til.model.ScopeTypeVO;
import org.fiware.iam.til.model.TrustedIssuerVO;

/** Grant data fixture and raw-body check proving the world-readable registry never reveals it. */
final class ScopeLeakFixture {

  static final String TYPED_SCOPE = "urn:ngsi-ld:product-order:typed-grant-hidden-from-registry";
  static final String UNTYPED_SCOPE =
      "urn:ngsi-ld:product-order:untyped-grant-hidden-from-registry";

  /** Every string a grant consists of; none of them may occur in a registry response. */
  static final List<String> GRANT_DATA =
      List.of(TYPED_SCOPE, UNTYPED_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP.getValue());

  /** One unscoped, one untyped and one typed credential, so none is deduplicated away. */
  static final int GRANTED_CREDENTIALS = 3;

  private static final String ISSUER_SCOPES_PATH = "/issuer/%s/scopes";
  private static final String UNSCOPED_TYPE = "UnscopedCredential";
  private static final String UNTYPED_GRANT_TYPE = "UntypedGrantCredential";
  private static final String TYPED_GRANT_TYPE = "TypedGrantCredential";

  private ScopeLeakFixture() {}

  /**
   * Gives the issuer one credential per kind of grant and verifies, through the management API,
   * that the grant data is stored, so its absence from the registry is not vacuous.
   */
  static void grantUnderEveryKindOfScope(
      IssuerApiTestClient insertionClient, HttpClient httpClient, String did) {
    insertionClient.createTrustedIssuer(
        new TrustedIssuerVO().did(did).credentials(List.of(credential(UNSCOPED_TYPE))), null, null);
    insertionClient.replaceCredentialsByScope(
        did, List.of(credential(UNTYPED_GRANT_TYPE)), UNTYPED_SCOPE, null);
    insertionClient.replaceCredentialsByScope(
        did, List.of(credential(TYPED_GRANT_TYPE)), TYPED_SCOPE, ScopeTypeVO.DATASPACE_MEMBERSHIP);

    String grants = httpClient.toBlocking().retrieve(String.format(ISSUER_SCOPES_PATH, did));
    GRANT_DATA.forEach(
        token ->
            assertTrue(grants.contains(token), "The management API has to know grant " + token));
  }

  /**
   * Fails if grant data occurs in the raw body or in any Base64-decodable string inside it, so a
   * leak through the encoded attribute bodies is caught as well.
   */
  static void assertNoGrantData(ObjectMapper objectMapper, String path, String rawBody)
      throws IOException {
    List<String> texts = new ArrayList<>(List.of(rawBody));
    collectDecoded(objectMapper.readTree(rawBody), texts);
    for (String token : GRANT_DATA) {
      for (String text : texts) {
        assertFalse(
            text.contains(token),
            String.format("%s must not reveal '%s', but returned: %s", path, token, text));
      }
    }
  }

  private static CredentialsVO credential(String type) {
    return CredentialsVOTestExample.build().validFor(null).credentialsType(type);
  }

  private static void collectDecoded(JsonNode node, List<String> decoded) {
    if (node.isTextual()) {
      try {
        decoded.add(new String(Base64.getDecoder().decode(node.asText()), StandardCharsets.UTF_8));
      } catch (IllegalArgumentException notBase64) {
        // plain text, already covered by the raw body
      }
    }
    node.forEach(child -> collectDecoded(child, decoded));
  }
}
