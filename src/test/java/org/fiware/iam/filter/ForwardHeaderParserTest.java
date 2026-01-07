package org.fiware.iam.filter;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import org.fiware.iam.configuration.ForwardedForConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForwardHeaderParserTest {

    private ForwardedForConfig config;
    private ForwardHeaderParser parser;
    private ServerSslConfiguration sslConfig;
    @BeforeEach
    void setup() {

        config = mock();
        when(config.getForHeader()).thenReturn("X-Forwarded-For");
        when(config.getProtocolHeader()).thenReturn("X-Forwarded-Proto");
        when(config.getHostHeader()).thenReturn("X-Forwarded-Host");
        when(config.getPortHeader()).thenReturn("X-Forwarded-Port");
        when(config.getPrefixHeader()).thenReturn("X-Forwarded-Prefix");

        HttpServerConfiguration serverConfig = mock();
        when(serverConfig.getPort()).thenReturn(java.util.Optional.of(8080));
        when(serverConfig.getHost()).thenReturn(java.util.Optional.of("myhost"));

        sslConfig = mock();
        when(sslConfig.isEnabled()).thenReturn(false);

        parser = new ForwardHeaderParser(config, serverConfig, sslConfig);
    }

    @Test
    void shouldReturnDefaultValuesWhenNoHeaders() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED)).thenReturn(null);
        when(headers.get(anyString())).thenReturn(null);

        ForwardedInfo info = parser.parse(request);

        assertEquals("myhost", info.getForwardedHost(), "Host should default to server host");
        assertEquals("http", info.getForwardedProto(), "Protocol should default to server protocol");
        assertEquals(8080, info.getForwardedPort(), "Port should default to server port");
        assertNull(info.getForwardedFor(), "ForwardedFor should be null");
        assertNull(info.getForwardedBy(), "ForwardedBy should be null");
        assertEquals("", info.getForwardedPrefix(), "Prefix should default to empty string");
    }

    @Test
    void shouldParseForwardedHeaderForAllDirectives() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED))
                .thenReturn("for=192.0.2.1;proto=https;host=example.com:8443;by=proxy1");

        ForwardedInfo info = parser.parse(request);

        assertEquals("192.0.2.1", info.getForwardedFor(), "ForwardedFor should match 'for'");
        assertEquals("https", info.getForwardedProto(), "Protocol should match 'proto'");
        assertEquals("example.com", info.getForwardedHost(), "Host should be parsed without port");
        assertEquals(8443, info.getForwardedPort(), "Port should be parsed from host");
        assertEquals("proxy1", info.getForwardedBy(), "By should match");
    }

    @Test
    void shouldIgnoreInvalidPortInForwardedHeader() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED))
                .thenReturn("host=example.com:invalid");

        ForwardedInfo info = parser.parse(request);

        assertEquals("example.com", info.getForwardedHost());
        assertEquals(80, info.getForwardedPort(), "Port should remain default if invalid");
    }

    @Test
    void shouldParseLegacyXForwardedHeaders() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);

        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED)).thenReturn(null);
        when(headers.get("X-Forwarded-For")).thenReturn("1.2.3.4");
        when(headers.get("X-Forwarded-Proto")).thenReturn("https");
        when(headers.get("X-Forwarded-Host")).thenReturn("legacyhost");
        when(headers.get("X-Forwarded-Port")).thenReturn("9090");
        when(headers.get("X-Forwarded-Prefix")).thenReturn("/prefix");

        ForwardedInfo info = parser.parse(request);

        assertEquals("1.2.3.4", info.getForwardedFor());
        assertEquals("https", info.getForwardedProto());
        assertEquals("legacyhost", info.getForwardedHost());
        assertEquals(9090, info.getForwardedPort());
        assertEquals("/prefix", info.getForwardedPrefix());
    }

    @Test
    void shouldPreferForwardedHeaderOverLegacyHeaders() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);

        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED))
                .thenReturn("for=10.0.0.1;proto=https;host=example.com:8443;by=proxy1");
        when(headers.get("X-Forwarded-For")).thenReturn("1.2.3.4");

        ForwardedInfo info = parser.parse(request);

        assertEquals("10.0.0.1", info.getForwardedFor());
    }

    @Test
    void shouldHandleQuotedValuesInForwardedHeader() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);

        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED))
                .thenReturn("for=\"192.0.2.1\";proto=\"https\";host=\"example.com:8443\";by=\"proxy1\"");

        ForwardedInfo info = parser.parse(request);

        // Assert quotes removed
        assertEquals("192.0.2.1", info.getForwardedFor());
        assertEquals("https", info.getForwardedProto());
        assertEquals("example.com", info.getForwardedHost());
        assertEquals(8443, info.getForwardedPort());
        assertEquals("proxy1", info.getForwardedBy());
    }

    @Test
    void shouldReturnDefaultPortWhenForwardedPortNotSet() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);

        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED)).thenReturn("host=example.com");

        ForwardedInfo info = parser.parse(request);

        assertEquals(80, info.getForwardedPort());
    }

    @Test
    void shouldHandleNullLegacyHeaderNames() {

        HttpRequest<?> request= mock();
        HttpHeaders headers = mock(HttpHeaders.class);

        when(request.getHeaders()).thenReturn(headers);
        when(config.getForHeader()).thenReturn(null);
        when(config.getProtocolHeader()).thenReturn(null);
        when(config.getHostHeader()).thenReturn(null);
        when(config.getPortHeader()).thenReturn(null);
        when(config.getPrefixHeader()).thenReturn(null);

        ForwardedInfo info = parser.parse(request);

        assertEquals("myhost", info.getForwardedHost());
        assertEquals(8080, info.getForwardedPort());
        assertEquals("http", info.getForwardedProto());
        assertEquals("", info.getForwardedPrefix());
    }

    @ParameterizedTest(name = "should set default port {1} for protocol {0} when forwardedPort=0")
    @CsvSource({
            "http, 80",
            "https, 443"
    })
    void shouldSetDefaultPortWhenForwardedPortIsZero(String proto, int expectedPort) {

        HttpRequest<?> request = mock(HttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        when(request.getHeaders()).thenReturn(headers);
        when(headers.get(HttpHeaders.FORWARDED)).thenReturn("proto=" + proto + ";host=example.com");

        ForwardedInfo info = parser.parse(request);

        assertEquals(expectedPort, info.getForwardedPort());
    }

    @Test
    void shouldUseServerPortWhenForwardedPortIsMinusOne() {

        HttpServerConfiguration serverConfig = mock();
        InetSocketAddress socketAddress = new InetSocketAddress(8080);
        HttpRequest<?> request = mock(HttpRequest.class);
        HttpHeaders headers = mock(HttpHeaders.class);

        when(request.getHeaders()).thenReturn(headers);
        when(serverConfig.getPort()).thenReturn(java.util.Optional.of(-1));
        when(serverConfig.getHost()).thenReturn(java.util.Optional.of("myhost"));
        when(request.getServerAddress()).thenReturn(socketAddress);

        parser = new ForwardHeaderParser(config, serverConfig, sslConfig);

        ForwardedInfo info = parser.parse(request);

        if (info.getForwardedPort() == -1) {
            info.setForwardedPort(request.getServerAddress().getPort());
        }

        // Assert
        assertEquals(8080, info.getForwardedPort());
    }
}
