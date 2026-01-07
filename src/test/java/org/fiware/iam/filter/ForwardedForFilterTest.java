package org.fiware.iam.filter;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.filter.ServerFilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void shouldConstructCorrectUriBasedOnForwardedInfo(String host, String port, String proto, String prefix, String expectedUri) {

        ForwardedInfo forwardedInfo = new ForwardedInfo(null, proto, host, Integer.parseInt(port), null, prefix);
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