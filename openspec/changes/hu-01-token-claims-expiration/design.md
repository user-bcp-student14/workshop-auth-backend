# Design

## Context

See [proposal.md](proposal.md) for motivation and scope, and [specs/auth-token/spec.md](specs/auth-token/spec.md) for the observable requirements.

In the current branch, `LoginRequest` already carries `client`, but `AuthResource` validates only the request, username and password. It passes username/password to `AuthService`; after authentication, `AuthService` asks `TokenService` to issue a token without client context. `TokenService` owns RS256 signing, reads the external private key, captures an injected `Clock`, and derives `exp` from `iat` plus `LIFETIME_SECONDS`. `LoginResponse` currently owns the documented response envelope and must not change shape.

The existing tests provide useful characterization points: `AuthResourceTest` and `EndpointContractTest` cover response and validation behavior, `EndpointFailureTest` covers generic 500 responses, and `TokenServiceTest` uses a fixed clock and independently verifies RS256 signatures and expiry. The existing endpoint tests also demonstrate that client omission/null/empty are currently accepted; HU-01 intentionally changes that behavior.

## Goals / Non-Goals

**Goals:**

- Carry a validated client identifier from the HTTP request to the issued token without changing the successful response envelope.
- Keep claim creation and signing in the existing token boundary, preserve RS256 and existing claims, and derive expiry from one issuance instant.
- Preserve existing credential validation order, 401 behavior, generic 500 response and log redaction.
- Make the behavior change testable with isolated keys and a deterministic clock.

**Non-Goals:**

- Change persistence, password verification, CORS, key provisioning, issuer, audience, role semantics or API response DTOs.
- Add a closed client allowlist, token verification endpoint, refresh/logout/revocation flow, or external consumer implementation.
- Replace the JWT library, signature algorithm, or application architecture.

## Protected Contracts

- Keep `POST /auth/login`, the HTTP 200 success status, the four top-level response properties (`token`, `expiresIn`, `user`, `debug`) and the three public `user` properties unchanged.
- Keep RS256 and existing `sub`, `role`, `iss`, `aud`, `iat` and `exp` claims. Add only the required `client` claim.
- Keep the 3600-second lifetime and the existing 400/401/500 response behavior, except for the new HTTP 400 rejection of missing or blank client.
- Do not expose passwords, issued tokens, configured secrets or private key material in logs.

## Components Affected

- `AuthResource`: validate `client` at the request boundary after existing request, username and password checks; preserve validation precedence for those existing fields.
- `AuthService`: accept the validated client as login context and pass it to token issuance only after successful authentication.
- `TokenService`: include the client claim while retaining ownership of signing, RS256, configured issuer/audience and lifetime.
- `LoginRequest`: already exposes `client`; no request model redesign is needed.
- `LoginResponse`: preserve its current fields and mapping; `client` belongs in the signed token, not as a new response property.
- Tests: extend the existing resource, endpoint-contract, token-service and logging test coverage rather than introducing parallel test infrastructure.

## Decisions

### Validate client at the API boundary and keep authentication unchanged

Reject an absent, null, empty or whitespace-only client in `AuthResource`, using the existing 400 validation envelope and the existing validation order for request, username and password. Treat any nonblank string as valid; do not trim, normalize or allowlist it because the Issue and HU define no such policy. Pass that validated value through `AuthService` to token issuance. This keeps HTTP validation with the resource and avoids coupling credential lookup or password verification to client metadata.

**Alternative considered:** validate inside `AuthService`. That would mix request-shape policy into the authentication service and make the HTTP error contract less direct. A closed allowlist was also considered but rejected because no accepted client set is specified.

### Keep token construction in TokenService and preserve the current JWT profile

Add the client to the existing token creation flow; do not move signing into the resource or response mapper. Continue using the configured RS256 private key, issuer and audience, and preserve all existing claims. The `client` claim is the only new token field. No dependency or second token format is needed.

**Alternative considered:** return to HMAC or replace the JWT mechanism. Both would change a documented and currently tested RS256 contract, expanding this change beyond HU-01.

### Use one issuance instant as the lifetime reference

Capture the issuance time once, use it for `iat`, derive `exp` as `iat + LIFETIME_SECONDS`, and keep `expiresIn` tied to that same 3600-second lifetime. Do not read the clock independently for claim creation and response duration. The fixed-clock test should assert exact `exp - iat` and the spec's one-second comparison tolerance at emission.

**Alternative considered:** calculate `exp` and `expiresIn` from separate clock reads. That creates avoidable drift and flaky boundary tests.

### Preserve redacted error logging and prohibit sensitive success logging

Keep the current generic client error and the exception logger's policy of recording type/stack without arbitrary exception messages. Do not add token, password, secret or private-key values to success or failure logs. Verify the configured logging backend in tests so assertions inspect emitted records rather than only the public HTTP body.

**Alternative considered:** log full exception messages or request/response bodies for diagnostics. These may carry credentials or token material and conflict with the security requirement.

## Incremental Strategy

1. Before changing behavior, add or confirm characterization tests for the current successful response envelope and `user` fields, existing validation precedence, 401 and generic 500 bodies, RS256 plus existing claims, and the current fixed 3600-second lifetime. Keep a characterization assertion that omitted client currently succeeds so the intended compatibility break is explicit in the test change.
2. Add token-service tests with a fixed `Clock` and isolated RSA key pair for the `client` claim, required claims, unchanged claims/signature, lifetime arithmetic and expiry boundary.
3. Change the endpoint contract tests to require a nonblank client and cover omitted, null, empty and whitespace-only values returning 400 without a token. Keep tests for the previous request/response contract and validation precedence.
4. Add an end-to-end successful login assertion that the submitted client is present in the decoded token while the response envelope remains unchanged.
5. Capture success and failure log output with sentinel password, token and key values; assert none are emitted. Retain the existing generic error and storage-failure coverage.

No persistence migration is expected. Keep the change deployable as an API compatibility change: callers must send `client` before rollout, and consumers must not require the new claim until all issuers they use have been upgraded.

## Risks / Trade-offs

- **Existing callers omit `client` → login starts returning 400.** Mitigate by identifying callers and updating their requests before rollout; the behavior change is explicit in the proposal/spec.
- **Consumers start requiring `client` before every issuer emits it → tokens from older instances fail.** Mitigate by allowing the claim to be absent during rollout and enabling strict consumer dependence only after issuer rollout completes.
- **A timing test compares against wall-clock time → flaky results at second boundaries.** Mitigate by using the existing injected fixed clock and testing expiry at `exp - 1`, `exp`, and `exp + 1`.
- **Logging assertions inspect only response data → sensitive log leakage goes unnoticed.** Mitigate by capturing records from the configured logger backend with unique sentinels for successful and failed flows.
- **No client allowlist is defined → any nonblank client is accepted.** This follows the current issue scope; add an allowlist only with a separately agreed contract.

## Rollback

The change has no database or persisted-token migration. Roll back by deploying the previous application artifact. That restores acceptance of requests without `client` but also stops issuing the claim. During rollback, consumers must continue tolerating tokens without `client`; do not roll back the issuer while consumers already require that claim. Previously issued RS256 tokens remain subject to their original signature, issuer, audience and expiration checks.

## Open Questions

None that block this design. The spec's nonblank-client policy and one-second lifetime tolerance are treated as approved constraints; a client allowlist remains explicitly out of scope.