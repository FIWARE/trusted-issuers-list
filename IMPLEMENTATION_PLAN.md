# Implementation Plan: [TIL] Typed scopes in the trusted-issuers-list

## Overview
Add a nullable `scopeType` to each grant, next to the existing `Credential.scope`. A data-space membership grant can then be told apart from a ProductOrder grant without reserving a prefix in `scope` (architecture D-11). The scope endpoints get an optional `scopeType` query parameter. An **absent `scopeType` addresses only untyped grants, never "any type"**, so every existing caller keeps addressing exactly the rows it addresses today. `scopeType` stays internal like `scope` and never appears in a `/v4` or `/v5` response.

## Steps

### Step 1: Persist the scope type (schema, entity, repository)
Add the column and the exact-pair repository queries. No API change in this step.

- **Migration**: new `src/main/resources/db/migration/v0/changelog-v0_0_7.xml`. `changelog.xml` uses `includeAll path="v0"`, so there is no need to register it.
  - Changeset `006-add-scope-type-to-credential`: `addColumn` `scope_type VARCHAR(255)`, nullable, no default, no data migration. Guard it the way v0_0_6 does: `preConditions onFail="MARK_RAN"`, `tableExists credential` and `not columnExists scope_type`.
  - Add an XML comment explaining why the column exists: it types a grant (e.g. data-space membership vs ProductOrder) without reserving a prefix in `scope`. `null` means untyped, and every row written before this version is untyped.
  - No new index. Lookups already go through `idx_credential_scope`, and `scope_type` has low cardinality.
  - Changeset `007-tag-v0.0.7` with `<tagDatabase tag="v0.0.7"/>`.
- **Entity** `repository/Credential.java`: add `@Nullable private String scopeType;` after `scope`, with a short Javadoc: the kind of thing `scope` identifies, `null` = untyped (the ProductOrder grants that existed before), and internal like `scope`. Extend the `scope` Javadoc with one sentence saying a grant is identified by the pair (scope, scopeType).
- **Repository** `repository/CredentialRepository.java`: replace `findByTrustedIssuerDidAndScope` / `deleteByTrustedIssuerDidAndScope` with
  - `List<Credential> findByTrustedIssuerDidAndScopeAndScopeType(String did, String scope, @Nullable String scopeType)`
  - `void deleteByTrustedIssuerDidAndScopeAndScopeType(String did, String scope, @Nullable String scopeType)`

  Both must match the **exact** pair: a `null` scopeType must translate to `scope_type IS NULL`. A plain derived query binds `null` as `scope_type = NULL`, which never matches. Implement it with abstract derived methods `...AndScopeType(…)` (non-null) and `...AndScopeTypeIsNull(did, scope)`, plus `default` methods that pick one of them based on `scopeType`. Keep the ticket's method names as the public entry points. An explicit `@Query` that works on H2, MySQL and Postgres is also fine. The dialect subinterfaces (`H2/MySql/PostgresCredentialRepository`) are empty and inherit the methods. Verify that no dialect override is needed.
- **Callers**: in `rest/TrustedIssuersListController.java`, change the two `deleteByTrustedIssuerDidAndScope(did, scope)` calls to the new method with `null`. Behaviour stays identical. In `TrustedIssuersListControllerTest`, change the `scopedCredentials` helper to call `findByTrustedIssuerDidAndScopeAndScopeType(did, scope, null)`.
- **Acceptance**: `mvn clean test` is green with no test changes beyond the helper. Liquibase applies v0_0_7 on H2. Existing rows keep `scope_type = NULL`. Run `mvn spotless:apply`.

### Step 2: Expose `scopeType` on the scope endpoints
Add the optional query parameter and the controller logic.

- **OpenAPI** `api/trusted-issuers-list.yaml`:
  - New component parameter `ScopeType` (`in: query`, `required: false`, `type: string`, example e.g. `dataspace-membership`).
  - Description: the kind of thing `scope` identifies. A grant is identified by (scope, scopeType). When omitted, only untyped grants are addressed, never grants of any type. It requires a scope. It is not part of the credential and is never exposed through the trusted-issuers-registry.
  - Reference it from `POST /issuer` (next to `OptionalScope`), and from `PUT` and `DELETE /issuer/{did}/credential` (next to `Scope`).
  - Update the operation descriptions and the `400` descriptions: "No scope provided" becomes something like "No scope provided, or a blank scope type / a scope type without a scope".
