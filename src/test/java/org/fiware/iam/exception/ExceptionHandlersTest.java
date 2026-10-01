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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import org.fiware.iam.tir.model.ProblemDetailsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Every failure is logged with its reason and stack trace, at a level matching its severity: an
 * invalid request at WARN, a conflict at INFO (stack trace only on DEBUG), an unexpected failure at
 * ERROR.
 */
class ExceptionHandlersTest {

  private static final HttpRequest<?> REQUEST = HttpRequest.POST("/issuer", "{}");

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
  void anIllegalArgumentIsAWarningWithItsStackTrace() {
    HttpResponse<ProblemDetailsVO> response =
        new IllegalArgumentExceptionHandler()
            .handle(REQUEST, new IllegalArgumentException("Provided string is not a valid did."));

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
    assertEquals("Provided string is not a valid did.", response.body().getDetail());
    ILoggingEvent event = singleEvent();
    assertEquals(Level.WARN, event.getLevel());
    assertEquals(
        "Rejected POST /issuer with 400: Provided string is not a valid did.",
        event.getFormattedMessage());
    assertNotNull(event.getThrowableProxy(), "An invalid request is logged with its stack trace.");
  }

  @Test
  void aConflictIsOneInfoLineWithTheDetailsOnDebug() {
    HttpResponse<ProblemDetailsVO> response =
        new ConflictExceptionHandler()
            .handle(REQUEST, new ConflictException("Issuer already exists.", "did:web:issuer.org"));

    assertEquals(HttpStatus.CONFLICT, response.getStatus());
    assertEquals(2, appender.list.size(), "A conflict is one INFO line plus its details on DEBUG.");
    ILoggingEvent info = appender.list.get(0);
    assertEquals(Level.INFO, info.getLevel());
    assertEquals("Rejected POST /issuer with 409", info.getFormattedMessage());
    assertNull(info.getThrowableProxy(), "On INFO, a conflict is a single line.");
    ILoggingEvent debug = appender.list.get(1);
    assertEquals(Level.DEBUG, debug.getLevel());
    assertEquals(
        "Conflict for POST /issuer on did:web:issuer.org: Issuer already exists.",
        debug.getFormattedMessage());
    assertNotNull(debug.getThrowableProxy(), "On DEBUG, the conflict comes with its stack trace.");
  }

  @Test
  void anUnexpectedExceptionIsAnErrorWithItsStackTrace() {
    HttpResponse<ProblemDetailsVO> response =
        new CatchAllExceptionHandler().handle(REQUEST, new IllegalStateException("broken"));

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    ILoggingEvent event = singleEvent();
    assertEquals(Level.ERROR, event.getLevel());
    assertEquals(
        "Unexpected error while handling POST /issuer: broken", event.getFormattedMessage());
    assertNotNull(
        event.getThrowableProxy(), "An unexpected failure has to be logged with its stack trace.");
  }

  private ILoggingEvent singleEvent() {
    assertEquals(1, appender.list.size(), "Every failure has to be logged exactly once.");
    return appender.list.get(0);
  }

  private static Logger exceptionLogger() {
    return (Logger) LoggerFactory.getLogger("org.fiware.iam.exception");
  }
}
