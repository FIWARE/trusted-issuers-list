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
package org.fiware.iam.repository;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.util.Collection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

/** Data entity to map an issuers credentials */
@Introspected
@Data
@Accessors(chain = true)
@Entity
@EqualsAndHashCode(exclude = "trustedIssuer")
@ToString(exclude = "trustedIssuer")
public class Credential {

  @GeneratedValue @Id private Integer id;

  @Nullable private Instant validFrom;

  @Nullable private Instant validTo;

  private String credentialsType;

  /**
   * The type of issuer for this credential (e.g., TI, TAO, RootTAO). Defaults to "Undefined" for
   * backward compatibility with v4.
   */
  @Nullable private String issuerType;

  /** DID of the Trusted Accreditation Organization that accredited this issuer. */
  @Nullable private String tao;

  /** DID of the Root Trusted Accreditation Organization in the trust chain. */
  @Nullable private String rootTao;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "trusted_issuer_id")
  private TrustedIssuer trustedIssuer;

  @OneToMany(
      mappedBy = "credential",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private Collection<Claim> claims;
}
