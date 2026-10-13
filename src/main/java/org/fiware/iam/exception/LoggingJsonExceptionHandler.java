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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.JsonExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import io.micronaut.json.JsonSyntaxException;
import jakarta.inject.Singleton;

/**
 * Logs the requests rejected by micronaut's {@link JsonExceptionHandler}, see {@link
 * RejectedRequestLogging}.
 */
@Produces
@Singleton
@Replaces(JsonExceptionHandler.class)
public class LoggingJsonExceptionHandler
    extends RejectedRequestLogging<JsonSyntaxException, Object> {

  public LoggingJsonExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
    super(new JsonExceptionHandler(responseProcessor));
  }
}
