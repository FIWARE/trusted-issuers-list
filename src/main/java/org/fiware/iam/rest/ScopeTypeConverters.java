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
package org.fiware.iam.rest;

import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import jakarta.inject.Singleton;
import org.fiware.iam.til.model.ScopeTypeVO;

/**
 * Converts a scope type to and from its wire value. An unknown value is rejected with a 400:
 * micronaut would otherwise bind it as {@code null} and address the untyped grants instead.
 */
@Singleton
public class ScopeTypeConverters implements TypeConverterRegistrar {

  private static final String UNKNOWN_SCOPE_TYPE =
      "Unknown scope type '%s' - omit it to address untyped grants.";

  @Override
  public void register(MutableConversionService conversionService) {
    conversionService.addConverter(ScopeTypeVO.class, String.class, ScopeTypeVO::getValue);
    conversionService.addConverter(
        CharSequence.class,
        ScopeTypeVO.class,
        value ->
            ScopeTypeVO.toOptional(value.toString())
                .orElseThrow(
                    () -> new IllegalArgumentException(String.format(UNKNOWN_SCOPE_TYPE, value))));
  }
}
