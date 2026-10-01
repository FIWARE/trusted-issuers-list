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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fiware.iam.repository.TrustedIssuerRepository;
import org.fiware.iam.til.api.IssuerApiTestClient;
import org.fiware.iam.til.model.CredentialsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** The INFO lines of the list have to say what actually happened to an issuer. */
@RequiredArgsConstructor
@MicronautTest
class TrustedIssuersListLoggingTest {

  private static final String ISSUER_DID = "did:web:consumer.org";
  private static final String ORDER_SCOPE = "urn:ngsi-ld:product-order:first";

  public final IssuerApiTestClient testClient;
  public final TrustedIssuerRepository repository;

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void attachAppender() {
    repository.deleteAll();
    appender.start();
    controllerLogger().addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    controllerLogger().detachAppender(appender);
  }

  @Test
  void grantingToAnUnknownIssuerLogsItsCreation() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(new CredentialsVO().credentialsType("UserCredential")));

    assertEquals(
        List.of(
            "Issuer did:web:consumer.org created for scope " + ORDER_SCOPE,
            "Issuer did:web:consumer.org granted credentials for scope " + ORDER_SCOPE),
        infoLines());
  }

  @Test
  void anEmptyGrantIsLoggedAsRevocation() throws Exception {
    testClient.replaceCredentialsByScope(
        ISSUER_DID, ORDER_SCOPE, List.of(new CredentialsVO().credentialsType("UserCredential")));
    appender.list.clear();

    testClient.replaceCredentialsByScope(ISSUER_DID, ORDER_SCOPE, List.of());

    assertEquals(
        List.of("Issuer did:web:consumer.org revoked credentials of scope " + ORDER_SCOPE),
        infoLines(),
        "Replacing the credentials of a scope with none must not read like a grant.");
  }

  private List<String> infoLines() {
    return appender.list.stream()
        .filter(event -> event.getLevel() == Level.INFO)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  private static Logger controllerLogger() {
    return (Logger) LoggerFactory.getLogger(TrustedIssuersListController.class);
  }
}
