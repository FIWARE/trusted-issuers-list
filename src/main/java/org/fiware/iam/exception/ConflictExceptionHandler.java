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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.tir.model.ProblemDetailsVO;

/** Catch all {@link ConflictException} and translate them into proper 409 responses. */
@Produces
@Singleton
@Requires(classes = {ConflictException.class, ExceptionHandler.class})
@Slf4j
public class ConflictExceptionHandler
    implements ExceptionHandler<ConflictException, HttpResponse<ProblemDetailsVO>> {

  @Override
  public HttpResponse<ProblemDetailsVO> handle(HttpRequest request, ConflictException exception) {
    log.debug("Received  a conflict for {}.", request, exception);
    return HttpResponse.status(HttpStatus.CONFLICT)
        .body(
            new ProblemDetailsVO()
                .status(HttpStatus.CONFLICT.getCode())
                .detail(exception.getLocalizedMessage())
                .instance(URI.create(exception.getEntityId()))
                .title("Conflict."));
  }
}
