package org.fiware.iam.filter;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.ssl.ServerSslConfiguration;
import org.fiware.iam.configuration.ForwardedForConfig;
import org.reactivestreams.Publisher;

import java.net.URI;

@Filter(Filter.MATCH_ALL_PATTERN)
public class ForwardedForFilter implements HttpServerFilter, Ordered {

    public static final String HOST_ATTR = "server-host";
    public static final String PORT_ATTR = "server-port";
    public static final String PROTO_ATTR = "server-proto";
    public static final String PREFIX_ATTR = "server-prefix";
    public static final String REQ_ATTR = "server-req";

    private final ForwardedForConfig config;
    private final int serverPort;
    private final String defaultServerProtocol;
    private final String defaultHost;
    public ForwardedForFilter(ForwardedForConfig config, HttpServerConfiguration serverConfiguration,
                              ServerSslConfiguration sslConfig) {
        this.config = config;
        this.serverPort = serverConfiguration.getPort().orElse(HttpServerConfiguration.DEFAULT_PORT);
        this.defaultServerProtocol = sslConfig.isEnabled() ? "https" : "http";
        this.defaultHost = serverConfiguration.getHost().orElse("localhost");
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        String hostHeader = config != null ? getHeaderValue(request, config.getHostHeader(), defaultHost) : defaultHost;
        String portHeader = config != null ? getHeaderValue(request, config.getPortHeader(), String.valueOf(serverPort)): String.valueOf(serverPort);
        String protoHeader = config != null ? getHeaderValue(request, config.getProtocolHeader(), defaultServerProtocol): defaultServerProtocol;
        if (portHeader.equals("-1")) {
            portHeader = String.valueOf(request.getServerAddress().getPort());
        }
        String prefixHeader = config != null ? getHeaderValue(request, config.getPrefixHeader(), ""): "";

        String portPart = "";
        if (!("http".equalsIgnoreCase(protoHeader) && "80".equals(portHeader))
                && !("https".equalsIgnoreCase(protoHeader) && "443".equals(portHeader))) {
            portPart = ":" + portHeader;
        }

        String reqUrl = String.format("%s://%s%s%s", protoHeader, hostHeader, portPart, prefixHeader);

        HttpRequest<?> modifiedRequest = request.setAttribute(HOST_ATTR, hostHeader)
                .setAttribute(PORT_ATTR, portHeader)
                .setAttribute(PROTO_ATTR, protoHeader)
                .setAttribute(PREFIX_ATTR, prefixHeader)
                .setAttribute(REQ_ATTR, URI.create(reqUrl));

        return chain.proceed(modifiedRequest);
    }

    private String getHeaderValue(HttpRequest<?> request, String headerName, String defaultValue) {
        try {
            return request.getHeaders().get(headerName, String.class, defaultValue);
        } catch (NullPointerException e) {
            return defaultValue;
        }

    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
