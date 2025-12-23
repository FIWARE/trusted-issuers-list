package org.fiware.iam.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import lombok.Data;

@ConfigurationProperties("micronaut.server.forward-headers")
@Data
public class ForwardedForConfig {

    private String protocolHeader;
    private String portHeader;
    private String hostHeader;
    private String prefixHeader;
}
