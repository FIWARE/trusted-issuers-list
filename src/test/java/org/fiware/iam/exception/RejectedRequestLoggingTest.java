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
package org.fiware.iam.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Requests that micronaut itself rejects are logged with their reason, while the answer stays the
 * one of micronaut's own handler.
 */
@MicronautTest
class RejectedRequestLoggingTest {

  @Inject
  @Client("/")
  HttpClient client;

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level previousLevel;

  @BeforeEach
  void attachAppender() {
    previousLevel = exceptionLogger().getLevel();
    exceptionLogger().setLevel(Level.DEBUG);
    appender.start();
    exceptionLogger().addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    exceptionLogger().detachAppender(appender);
    exceptionLogger().setLevel(previousLevel);
  }

  @Test
  void aMalformedBodyIsLoggedAndAnsweredByMicronaut() {
    HttpClientResponseException response =
        reject(
            HttpRequest.POST("/issuer", "{\"did\": broken")
                .contentType(MediaType.APPLICATION_JSON_TYPE));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
    assertTrue(
        response.getResponse().getBody(String.class).orElseThrow().contains("Invalid JSON"),
        "The answer has to stay the one of micronaut.");
    assertRejectionLogged(LoggingJsonExceptionHandler.class, "Rejected POST /issuer with 400: ");
  }

  @Test
  void aMissingBodyIsLogged() {
    reject(HttpRequest.POST("/issuer", "").contentType(MediaType.APPLICATION_JSON_TYPE));

    assertRejectionLogged(
        LoggingUnsatisfiedRouteHandler.class,
        "Rejected POST /issuer with 400: Required Body [trustedIssuerVO] not specified");
  }

  @Test
  void aBodyThatCannotBeConvertedIsLogged() {
    reject(
        HttpRequest.POST(
                "/issuer",
                "{\"did\":\"did:web:a.org\",\"credentials\":[{\"validFor\":{\"from\":\"notadate\"}}]}")
            .contentType(MediaType.APPLICATION_JSON_TYPE));

    assertRejectionLogged(
        LoggingConversionErrorHandler.class,
        "Rejected POST /issuer with 400: Failed to convert argument [trustedIssuerVO]");
  }

  private HttpClientResponseException reject(HttpRequest<?> request) {
    return assertThrows(
        HttpClientResponseException.class, () -> client.toBlocking().exchange(request));
  }

  private void assertRejectionLogged(Class<?> handler, String messagePrefix) {
    List<ILoggingEvent> events =
        appender.list.stream()
            .filter(event -> event.getLoggerName().equals(handler.getName()))
            .toList();
    assertEquals(2, events.size(), "A rejection is one WARN line plus its trace on DEBUG.");
    ILoggingEvent warn = events.get(0);
    assertEquals(Level.WARN, warn.getLevel());
    assertTrue(
        warn.getFormattedMessage().startsWith(messagePrefix),
        "Unexpected message: " + warn.getFormattedMessage());
    assertNull(warn.getThrowableProxy(), "Any client can send invalid requests, keep it one line.");
    ILoggingEvent debug = events.get(1);
    assertEquals(Level.DEBUG, debug.getLevel());
    assertNotNull(debug.getThrowableProxy(), "On DEBUG, the rejection comes with its stack trace.");
  }

  private static Logger exceptionLogger() {
    return (Logger) LoggerFactory.getLogger("org.fiware.iam.exception");
  }
}
