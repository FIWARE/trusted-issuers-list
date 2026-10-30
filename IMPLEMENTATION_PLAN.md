# Implementation Plan: [TIL] Delete the unattributed credentials of an issuer (migration path)

## Overview
`DELETE /issuer/{did}/credential` (operationId `deleteCredentialsByScope`) gets an explicit
`unscoped=true` marker. The marker removes only the credentials with no scope, which are the ones
managed directly through `PUT /issuer/{did}`. Every scoped grant and the issuer itself are kept.
This is only needed for the CM-5 `adoptUnattributed` migration. The marker is explicit, so a
request that forgets `scope` still gets a `400` and never deletes anything.

Design decisions (based on the analysis comment):
- Marker: an optional boolean query parameter `unscoped`, on the DELETE only. A reserved scope
  value was rejected because `scope` is a free string, so any reserved value could clash with a
  real scope.
- The DELETE gets its own optional scope parameter. The shared `Scope` stays `required: true`
  for the PUT, so `replaceCredentialsByScope` does not change.
- Validation. Each of these cases throws `IllegalArgumentException`, so the existing
  `IllegalArgumentExceptionHandler` answers `400` and logs one WARN line:
  - `unscoped` absent or `false`, and no scope or a blank one: the current `requireScope` path,
    with a message that now also mentions `unscoped=true`.
  - `unscoped=true` together with a scope.
  - `unscoped=true` together with a `scopeType`.
- No schema migration. `credential.scope` is already nullable, and claims cascade
  (`CascadeType.ALL` plus `onDelete="CASCADE"` in v0_0_1).

## Steps

### Step 1: Explicit `unscoped` marker on `DELETE /issuer/{did}/credential`
Update the API, the repository and the controller together, and make the existing test call sites
follow. Regenerating the API changes the signatures of `IssuerApi` and `IssuerApiTestClient`, so
the build only compiles when all of them change in the same step.

Files:
- `api/trusted-issuers-list.yaml`
  - Under `components/parameters`, add:
    - `RevokedScope`: `name: scope`, `in: query`, `required: false`. Description: what granted
      the credentials. Required unless `unscoped=true`, and must not be combined with it. Never
      exposed through the trusted-issuers-registry.
    - `Unscoped`: `name: unscoped`, `in: query`, `required: false`, `type: boolean`. Description:
      addresses the credentials that carry no scope, i.e. the ones managed through the issuer
      endpoints. It is an explicit marker so that a forgotten scope is rejected instead of
      deleting them. Must not be combined with `scope` or `scopeType`. Intended for migrating
      directly managed entries into grants.
  - DELETE `/issuer/{did}/credential`: replace `Scope` with `RevokedScope`, keep `ScopeType`,
    and append `Unscoped` as the last parameter. In the operation description, add one sentence
    about `unscoped=true`. Change the 400 description to "No scope and no `unscoped=true`, both
    of them, a scope type with `unscoped=true`, or an unknown scope type".
  - The PUT keeps `#/components/parameters/Scope` (required).
- `src/main/java/org/fiware/iam/repository/CredentialRepository.java`
  - Add `void deleteByTrustedIssuerDidAndScopeIsNull(String did);`. Give it a short Javadoc with
    `@param` in the style of the file: removes the directly managed credentials and leaves every
    grant alone.
- `src/main/java/org/fiware/iam/rest/TrustedIssuersListController.java`
  - The signature becomes
    `deleteCredentialsByScope(String did, @Nullable String scope, @Nullable ScopeTypeVO scopeType, @Nullable Boolean unscoped)`.
    Check the parameter order against the regenerated `target/generated-sources/.../til/api/IssuerApi.java`.
  - Add `private static final String` messages next to `NO_SCOPE_PROVIDED`, for example
    `UNSCOPED_WITH_SCOPE` and `UNSCOPED_WITH_SCOPE_TYPE`. Reword `NO_SCOPE_PROVIDED`, or add a
    DELETE-specific constant such as `NO_SCOPE_OR_UNSCOPED`, so that the DELETE's message points
    to `unscoped=true`. The PUT message stays as it is.
  - Flow:
    1. If `Boolean.TRUE.equals(unscoped)`:
       - a scope that is non-null → throw `UNSCOPED_WITH_SCOPE`.
       - a `scopeType` that is non-null → throw `UNSCOPED_WITH_SCOPE_TYPE`.
       - an unknown issuer → `log.debug("Issuer {} has no unscoped credentials to delete, it does not exist", did)`, then `404`.
       - otherwise call `credentialRepository.deleteByTrustedIssuerDidAndScopeIsNull(did)`,
         then `log.info("Issuer {} deleted its unscoped credentials", did)`, then `204`.
    2. Otherwise keep the current behaviour unchanged: `requireScope`, then the 404 check, the
       IsNull/Equals delete, and the existing INFO line.
    Keep the method small. Moving the unscoped branch into a private helper is fine.
  - Extend the method's Javadoc with one sentence on `unscoped`, plus a `@param unscoped` line.
