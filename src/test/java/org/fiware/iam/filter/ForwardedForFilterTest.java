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
package org.fiware.iam.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.ServerFilterChain;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;

class ForwardedForFilterTest {

  private ServerFilterChain chain;
  private HttpRequest<?> request;
  private ForwardHeaderParser forwardHeaderParser;
  private ForwardedForFilter filter;

  @BeforeEach
  void setUp() {

    forwardHeaderParser = mock();
    chain = mock();
    request = mock();
    filter = new ForwardedForFilter(forwardHeaderParser);

    when(request.getMethod()).thenReturn(HttpMethod.GET);
    when(chain.proceed(any())).thenReturn(Mono.empty());
    doReturn(request).when(request).setAttribute(any(), any());
  }

  @ParameterizedTest
  @CsvSource({
    "example.com, 443, https, /api, https://example.com/api",
    "example.com, 80, http, , http://example.com",
    "myserver, 9000, http, /v1, http://myserver:9000/v1"
  })
  void shouldConstructCorrectUriBasedOnForwardedInfo(
      String host, String port, String proto, String prefix, String expectedUri) {

    ForwardedInfo forwardedInfo =
        new ForwardedInfo(null, proto, host, Integer.parseInt(port), null, prefix);
    when(forwardHeaderParser.parse(request)).thenReturn(forwardedInfo);

    filter.doFilter(request, chain);

    verify(chain).proceed(request);
    verify(request).setAttribute(ForwardedForFilter.REQ_ATTR, URI.create(expectedUri));
    verify(request).setAttribute(ForwardedForFilter.FORWARD_INFO_ATTR, forwardedInfo);
    verify(forwardHeaderParser).parse(request);
  }

  @Test
  void shouldReturnHighestPrecedenceOrder() {

    assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
  }
}
