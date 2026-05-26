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
package org.fiware.iam.configuration;

import io.micronaut.context.annotation.ConfigurationInject;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import lombok.Getter;

/**
 * Configuration for processing forwarded request headers. This configuration is used to extract
 * information from headers such as Forwarded and X-Forwarded-* to determine the original request
 * details. `Forwarded` header, as defined in RFC 7239, is preferred over `X-Forwarded-*` headers.
 */
@ConfigurationProperties("micronaut.server.forward-headers")
@Getter
public class ForwardedForConfig {

  /** The name of the header that carries the protocol. Default: "X-Forwarded-Proto". */
  private String protocolHeader;

  /** The name of the header that carries the port. Default: "X-Forwarded-Port". */
  private String portHeader;

  /** The name of the header that carries the host. Default: "X-Forwarded-Host". */
  private String hostHeader;

  /**
   * The name of the header that carries the context path or prefix of the request. Default:
   * "X-Forwarded-Prefix".
   */
  private String prefixHeader;

  /** The name of the header that carries the client’s IP address. Default: "X-Forwarded-For". */
  private String forHeader;

  @ConfigurationInject
  public ForwardedForConfig(
      @Nullable String protocolHeader,
      @Nullable String portHeader,
      @Nullable String hostHeader,
      @Nullable String prefixHeader,
      @Nullable String forHeader) {

    this.protocolHeader = protocolHeader;
    this.portHeader = portHeader;
    this.hostHeader = hostHeader;
    this.prefixHeader = prefixHeader;
    this.forHeader = forHeader;
  }
}
