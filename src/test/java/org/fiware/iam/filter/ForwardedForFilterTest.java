package org.fiware.iam.filter;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import org.fiware.iam.configuration.ForwardedForConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForwardedForFilterTest {

    private ForwardedForConfig config;
    private HttpServerConfiguration serverConfig;
    private ServerSslConfiguration sslConfig;
    private ServerFilterChain chain;
    private MutableHttpRequest<?> request;
    private Map<CharSequence, CharSequence> headers;

    @BeforeEach
    void setUp() {

        config = mock();
        serverConfig = mock();
        sslConfig = mock();
        chain = mock();
        request = HttpRequest.create(HttpMethod.GET, "http://example.com/test");
        headers = new HashMap<>();

        when(serverConfig.getPort()).thenReturn(Optional.of(8080));
        when(serverConfig.getHost()).thenReturn(Optional.of("localhost"));
        when(sslConfig.isEnabled()).thenReturn(false);

        when(config.getHostHeader()).thenReturn("X-Forwarded-Host");
        when(config.getPortHeader()).thenReturn("X-Forwarded-Port");
        when(config.getProtocolHeader()).thenReturn("X-Forwarded-Proto");
        when(config.getPrefixHeader()).thenReturn("X-Forwarded-Prefix");

        when(chain.proceed(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldSetDefaultAttributesWhenHeadersAreMissing() {

        ForwardedForFilter filter = new ForwardedForFilter(config, serverConfig, sslConfig);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        filter.doFilter(request, chain);

        verify(chain).proceed(captor.capture());
        HttpRequest<?> modified = captor.getValue();

        assertEquals("localhost", modified.getAttribute(ForwardedForFilter.HOST_ATTR).get());
        assertEquals("8080", modified.getAttribute(ForwardedForFilter.PORT_ATTR).get());
        assertEquals("http", modified.getAttribute(ForwardedForFilter.PROTO_ATTR).get());
        assertEquals(URI.create("http://localhost:8080"), modified.getAttribute(ForwardedForFilter.REQ_ATTR).get());
    }

    @ParameterizedTest
    @CsvSource({
            "example.com, 443, https, /api, https://example.com/api",
            "example.com, 80, http, , http://example.com",
            "myserver, 9000, http, /v1, http://myserver:9000/v1"
    })
    void shouldConstructCorrectUriBasedOnHeaders(String host, String port, String proto, String prefix, String expectedUri) {

        headers.put("X-Forwarded-Host", host);
        headers.put("X-Forwarded-Port", port);
        headers.put("X-Forwarded-Proto", proto);
        if (prefix != null) {
            headers.put("X-Forwarded-Prefix", prefix);
        }
        request.headers(headers);
        ForwardedForFilter filter = new ForwardedForFilter(config, serverConfig, sslConfig);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        filter.doFilter(request, chain);

        verify(chain).proceed(captor.capture());
        assertEquals(URI.create(expectedUri), captor.getValue().getAttribute(ForwardedForFilter.REQ_ATTR).get());    }

    @Test
    void shouldUseHttpsDefaultWhenSslIsEnabled() {

        when(sslConfig.isEnabled()).thenReturn(true);
        ForwardedForFilter filter = new ForwardedForFilter(config, serverConfig, sslConfig);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);

        filter.doFilter(request, chain);

        verify(chain).proceed(captor.capture());
        assertEquals("https", captor.getValue().getAttribute(ForwardedForFilter.PROTO_ATTR).get());    }

    @Test
    void shouldReturnHighestPrecedenceOrder() {

        ForwardedForFilter filter = new ForwardedForFilter(config, serverConfig, sslConfig);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, filter.getOrder());
    }

    @Test
    void shouldNotFailIfConfigIsNull() {

        ForwardedForFilter filter = new ForwardedForFilter(null, serverConfig, sslConfig);
        assertDoesNotThrow(() -> filter.doFilter(request, chain));
    }
}