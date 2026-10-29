# Trusted Issuers List

## Overview
A Micronaut microservice that manages trusted credential issuers. It exposes a CRUD management API (Trusted Issuers List, TIL) and read-only EBSI-compatible consumption APIs (Trusted Issuers Registry, TIR `/v4` and `/v5`). All of them share one database.

## Tech Stack
- Language: Java 21
- Build: Maven (no wrapper, use `mvn`)
- Framework: Micronaut 4.10.5 (Micronaut Data JDBC, Micronaut Validation)
- Persistence: JPA annotations on entities, schema managed by Liquibase
- Mapping: MapStruct 1.6.3
- Code generation: OpenAPI Generator 7.17.0 with micronaut-openapi-codegen 4.5.0
- Test: JUnit 5, Micronaut Test, Mockito 5.21.0
- Database: MySQL (default), PostgreSQL, H2 (tests)
- Formatting: Spotless with google-java-format and a license header (`license-header.txt`)

## Project Structure
```
api/
  trusted-issuers-list.yaml          # TIL management API (CRUD + scoped grants + scope queries)
  trusted-issuers-registry.yaml      # TIR /v4 (read-only)
  trusted-issuers-registry-v5.yaml   # TIR /v5 (read-only)
src/main/java/org/fiware/iam/
  Application.java                   # entry point, routes liquibase logging through logback
  TILMapper.java                     # TIL VOs <-> entities
  TIRMapper.java / TIRv5Mapper.java  # entities -> /v4 and /v5 VOs (Base64/SHA256), must never expose scope
  rest/TrustedIssuersListController.java      # implements til IssuerApi
  rest/TrustedIssuerRegistryController.java   # /v4
  rest/TrustedIssuerRegistryV5Controller.java # /v5
  repository/                        # TrustedIssuer -> Credential -> Claim -> ClaimValue entities,
                                     # CredentialRepository/TrustedIssuerRepository + H2/MySql/Postgres subinterfaces
  exception/                         # exception types + ExceptionHandlers (ProblemDetails, logging)
  filter/, configuration/            # X-Forwarded-* handling
src/main/resources/db/migration/
  changelog.xml                      # includeAll path="v0", so new files in v0/ are picked up automatically
  v0/changelog-v0_0_1..7.xml         # v0_0_6 adds credential.scope, v0_0_7 credential.scope_type (precondition-guarded)
src/test/java/org/fiware/iam/rest/
  TrustedIssuersListControllerTest.java       # implements IssuerApiTestSpec, scoped-grant tests from line ~369
  TrustedIssuersListLoggingTest.java          # asserts exact INFO log lines
  TrustedIssuerRegistryControllerTest.java    # /v4, includes "scope never reaches the registry" tests
  TrustedIssuerRegistryV5ControllerTest.java  # /v5
src/test/resources/application.yaml           # H2 in-memory
```

## Build & Test
```bash
mvn clean compile                  # includes OpenAPI code generation (target/generated-sources)
mvn test                           # H2 in-memory
mvn test -Dtest=TrustedIssuersListControllerTest
mvn spotless:apply                 # format + license header (not bound to a phase, run it before committing)
mvn spotless:check
```

## Key Conventions
- **OpenAPI-first**: changing an operation's parameters in `api/*.yaml` changes the generated `IssuerApi` interface and the generated `IssuerApiTestClient`. Every controller and every test call site must follow. Packages: `org.fiware.iam.{til,tir,tir.v5}.{api,model}`, models end in `VO`. Optional query params arrive as `@Nullable String` (`useOptional=false`).
- **Generated test helpers**: tests implement `*TestSpec` (one method per operation and status), call through `*TestClient`, and build data with `*VOTestExample`.
- **Scoped grants**: a grant is the pair (`Credential.scope`, `Credential.scopeType`), both nullable. The scope says what granted a credential (e.g. a ProductOrder id), and the type says what kind of thing it is (enum `ScopeType`, e.g. `dataspace-membership`). A `null` scope means managed directly via `PUT /issuer/{did}`, and scoped operations never touch such a credential. A `null` scopeType matches only untyped grants: derived queries need an IsNull/Equals split behind a `default` method, because binding null never matches. `scope` is internal: it is absent from the API models and from every `/v4` and `/v5` projection.
- **Dialect repos**: `H2/MySql/Postgres*Repository` are empty subinterfaces gated by `@Requires(property = "datasources.default.dialect")`. Queries live in the base interface.
- **Liquibase**: one file per schema version under `v0/`, ending with a `tagDatabase` changeset. Use `preConditions onFail="MARK_RAN"` guards (see v0_0_6). Keep MySQL's key-length limits in mind (`trusted_issuer_id` is varchar(768)).
- **Lombok**: `@Data`, `@Accessors(chain = true)`, `@RequiredArgsConstructor`, `@Slf4j`.
- **No magic constants**: use `private static final` names (see the top of the controller and the test).
- **Doc density**: classes carry a one-line Javadoc. Public controller methods and repository methods carry a short Javadoc that explains *why*, plus `@param`/`@return`. Liquibase changesets get an XML comment that explains the reason. Match this density and do not inflate it.
- **Logging**: failures are logged once, by their exception handler. Unexpected ones go to ERROR with the stack trace (`CatchAllExceptionHandler`). Client errors are one line with the reason, with the stack trace only on DEBUG: 400 at WARN (including the requests micronaut rejects itself, via `RejectedRequestLogging`), and 409 at INFO as `Issuer <did> already exists, ...`. Changes of the list are logged at INFO with did (and scope) only, without counts, for example `Issuer <did> granted credentials for scope <scope>`. Full bodies and lookup misses go to DEBUG. Lines about an issuer start with `Issuer <did>`. `TrustedIssuersListLoggingTest` asserts the exact strings. See the README section "Logging" for the env vars.
- **Tests**: `@ParameterizedTest` + `@MethodSource` returning `Stream<Arguments>`. `@BeforeEach` wipes the repository.

## Important Files
- `pom.xml` ~250-320: openapi-generator executions `tir`, `til`, `tir-v5`. ~324: build-helper. ~504: spotless.
- `src/main/java/org/fiware/iam/rest/TrustedIssuersListController.java`: `requireScope`, `replaceCredentialsByScope`, `deleteCredentialsByScope`, `updateIssuer` (keeps scoped credentials).
- `src/main/java/org/fiware/iam/repository/CredentialRepository.java`: scope-based find/delete.
- `IMPLEMENTATION_PLAN.md`: current ticket plan.
