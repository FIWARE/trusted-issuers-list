# Trusted Issuers List

## Overview
A Micronaut-based microservice that manages trusted credential issuers. Exposes two APIs: a CRUD management API (Trusted Issuers List) and a read-only EBSI-compatible consumption API (Trusted Issuers Registry v4). Both share the same database.

## Tech Stack
- Language: Java 21
- Build: Maven (wrapper not included; use `mvn`)
- Framework: Micronaut 4.10.5 (Micronaut Data JDBC, Micronaut Validation)
- ORM: JPA/Hibernate (schema managed by Liquibase, not hbm2ddl)
- Mapping: MapStruct 1.6.3
- Code Generation: OpenAPI Generator 7.17.0 with micronaut-openapi-codegen 4.5.0
- Test: JUnit 5, Micronaut Test, Mockito 5.21.0
- Database: MySQL (default), PostgreSQL, H2 (tests)

## Project Structure
```
api/                              # OpenAPI YAML specs (input to code generation)
  trusted-issuers-list.yaml       # TIL management API (v4.0.0)
  trusted-issuers-registry.yaml   # TIR read-only API (v4, EBSI-compatible)
src/main/java/org/fiware/iam/
  Application.java                # Micronaut entry point
  TILMapper.java                  # MapStruct: TIL VOs <-> DB entities
  TIRMapper.java                  # MapStruct: DB entities -> TIR VOs (Base64/SHA256)
  rest/
    TrustedIssuersListController.java   # CRUD endpoints (implements IssuerApi)
    TrustedIssuerRegistryController.java # Read-only v4 endpoints (implements TirApi)
  repository/
    TrustedIssuer.java            # JPA entity (PK: did)
    Credential.java               # JPA entity (FK: trusted_issuer_id)
    Claim.java                    # JPA entity (FK: credential_id)
    ClaimValue.java               # JPA entity (FK: claim_id)
    TrustedIssuerRepository.java  # Base repo interface
    CredentialRepository.java     # Base repo interface
    H2*/MySql*/Postgres*Repository.java  # Dialect-specific implementations
  exception/                      # Custom exception types + Micronaut handlers
  filter/                         # X-Forwarded-* header handling
  configuration/                  # Config beans
src/main/resources/
  application.yaml                # Server + DB config (port 8080, MySQL default)
  db/migration/                   # Liquibase changelogs
    changelog.xml                 # Root changelog (includes v0/)
    v0/changelog-v0_0_1.xml       # Initial tables
    v0/changelog-v0_0_2.xml       # DID field to 768 chars
    v0/changelog-v0_0_3.xml       # Add path to claim
    v0/changelog-v0_0_4.xml       # Change dates to timestamps
src/test/java/org/fiware/iam/rest/
  TrustedIssuersListControllerTest.java    # Tests TIL CRUD (implements IssuerApiTestSpec)
  TrustedIssuerRegistryControllerTest.java # Tests TIR v4 read (implements TirApiTestSpec)
src/test/resources/application.yaml        # H2 in-memory config for tests
```

## Build & Test
```bash
mvn clean compile          # Build (includes OpenAPI code generation)
mvn test                   # Run tests (H2 in-memory DB)
mvn clean install          # Full build + tests + package
mvn clean generate-sources # Only generate code from OpenAPI specs
```

## Key Conventions
- **OpenAPI-first**: YAML specs in `api/` drive code generation. Generated interfaces go to `target/` under packages `org.fiware.iam.{tir,til}.{api,model}`. Controllers implement generated API interfaces. Models are suffixed with `VO`.
- **Generated test clients**: OpenAPI Generator produces `*TestClient` and `*TestSpec` interfaces. Test classes implement `*TestSpec` and use `*TestClient` for HTTP calls.
- **Generated test examples**: `*VOTestExample` classes provide builder methods for test data.
- **Dialect-specific repos**: Each DB dialect has its own `@JdbcRepository` implementation gated by `@Requires(property = "datasources.default.dialect")`.
- **MapStruct mappers**: Use `@Mapper(componentModel = "jsr330")`. Complex mappings (Base64, SHA256, JSON) are default methods.
- **Controller pattern**: Annotated with `@Controller("${general.basepath:/}")`, uses `@RequiredArgsConstructor` for DI.
- **Pagination**: TIL uses offset-based (page, pageSize). TIR v4 uses cursor-based with `page[after]` (integer) and `page[size]`, max 100.
- **Exception handling**: Custom `ExceptionHandler` beans map exceptions to RFC 7807 ProblemDetails responses.
- **Lombok**: `@Data`, `@Accessors(chain = true)`, `@RequiredArgsConstructor` used throughout.
- **No magic constants**: Named constants for defaults, param names, etc.

## Important Files
- `pom.xml` lines 248-307: OpenAPI Generator plugin config (executions: `tir`, `til`)
- `pom.xml` lines 308-350: build-helper plugin (adds generated sources to classpath)
- `api/trusted-issuers-registry.yaml`: TIR v4 spec (2 endpoints: list issuers, get issuer)
- `api/trusted-issuers-list.yaml`: TIL spec (5 endpoints: CRUD on issuers)
- `src/main/java/org/fiware/iam/rest/TrustedIssuerRegistryController.java`: Reference for v5 controller pattern
- `src/main/java/org/fiware/iam/TIRMapper.java`: Reference for v5 mapper (Base64/SHA256 encoding)
