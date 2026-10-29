# Implementation Plan: [TIL] Scope queries on the management API

## Overview
Add two read endpoints to the TIL management API (`api/trusted-issuers-list.yaml`), which the eventing reaction and the reconciliation sweep need: `GET /issuer/{did}/scopes` lists the distinct (scope, scopeType) grants of an issuer, each with its credential count, and `GET /issuer?scope=&scopeType=` filters the issuer list by one grant. The (scope, scopeType) data already exists from TIL-1 (#95, Liquibase v0_0_7), so no schema migration is needed. The scope stays internal to the management API and must never appear in a `/v4` or `/v5` response.

Decisions (taken from the analysis comment, since the ticket leaves them open):
- The count is the number of credentials the issuer holds under that pair.
- The scopes list is not paginated, because an issuer has few grants.
- Reads are not logged at INFO. A lookup miss goes to DEBUG (`Issuer <did> ...`).
- A `null` scopeType matches only untyped grants, never "any type". This is the same rule as `CredentialRepository`.

## Steps

### Step 1: `GET /issuer/{did}/scopes`
**Spec** (`api/trusted-issuers-list.yaml`):
- New path `/issuer/{did}/scopes`, `get`, `operationId: getIssuerScopes`, tag `issuer`, parameter `#/components/parameters/Id`.
- `200` returns a JSON array of a new schema `IssuerScope` (generated as `IssuerScopeVO`). Its fields are `scope` (string, required), `scopeType` (`$ref ScopeType`, absent for untyped grants) and `credentialCount` (integer, minimum 1, required). Describe it as internal to the management API and never exposed through the trusted-issuers-registry, matching the wording of the existing params.
- `404` returns `ProblemDetails` for an unknown issuer.
- The description says that credentials managed directly (without a scope) are not listed, so an issuer that only has those returns `[]`.

**Repository** (`src/main/java/org/fiware/iam/repository/CredentialRepository.java`):
- Add `List<Credential> findByTrustedIssuerDidAndScopeIsNotNull(String did)`, with the short Javadoc plus `@param`/`@return` like its neighbours. Keep the H2/MySql/Postgres subinterfaces empty.
- Group in Java, not in a native `GROUP BY` projection: the set is small, the query stays portable across the three dialects, and the code stays simpler. If a step agent prefers SQL, an `@Introspected` record with a `@Query` `GROUP BY` in the base interface is acceptable. This project uses `micronaut-data-jdbc` only (no Hibernate), so `@Query` is native SQL against tables and columns (`credential.trusted_issuer_id`, `scope`, `scope_type`), not JPQL. Keep it portable ANSI SQL, because all three dialect repos inherit it.

**Controller** (`src/main/java/org/fiware/iam/rest/TrustedIssuersListController.java`):
- Implement `getIssuerScopes(String did)`. If the issuer does not exist (`trustedIssuerRepository.existsById(did)` or similar), return `HttpResponse.notFound()` and log at DEBUG `Issuer {} has no scopes to list, it does not exist`.
- Otherwise group the scoped credentials by (scope, scopeType). A null scopeType needs a null-safe key, for example a private record `Grant(String scope, @Nullable String scopeType)`. Map each group to `IssuerScopeVO` (`scopeType` via `ScopeTypeVO.fromValue`, null stays null) and sort deterministically by scope, then scopeType with untyped first. Name any constants.
- Add a Javadoc that explains the why (the `granted` reaction mode and the report), plus `@param`/`@return`, matching the other public controller methods.

**Tests** (`src/test/java/org/fiware/iam/rest/TrustedIssuersListControllerTest.java`):
- The regenerated `IssuerApiTestSpec` gains `getIssuerScopes200`/`getIssuerScopes404`. Implement both, otherwise the test class does not compile.
- Parameterized (`@MethodSource`) cases:
  - Exactly the distinct pairs, with the right counts. Build a dedicated fixture: `grantEveryPairAndAnUnscopedCredential()` (TrustedIssuersListControllerTest ~868) grants only `ORDER_SCOPE`, untyped and typed, with one credential per pair, so it cannot tell a real count from a constant `1` or from a claim count. The fixture needs at least one pair with ≥2 credentials, the same scope untyped and typed, a second scope, and one unscoped credential, which must not appear. Assert the exact list of (scope, scopeType, credentialCount) entries in the expected order.
  - An issuer with only unattributed credentials (created via `createTrustedIssuer(vo, null, null)`) returns `[]`.
  - A pair whose credentials were revoked (`deleteCredentialsByScope`) disappears.
  - Unknown did returns 404.

**Acceptance:** `mvn spotless:apply && mvn test` passes. The generated client exposes `getIssuerScopes`. No change to `/v4`/`/v5` code.

### Step 2: Filter `GET /issuer` by scope and scopeType
**Spec**: add a new parameter `#/components/parameters/ScopeFilter` (name `scope`, `in: query`, optional, string) and the existing `#/components/parameters/ScopeType` to `getIssuers`, after `PageSize`/`Page`, so the generated signature becomes `getIssuers(pageSize, page, scope, scopeType)`. Extend the description: with a scope, only issuers holding credentials granted by that exact (scope, scopeType) pair are listed, which the reconciliation sweep needs. Add a `400` response (`ProblemDetails`) for an invalid page size, a blank scope, an unknown scope type, or a scope type without a scope. Do not reuse `OptionalScope`: its description is written for writes ("When omitted, the credentials are managed directly..."), and OpenAPI 3.0 cannot override the description of a `$ref` parameter. `ScopeFilter` says that it lists only issuers holding credentials granted by that scope and that the scope is never exposed through the trusted-issuers-registry. `ScopeType` already reads correctly for a filter and is reused. The generated Java signature is the same either way.

**Repository** (`TrustedIssuerRepository.java`, base interface only):
- Add a paged lookup of issuers by grant, split IsNull/Equals like `CredentialRepository`, behind a `default` dispatcher, for example `Page<TrustedIssuer> findByGrant(String scope, @Nullable String scopeType, Pageable pageable)`.
- Preferred: derived queries `findDistinctByCredentialsScopeAndCredentialsScopeTypeIsNull(String scope, Pageable)` / `...ScopeTypeEquals(...)`. If Micronaut Data counts the joined rows wrongly (a total that includes duplicates), fall back to explicit `@Query` + `countQuery` with `SELECT DISTINCT` / `COUNT(DISTINCT ...)`. With `micronaut-data-jdbc` (no Hibernate) these are native SQL against `trusted_issuer` and `credential` (`trusted_issuer_id`, `scope`, `scope_type`), not JPQL. Keep them portable ANSI SQL so they run unchanged on H2, MySQL and Postgres, because the base interface is inherited by all three dialect repos. The query must only select issuer DIDs, without fetching credentials.
- Keep the `did` sort applied via `Pageable`.

**Controller** (`getIssuers`):
- New `@Nullable String scope, @Nullable ScopeTypeVO scopeType` parameters. A scopeType without a scope throws `IllegalArgumentException(SCOPE_TYPE_WITHOUT_SCOPE)`, the same as `createTrustedIssuer`. A blank scope goes through `requireScope` and returns 400 (confirmed by the reviewer). Micronaut may bind `?scope=` as `""` or as `null`. If it arrives as `null`, the request would silently return the unfiltered list, which is the worst outcome for the reconciliation sweep. Pin down the actual binding and make sure `?scope=` returns 400 either way (e.g. inspect the raw query parameter if it binds as `null`).
- Without a scope, behaviour is unchanged (`findAll(pagination)`). With a scope, use the new repository query and convert the type with `toColumn(scopeType)`. Update the Javadoc.

**Tests:**
- Update every `testClient.getIssuers(a, b)` call site in `TrustedIssuersListControllerTest` (and any other test) to the 4-arg form.
- New parameterized cases:
  - The filter returns only issuers holding that pair. Untyped and typed grants of the same scope are distinguished, and so is another scope. An issuer with only unscoped credentials is never matched.
  - An issuer holding several credentials under the pair is listed once, and `total` is asserted exactly. A join-based count is where duplicates would show up, so this fixture must hold ≥2 credentials under the filtered pair.
  - Paging works with the filter.
  - An unknown scope returns an empty page.
  - `?scopeType=dataspace-membership` without a scope returns 400.
  - `?scope=` (empty) returns 400, through the raw `httpClient`, so that the binding (`""` vs `null`) is exercised for real.
  - An unknown scopeType value returns 400 (raw `httpClient` call, as in the existing `CREATE_ISSUER_TYPE_ONLY_PATH` test).

**Acceptance:** `mvn spotless:apply && mvn test` passes. Unfiltered `GET /issuer` responses are unchanged.

### Step 3: Prove the scope never reaches `/v4` or `/v5`
**Tests only**, in `src/test/java/org/fiware/iam/rest/TrustedIssuerRegistryControllerTest.java` and `TrustedIssuerRegistryV5ControllerTest.java`, by extending the existing "the scope of a credential must not reach the registry" sections (v4 ~246, v5 ~935), not by adding a parallel set. Reuse their fixtures and constants where they fit:
- Grant credentials to an issuer under a distinctive typed and untyped scope (named constants), plus one unscoped credential.
- Fetch every registry response for it as a raw string through `httpClient`: `/v4/issuers/` and `/v4/issuers/{did}`; `/v5/issuers/`, `/v5/issuers/{did}`, `/v5/issuers/{did}/attributes`, `/v5/issuers/{did}/attributes/{attributeId}`, and the two `revisions` endpoints.
- Assert that neither the scope string nor `dataspace-membership` occurs anywhere in the raw body, including inside the Base64-decoded attribute bodies.
- Use `@ParameterizedTest` + `@MethodSource` over the endpoint paths.
- Update the README only if it documents the management endpoints individually. It currently does not, so this is probably not needed.

**Acceptance:** `mvn spotless:check && mvn test` passes. These tests would fail if a TIR mapper ever mapped `scope`/`scopeType`.
