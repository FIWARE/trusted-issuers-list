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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Data entity to represent the value of a claim. Could be of multiple types, thus will be stored as
 * a json-stirng
 */
@Accessors(chain = true)
@Data
@NoArgsConstructor
@Introspected
@Getter
@Entity
@EqualsAndHashCode(exclude = "claim")
@ToString(exclude = "claim")
public class ClaimValue {

  @Id @GeneratedValue private Integer id;

  // should be serialized json
  private String value;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id")
  @JsonIgnore
  private Claim claim;

  public ClaimValue(Integer id, String value, @Nullable Claim claim) {
    this.id = id;
    this.value = value;
    this.claim = claim;
  }
}
