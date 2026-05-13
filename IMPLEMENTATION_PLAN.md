# Implementation Plan: Implement TIRv5 API

## Overview
Implement the EBSI Trusted Issuers Registry v5 API alongside the existing v4 implementation. The v5 API introduces attributes and revisions as sub-resources, a restructured issuer response (link-based instead of inline attributes), new fields (`issuerType`, `tao`, `rootTao`) on credentials, cursor-based string pagination with max page size 50, and a `?version=deprecated` backward-compatibility mode. The new controller will share the existing database, with backward-compatible schema additions. Required endpoints: List issuers, Get an issuer, List attributes, Get an attribute. Optional: List revisions, Get a revision.

## Steps

### Step 1: Create OpenAPI v5 YAML spec and configure Maven code generation

Create the file `api/trusted-issuers-registry-v5.yaml` with an OpenAPI 3.0.3 spec (matching the existing specs' OpenAPI version for compatibility with the codegen plugin) defining the v5 endpoints.

**Endpoints to define:**
1. `GET /v5/issuers` - List issuers (params: `page[after]` as string, `page[size]` as integer max 50)
2. `GET /v5/issuers/{did}` - Get an issuer (param: `version` query enum `deprecated`/`latest`, default `latest`)
3. `GET /v5/issuers/{did}/attributes` - List attributes (params: `page[after]`, `page[size]`)
4. `GET /v5/issuers/{did}/attributes/{attributeId}` - Get an attribute
5. `GET /v5/issuers/{did}/attributes/{attributeId}/revisions` - List revisions (params: `page[after]`, `page[size]`, `version` query enum)
6. `GET /v5/issuers/{did}/attributes/{attributeId}/revisions/{revisionId}` - Get a revision

**Schemas to define:**
- `IssuersResponse` (reuse same structure as v4: `self`, `items`, `total`, `pageSize`, `links`)
- `IssuerEntry` (`did`, `href`)
- `IssuerLatest` (`did`, `attributes` as string link, `hasAttributes` as boolean)
- `IssuerDeprecated` (`did`, `attributes` as array of `Attribute`)
- `AttributesResponse` (`self`, `items` array of `AttributeEntry`, `total`, `pageSize`, `links`)
- `AttributeEntry` (`id` as SHA256 hash string, `href`)
- `AttributeDetails` (`did`, `attribute` of type `Attribute`)
- `Attribute` (`hash`, `body`, `issuerType` enum, `tao`, `rootTao`)
- `RevisionsResponse` (`self`, `items`, `total`, `pageSize`, `links`)
- `Links` (`first`, `prev`, `next`, `last`)
- `ProblemDetails` (RFC 7807)

**Maven changes in `pom.xml`:**
Add a third OpenAPI Generator execution block `<id>tir-v5</id>` alongside existing `tir` and `til` executions:
- `inputSpec`: `./api/trusted-issuers-registry-v5.yaml`
- `apiPackage`: `org.fiware.iam.tir.v5.api`
- `modelPackage`: `org.fiware.iam.tir.v5.model`

**Files changed:**
- `api/trusted-issuers-registry-v5.yaml` (new)
- `pom.xml` (add execution block)

**Acceptance criteria:**
- `mvn clean generate-sources` succeeds and produces interfaces/models in `org.fiware.iam.tir.v5.api` and `org.fiware.iam.tir.v5.model`
- Generated API interface has methods for all 6 endpoints
- Generated models include all schemas listed above with `VO` suffix
- Generated test client and test spec are produced

### Step 2: Add backward-compatible DB schema changes and update entity classes

Add new nullable columns to the `credential` table for v5 fields that don't exist in the current schema. These fields are used by v5's `Attribute` model but are optional in v4, so existing data remains valid.

**Liquibase migration** (`src/main/resources/db/migration/v0/changelog-v0_0_5.xml`):
- Add `issuer_type` column (`VARCHAR(255)`, nullable, default `'Undefined'`) to `credential` table
- Add `tao` column (`VARCHAR(768)`, nullable) to `credential` table
- Add `root_tao` column (`VARCHAR(768)`, nullable) to `credential` table

**Entity changes** (`src/main/java/org/fiware/iam/repository/Credential.java`):
- Add field `issuerType` (`String`, `@Nullable`, defaults to `"Undefined"`)
- Add field `tao` (`String`, `@Nullable`)
- Add field `rootTao` (`String`, `@Nullable`)

**TIL Mapper impact:** The TIL API (management API) should also support setting these new fields when creating/updating issuers. The `CredentialsVO` in the TIL spec does not currently have these fields. For now, the new Credential entity fields will be populated with defaults when created via the TIL API (backward compatible). A future ticket can add them to the TIL spec if management of v5 fields via TIL is needed.

**Files changed:**
- `src/main/resources/db/migration/v0/changelog-v0_0_5.xml` (new)
- `src/main/java/org/fiware/iam/repository/Credential.java` (modified)

**Acceptance criteria:**
- Liquibase migration runs successfully on H2 (tests) and is backward-compatible
- Existing tests still pass (`mvn test`)
- New fields are nullable, so existing data is not affected
- `Credential` entity has the three new fields with `@Nullable`

### Step 3: Implement TIRv5 mapper and controller for all endpoints

Create the v5 mapper and controller that reuse the existing repositories but produce v5-shaped responses.

**Mapper** (`src/main/java/org/fiware/iam/TIRv5Mapper.java`):
- MapStruct interface `@Mapper(componentModel = "jsr330")`
- Map `TrustedIssuer` -> `IssuerLatestVO` (set `attributes` to link string like `{baseUri}/v5/issuers/{did}/attributes`, set `hasAttributes` based on whether credentials exist)
- Map `TrustedIssuer` -> `IssuerDeprecatedVO` (inline attributes, reuse the Base64/SHA256 encoding pattern from `TIRMapper`)
- Map `Credential` -> `AttributeVO` (Base64-encode body, SHA256 hash, map `issuerType`/`tao`/`rootTao`)
- Map `Credential` -> `AttributeDetailsVO` (wrap attribute with DID)
- Compute attribute ID: SHA256 hex string of the credential body (same hash used in v4 but hex-encoded, not Base64-encoded, per v5 spec)

**Controller** (`src/main/java/org/fiware/iam/rest/TrustedIssuerRegistryV5Controller.java`):
- Implements the generated `TirV5Api` interface (or whatever the generated interface name is)
- Annotated with `@Controller("${general.basepath:/}")`
- Inject `TrustedIssuerRepository`, `CredentialRepository`, `TIRv5Mapper`

**Endpoint implementations:**

1. **`GET /v5/issuers`** (`getIssuersV5`):
   - Accept `page[size]` (default 10, max 50) and `page[after]` (string cursor)
   - Reuse the offset-based pagination from `TrustedIssuerRepository.findAll(Pageable)` (convert cursor to page number, similar to v4 controller)
   - Return `IssuersResponseVO` with `IssuerEntryVO` items, pagination links, total count

2. **`GET /v5/issuers/{did}`** (`getIssuerV5`):
   - Validate DID format (reuse `checkDidFormat` logic)
   - If `version=deprecated`: return `IssuerDeprecatedVO` (inline attributes array, v4-style)
   - If `version=latest` (default): return `IssuerLatestVO` (`attributes` as link string, `hasAttributes` boolean)
   - Return 404 if issuer not found

3. **`GET /v5/issuers/{did}/attributes`** (`getIssuerAttributesV5`):
   - Validate DID, return 404 if issuer not found
   - Paginate over the issuer's credentials
   - Return `AttributesResponseVO` with `AttributeEntryVO` items (id = SHA256 hex hash, href = link to attribute)

4. **`GET /v5/issuers/{did}/attributes/{attributeId}`** (`getIssuerAttributeV5`):
   - Validate DID, find issuer
   - Find credential whose SHA256 hex hash matches `attributeId`
   - Return `AttributeDetailsVO` with the attribute data
   - Return 404 if issuer or attribute not found

5. **`GET /v5/issuers/{did}/attributes/{attributeId}/revisions`** (`getIssuerAttributeRevisionsV5`):
   - Since the current system doesn't track revision history (credentials are replaced, not versioned), this endpoint returns a single-item list containing the current (latest) revision
   - The revision ID can be the credential's database ID or a hash
   - If `version=deprecated`: return inline attribute objects; if `version=latest`: return `{id, href}` entries

6. **`GET /v5/issuers/{did}/attributes/{attributeId}/revisions/{revisionId}`** (`getIssuerAttributeRevisionV5`):
   - Return the specific revision (in this implementation, the current credential if the revisionId matches)
   - Return 404 if not found

**Shared pagination helper:**
- Extract the link-building logic from `TrustedIssuerRegistryController` into a shared utility or base class to avoid duplication. The v5 controller needs the same pattern but with string cursors and different max page sizes.

**Files changed:**
- `src/main/java/org/fiware/iam/TIRv5Mapper.java` (new)
- `src/main/java/org/fiware/iam/rest/TrustedIssuerRegistryV5Controller.java` (new)

**Acceptance criteria:**
- Project compiles with `mvn clean compile`
- All 6 endpoints are implemented and return correct response shapes
- v5 issuer response uses link-based attributes by default, inline with `?version=deprecated`
- Attribute IDs are SHA256 hex-encoded hashes of the Base64-encoded credential body
- Pagination enforces max page size of 50
- `issuerType`, `tao`, `rootTao` fields from Credential entity are mapped to attribute responses
- Existing v4 endpoints and TIL endpoints are unaffected

### Step 4: Write tests for the v5 controller endpoints

Create comprehensive tests following the existing test patterns (implement the generated `TirV5ApiTestSpec`, use auto-generated `TirV5ApiTestClient` and `*VOTestExample` classes).

**Test class** (`src/test/java/org/fiware/iam/rest/TrustedIssuerRegistryV5ControllerTest.java`):
- Annotated with `@MicronautTest`, `@RequiredArgsConstructor`
- Implements the generated test spec interface
- Injects `TirV5ApiTestClient` (generated), `IssuerApiTestClient` (for inserting test data via TIL API), `TrustedIssuerRepository`

**Test cases:**

1. **List issuers (`GET /v5/issuers`):**
   - 200: Returns correct paginated list with proper total, pageSize, items, and links
   - 200: Pagination works correctly (page[after] cursor, page[size])
   - 200: Empty list when no issuers
   - 400: Invalid page size (0, negative, >50)

2. **Get issuer (`GET /v5/issuers/{did}`):**
   - 200 (latest): Returns issuer with `attributes` link and `hasAttributes=true` when credentials exist
   - 200 (latest): Returns issuer with `hasAttributes=false` when no credentials
   - 200 (deprecated): Returns issuer with inline attributes array
   - 400: Invalid DID format (parameterized with various invalid DIDs)
   - 404: Non-existent DID

3. **List attributes (`GET /v5/issuers/{did}/attributes`):**
   - 200: Returns correct attribute entries with SHA256 IDs and hrefs
   - 200: Pagination works correctly
   - 200: Empty list when issuer has no credentials
   - 404: Non-existent DID

4. **Get attribute (`GET /v5/issuers/{did}/attributes/{attributeId}`):**
   - 200: Returns correct attribute details with hash, body, issuerType, tao, rootTao
   - 404: Non-existent attribute ID
   - 404: Non-existent DID

5. **List revisions (`GET /v5/issuers/{did}/attributes/{attributeId}/revisions`):**
   - 200: Returns single revision (current credential)
   - 404: Non-existent attribute or DID

6. **Get revision (`GET /v5/issuers/{did}/attributes/{attributeId}/revisions/{revisionId}`):**
   - 200: Returns correct revision details
   - 404: Non-existent revision, attribute, or DID

**Test patterns to follow:**
- Use `@MethodSource` and `@ValueSource` for parameterized tests
- Use `@BeforeEach` to clean repository
- Use the TIL `IssuerApiTestClient` to insert test data (same pattern as existing v4 tests)
- Use generated `*VOTestExample` builders for constructing test data
- Assert HTTP status codes, response body structure, and field values

**Files changed:**
- `src/test/java/org/fiware/iam/rest/TrustedIssuerRegistryV5ControllerTest.java` (new)

**Acceptance criteria:**
- All tests pass with `mvn test`
- Tests cover all 6 endpoints with success and error cases
- Parameterized tests used for DID validation and page size validation
- Existing v4 and TIL tests still pass
- Tests verify correct attribute ID computation (SHA256 hex hash)
- Tests verify `?version=deprecated` returns inline attributes

### Step 5: Verify full integration and backward compatibility

Run the complete test suite and verify that no regressions were introduced. Ensure the v4 API, TIL API, and new v5 API all work correctly with the shared database.

**Verification steps:**
- Run `mvn clean test` and confirm all tests (v4, TIL, and v5) pass
- Verify Liquibase migration applies cleanly (new columns are backward-compatible)
- Verify that creating an issuer via TIL API makes it visible through both v4 and v5 endpoints
- Verify the v4 `IssuerAttribute` response still works correctly (the `issuerType` field already exists in v4 spec and should now return the stored value instead of always `UNDEFINED`)
- Check for any compilation warnings or code style issues

**Files changed:**
- Potentially minor fixes to any files from Steps 1-4 based on integration issues

**Acceptance criteria:**
- `mvn clean install` succeeds (build + all tests + package)
- No test regressions
- v4 and TIL APIs unaffected by changes
- Shared database works correctly for all three APIs
