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
import jakarta.persistence.*;
import java.util.List;
import lombok.*;
import lombok.experimental.Accessors;

/** Data entity to represent a credentials claim. */
@Introspected
@Accessors(chain = true)
@NoArgsConstructor
@Data
@Entity
@Getter
@EqualsAndHashCode(exclude = "credential")
@ToString(exclude = "credential")
public class Claim {

  @GeneratedValue @Id private Integer id;

  private String name;
  private String path;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credential_id")
  private Credential credential;

  @OneToMany(
      mappedBy = "claim",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  private List<ClaimValue> claimValues;
}