- `src/test/java/org/fiware/iam/rest/TrustedIssuersListControllerTest.java`
  - Add `null` as the new trailing argument at every existing `deleteCredentialsByScope(...)`
    call site, about 14 of them, for example at lines ~624, 638, 647, 702, 725, 773, 910, 932,
    939, 1061 and 1069. Grep for all of them.
  - Fix the `IssuerApiTestSpec` override signatures if the generated spec changed. The spec
    methods take no args, so most likely only the client calls change.
  - New tests. Use parameterized tests (`@ParameterizedTest` + `@MethodSource` returning
    `Stream<Arguments>`) where several cases share one shape, and named constants such as
    `UNSCOPED = true`:
    - `deleteCredentialsByScope204_unscoped`: the issuer has unscoped credentials (created via
      `createTrustedIssuer` without a scope), an untyped grant and a typed grant (via
      `replaceCredentialsByScope`). After `deleteCredentialsByScope(did, null, null, true)`,
      check the following:
      - the response is 204;
      - the issuer still exists (`getIssuer` 200);
      - only the scoped credentials remain (check through `credentialRepository` or the
        repository the existing tests use);
      - `getIssuerScopes` still lists both grants with the same counts.
    - `deleteCredentialsByScope204_unscopedIsIdempotent`: run the call twice. Both calls return
      204, and the issuer and its grants are unchanged after the second call. Also cover an
      issuer that has only scoped grants (nothing to delete → 204).
    - `deleteCredentialsByScope404_unscoped`: an unknown DID → 404.
    - Parameterized `deleteCredentialsByScope400_invalidMarker(scope, scopeType, unscoped)`
      with these cases:
      - `(null, null, null)`: no marker and no scope. This is the acceptance case "a forgotten
        parameter stays an error".
      - `(null, null, false)`;
      - `("", null, false)`;
      - `(ORDER_SCOPE, null, true)`;
      - `(ORDER_SCOPE, DATASPACE_MEMBERSHIP, true)`;
      - `(null, DATASPACE_MEMBERSHIP, true)`;
      - `(null, DATASPACE_MEMBERSHIP, null)`.
      Every case expects `HttpClientResponseException` with status 400, and nothing is deleted:
      the issuer's credentials are unchanged afterwards.
    - Add an `Arguments.of(...)` case to the existing parameterized `getIssuerScopes` scenario
      list (~line 1040-1075): `grantSeveralPairsAndAnUnscopedCredential(client)` followed by an
      unscoped delete. The expected grants are all pairs, unchanged.

Acceptance:
- `mvn clean compile` and `mvn test` pass.
- `mvn spotless:apply` has been run, and `mvn spotless:check` is clean.
- The PUT `/issuer/{did}/credential` still rejects a missing scope with 400.

### Step 2: Logging assertion and documentation
Pin down the new INFO line and document the migration path.

Files:
- `src/test/java/org/fiware/iam/rest/TrustedIssuersListLoggingTest.java`
  - Add a test that follows the pattern of `anEmptyGrantIsLoggedAsRevocation` (~line 78-93):
    create `ISSUER_DID` without a scope, call `appender.list.clear()`, then call
    `testClient.deleteCredentialsByScope(ISSUER_DID, null, null, true)`. Assert that
    `infoLines()` equals exactly
    `List.of("Issuer did:web:consumer.org deleted its unscoped credentials")`. Use the exact
    string the controller produces in Step 1.
  - If the test class already checks WARN lines for 400s, add one assertion for the request
    that has neither a scope nor the marker. Otherwise skip it.
- `README.md`, "Logging" section (~line 74)
  - Extend the INFO bullet with "or the unscoped credentials of an issuer were deleted". Keep the
    wording minimal.
- `README.md`: if there is no endpoint documentation, add at most a short paragraph near the
  API links (~line 107). It says that `DELETE /issuer/{did}/credential?unscoped=true` removes
  only the directly managed credentials (for migrating them into grants), and that a missing
  scope without the marker is rejected.
- `AGENTS.md`
  - In the "Scoped grants" convention, add one sentence: the DELETE addresses `null`-scope
    credentials only through the explicit `unscoped=true` marker (never by omitting `scope`),
    and the marker is exclusive with `scope`/`scopeType`.
  - Add `deleteByTrustedIssuerDidAndScopeIsNull` to the `CredentialRepository` line under
    "Important Files".

Acceptance:
- `mvn test` passes, including `TrustedIssuersListLoggingTest`.
- `mvn spotless:check` is clean.
- The docs mention the marker and that the issuer and its grants are kept.
