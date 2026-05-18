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
package org.fiware.iam.exception;

import lombok.Getter;

/** Exception to be thrown in all conflict-cases */
public class ConflictException extends RuntimeException {

  @Getter private final String entityId;

  public ConflictException(String message, String entityId) {
    super(message);
    this.entityId = entityId;
  }

  public ConflictException(String message, Throwable cause, String entityId) {
    super(message, cause);
    this.entityId = entityId;
  }
}
