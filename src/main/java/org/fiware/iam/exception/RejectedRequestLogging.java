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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs a request that micronaut itself rejects as invalid (malformed body, missing or unconvertible
 * arguments) the same way as {@link IllegalArgumentExceptionHandler} does, and then answers it with
 * micronaut's own handler, so that the response does not change.
 *
 * <p>Without it, such a request only shows up as a 400 in the request log, without any reason.
 *
 * @param <E> the exception the handler is responsible for
 * @param <R> the response type of the original handler
 */
@RequiredArgsConstructor
public abstract class RejectedRequestLogging<E extends Throwable, R>
    implements ExceptionHandler<E, R> {

  /** Named after the concrete handler, so the line tells which kind of rejection it was. */
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ExceptionHandler<E, R> delegate;

  @Override
  public R handle(HttpRequest request, E exception) {
    log.warn(
        "Rejected {} {} with 400: {}",
        request.getMethod(),
        request.getUri(),
        exception.getMessage(),
        exception);
    return delegate.handle(request, exception);
  }
}
