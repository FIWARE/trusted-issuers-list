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
package org.fiware.iam;

import io.micronaut.runtime.Micronaut;
import java.util.Map;
import liquibase.Scope;
import liquibase.ui.LoggerUIService;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Application {

  /** Liquibase setting that decides where the update summary is written to. */
  private static final String LIQUIBASE_SUMMARY_OUTPUT = "liquibase.command.showSummaryOutput";

  /** Write the update summary to the log instead of stdout. */
  private static final String LIQUIBASE_SUMMARY_TO_LOG = "LOG";

  /** Environment variable selecting the log output, see logback.xml. */
  private static final String LOG_FORMAT = "LOG_FORMAT";

  /** Log output as one JSON object per line. */
  private static final String LOG_FORMAT_JSON = "JSON";

  public static void main(String[] args) throws Exception {

    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();

    // liquibase prints the applied change sets and its update summary directly to stdout, bypassing
    // the log format (and breaking JSON output). Both are routed through the "liquibase" logger
    // instead: the migrations run on this thread during startup, so a scope with a logging UI
    // applies to them. An explicitly configured summary output is kept.
    Scope.enter(Map.of(Scope.Attr.ui.name(), new LoggerUIService()));
    if (System.getProperty(LIQUIBASE_SUMMARY_OUTPUT) == null) {
      System.setProperty(LIQUIBASE_SUMMARY_OUTPUT, LIQUIBASE_SUMMARY_TO_LOG);
    }

    // the ascii banner would be the only output that is not a JSON line
    boolean jsonLogging = LOG_FORMAT_JSON.equalsIgnoreCase(System.getenv(LOG_FORMAT));
    Micronaut.build(args).mainClass(Application.class).banner(!jsonLogging).start();
  }
}