- **Controller** `rest/TrustedIssuersListController.java`. The generated signatures gain a trailing `@Nullable String scopeType`.
  - `createTrustedIssuer(vo, scope, scopeType)`: reject a `scopeType` without a `scope` with 400 (`IllegalArgumentException` with a named constant message). An unscoped typed credential could not be addressed by any scoped operation. When a scope is given, set both `scope` and `scopeType` on every credential.
  - `replaceCredentialsByScope(did, scope, scopeType, body)`: delete and save using the exact (scope, scopeType) pair, and set `scopeType` on each saved credential.
  - `deleteCredentialsByScope(did, scope, scopeType)`: delete the exact pair only.
  - Validation: add `requireScopeType(scopeType)`. `null` is allowed. A blank value gets a 400, because `""` would otherwise become a separate type that nobody addresses deliberately. Keep it next to `requireScope` and give both named-constant messages.
  - `updateIssuer` stays unchanged. Typed credentials always carry a scope, so the existing `getScope() == null` filter already leaves them alone. Add one sentence to its Javadoc noting this.
  - Logging: untyped lines must stay byte-for-byte identical, because `TrustedIssuersListLoggingTest` asserts them. For typed grants, append the type, for example `Issuer <did> granted credentials for scope <scope> of type <scopeType>`, and the same for `created for scope` and `revoked credentials of scope`. Use one small private helper to format "scope [of type]" so there are no duplicated branches.
  - Update the Javadoc `@param` lines for the new parameter, kept terse.
- **Generated client call sites**: every `testClient.createTrustedIssuer / replaceCredentialsByScope / deleteCredentialsByScope` call in `TrustedIssuersListControllerTest`, `TrustedIssuersListLoggingTest`, `TrustedIssuerRegistryControllerTest` and `TrustedIssuerRegistryV5ControllerTest` (~70 calls) gets an extra `null` argument so that it compiles. Do not change any semantics.
- **TIRMapper / TIRv5Mapper**: untouched.
- **Docs**: in `AGENTS.md`, update the "Scoped grants" convention to mention `scopeType` and the absent-means-untyped rule, and the logging line "(and scope, scope type)". If the README documents the scope endpoints, add `scopeType` there.
- **Acceptance**: `mvn clean test` and `mvn spotless:check` are green. All existing tests pass unchanged apart from the extra `null` argument.

### Step 3: Parameterized (scope, scopeType) tests and registry isolation
Prove the four acceptance criteria. Test code only, unless a test exposes a bug from the earlier steps.

- `TrustedIssuersListControllerTest`: add constants, e.g. `MEMBERSHIP_SCOPE_TYPE = "dataspace-membership"` and `OTHER_SCOPE_TYPE`. Add a helper `scopedCredentials(did, scope, scopeType)` and keep the 2-arg overload delegating with `null`.
  - `@ParameterizedTest @MethodSource` over (scope, scopeType) pairs: `(ORDER_SCOPE, null)`, `(ORDER_SCOPE, MEMBERSHIP_SCOPE_TYPE)`, `(ORDER_SCOPE, OTHER_SCOPE_TYPE)`. For each pair, grant it plus every *other* pair with the **same scope string**, and grant an unscoped credential through `POST /issuer` without scope.
    - **Coexist**: after `PUT`, every pair holds its own credential.
    - **Independent replace**: `PUT` of one pair with a new credential list changes only that pair.
    - **Independent delete**: `DELETE` of one pair removes only that pair. The other pairs and the unscoped credential survive. This covers "a scoped call without `scopeType` never touches a typed grant, and vice versa".
  - Parameterized `POST /issuer?scope=…[&scopeType=…]`: credentials are attributed to the exact pair and revocable only through it.
  - Parameterized 400 cases: blank `scopeType` on PUT, DELETE and POST, and `scopeType` without `scope` on POST.
  - `updateIssuer` keeps typed grants, the counterpart of `updateIssuer_keepsWhatAScopeGranted`.
  - Migration/untyped: a credential written without `scopeType` is persisted with `scopeType == null` and is found by `findByTrustedIssuerDidAndScopeAndScopeType(did, scope, null)`.
- `TrustedIssuerRegistryControllerTest` (/v4) and `TrustedIssuerRegistryV5ControllerTest` (/v5): next to the existing "scope changes neither body nor hash" tests, add a case where the same credential is granted untyped and then typed. The attribute body, hash (and the v5 attribute id) must be identical, and no response contains the scopeType value. This covers "nothing new appears in a /v4 or /v5 response".
- `TrustedIssuersListLoggingTest`: one case asserting the typed INFO line, e.g. `Issuer did:web:consumer.org granted credentials for scope <scope> of type <type>`.
- **Acceptance**: `mvn clean test` and `mvn spotless:check` are green. Every ticket acceptance bullet maps to at least one test.
