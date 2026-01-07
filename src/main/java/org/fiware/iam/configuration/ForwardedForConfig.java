package org.fiware.iam.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Getter;

/**
 * Configuration for processing forwarded request headers.
 * This configuration is used to extract information from headers such as
 * Forwarded and X-Forwarded-* to determine the original request details.
 * `Forwarded` header, as defined in RFC 7239, is preferred over `X-Forwarded-*` headers.
 */
@ConfigurationProperties("micronaut.server.forward-headers")
@Getter
public class ForwardedForConfig {


    /**
     * The name of the header that carries the protocol.
     * Default: "X-Forwarded-Proto".
     */
    private String protocolHeader;

    /**
     * The name of the header that carries the port.
     * Default: "X-Forwarded-Port".
     */
    private String portHeader;

    /**
     * The name of the header that carries the host.
     * Default: "X-Forwarded-Host".
     */
    private String hostHeader;

    /**
     * The name of the header that carries the context path or prefix of the request.
     * Default: "X-Forwarded-Prefix".
     */
    private String prefixHeader;

    /**
     * The name of the header that carries the client’s IP address.
     * Default: "X-Forwarded-For".
     */
    private String forHeader;
}
