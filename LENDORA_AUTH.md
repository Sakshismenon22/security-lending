# Lendora signup and login

Implemented on `feature/lendora-frontend`, without Spring Security. There are exactly two
organization types: **LENDER → LendDesk** and **BORROWER → BorrowDesk**. The account service
stores the type at signup; login cannot select or override it.

## Start the services

On Windows, create `lendora_db` in PostgreSQL, stop any old demo backend windows
and the test frontend on port 5176, then run **`start-lendora.cmd`** from the repo
root (equivalent to `start-demo.cmd -Lendora`). This mode starts the existing demo
services plus `lendora-service`, BorrowDesk, LendDesk and Lendora. It enables account
authentication and shares a generated local provisioning key across the three
backends, saved in ignored `runtime/lendora-provisioning.key`. If you already set
`LENDORA_PROVISIONING_KEY`, the launcher uses that value instead. Keep the saved key
private. Database credentials still come from each service's properties/environment.
`start-lendora.cmd -Plan` lists the components without starting them or writing a key.
The original `start-demo.cmd` continues to launch the legacy test console.

For manual startup:

Use a full JDK 17+ and PostgreSQL. Create `lendora_db` in addition to the existing
`lender_db` and `borrower_db` databases. Defaults are postgres/postgres; deployment
settings can be overridden with the variables in `lendora-service/src/main/resources/application.properties`.

Choose one random provisioning key and set **the same value** as
`LENDORA_PROVISIONING_KEY` in the terminals running the three Java services. This
key is for service-to-service provisioning and is never a frontend VITE variable.
For example, generate a value with `openssl rand -hex 32`, then export it in each
terminal. On PowerShell use `$env:LENDORA_PROVISIONING_KEY = '<your generated value>'`.
Missing keys disable workspace registration; no default shared secret is shipped.

Run in separate terminals:

| Project | Command | Default port |
| --- | --- | --- |
| `borrower-service` | `./mvnw spring-boot:run` | 8081 |
| `lender-service` | `./mvnw spring-boot:run` | 8084 |
| `lendora-service` | `./mvnw spring-boot:run` | 8086 |
| `borrowdesk` | `npm ci && npm run dev` | 5173 |
| `lenddesk` | `npm ci && npm run dev` | 5175 |
| `lendora` | `npm ci && npm run dev` | 5176 |

On Windows use `mvnw.cmd`. Start the existing Kafka/FIX/lifecycle/market-data services
as usual when testing lending workflows. Signup and login themselves do not need
Kafka to complete: organizations, initial financial accounts and first admin records
are committed by their owning services, with their existing outbox events queued.

Open **http://localhost:5176**. Use `localhost` consistently across services and
frontends so the host-only session cookie is shared across their local ports.
The test console also defaults to port 5176; stop it or run it on another port.

Borrower migration **V17** adds the idempotent Lendora organization identity.
The new account database uses its own Flyway migration V1. Existing organizations
and directory users are not silently given passwords: create an account through
Lendora. Existing agreements/counterparties are not automatically connected to a
newly registered organization. The existing FIX routing configuration still needs
the newly provisioned borrower organization ID and institution identifiers.

## Endpoints and access

All account endpoints are under `http://localhost:8086/api/v1`:

- `POST /organizations/register`: `{ organization: { name, type, registrationNumber,
  country, baseCurrency: "INR", registeredAddress }, administrator: { fullName,
  email, password }, acceptedTerms: true }`.
- `POST /auth/login`: `{ email, password, rememberMe?: boolean }`. Returns the
  organization, actual business-service organization ID, administrator and workspace.
- `GET /auth/me`: resolves the current cookie session.
- `POST /auth/logout`: revokes the current session and clears the cookie.

The browser receives an HttpOnly `LENDORA_SESSION` cookie. Passwords use salted
PBKDF2-HMAC-SHA256 with 600,000 iterations; only session-token hashes are persisted.
Email is normalized case-insensitively and unique across accounts. Organization
registration is unique by type and registration number. Login rotates the browser's
previous session. Five incorrect attempts temporarily lock an account for 15 minutes.
Sessions expire after 12 hours, or 30 days with Remember me. Password recovery
returns unavailable until mail/recovery is implemented; it never pretends to send mail.

Registration provisions the organization's local account and ADMIN member through
`POST /internal/lendora/provision` on the appropriate business service. These endpoints
require `X-Lendora-Provisioning-Key`, use a stable external identity for retries,
and never accept or store the administrator's password. A provisioning failure
rolls back the central signup and can be retried with the same details.

The workspaces resolve the session before mounting or constructing organization API
URLs. LendDesk forces live mode and the session's organization; the old editable
organization selector is hidden. BorrowDesk binds all API helpers after its assigned
organization is known. Both include session cookies in business requests and provide
logout. Changing local storage, the URL or the frontend cannot grant another role or
tenant: custom MVC interceptors verify the session and organization on business APIs.
Anonymous legacy WebSockets are disabled in authenticated mode; the portals use REST
polling. Internal FIX/lifecycle endpoints keep their existing service contracts.

## Configuration

- `LENDORA_DATABASE_URL`, `LENDORA_DATABASE_USERNAME`, `LENDORA_DATABASE_PASSWORD`.
- `LENDORA_PROVISIONING_KEY`: same private value on all three services.
- `LENDORA_LENDER_SERVICE_URL` / `LENDORA_BORROWER_SERVICE_URL`: provisioning targets.
- `LENDORA_AUTH_URL`: account-service root URL used by lender/borrower interceptors.
- `LENDORA_ALLOWED_ORIGINS`: explicit browser origins for all three services.
- `LENDORA_SECURE_COOKIE=true` when serving HTTPS. Use a trusted same-site deployment
  or reverse proxy for account and workspace origins so cookies are sent consistently.
- Lendora's `VITE_AUTH_API_URL` defaults to `/api/v1`; Vite proxies it to port 8086.
- Workspace `VITE_LENDORA_API_URL` defaults to `http://localhost:8086/api/v1` and
  `VITE_LENDORA_URL` to `http://localhost:5176`.
- `VITE_LENDDESK_URL` / `VITE_BORROWDESK_URL` select workspace destinations.

`LENDORA_AUTH_ENABLED=false` on business services and
`VITE_LENDORA_AUTH_REQUIRED=false` on workspaces are explicit legacy/demo overrides.
Authentication is enabled by default. The old `start-demo.cmd` test-console launcher
uses the backend override; restart those services without it before testing real login.

## Verify

Run `./mvnw test` in `lendora-service`, `lender-service`, and `borrower-service`.
`AccountIntegrationTests` covers signup, duplicates, password checks, lockout,
cookies, rotation, rollback and logout. `LendoraAccessTests` in both business
services covers workspace/tenant isolation and private, idempotent provisioning.

Run Lendora's `npm test` and `npm run test:e2e` plus builds for all three frontends.
The browser tests cover role redirects, pinned organization IDs, expired sessions
and logout. For an installed Chromium binary set
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` before running browser tests.

Verification for this change: all 5 account-service tests, both business services' new
access/provisioning tests, 13 browser tests, frontend validation tests and all three
frontend builds pass. A live smoke test using isolated H2 databases also passed real
HTTP signup, provisioning, login, tenant/role isolation and logout for both types.

The broader workflow suites have two existing collateral-release failures, reproduced
on the unchanged parent commit: lender `partialReleaseRechecksMarketCoverageAndPreservesImmutableCashMovements`
(cash comparison) and borrower `partialCashReleaseIsLimitedToExcessAndCreditsOnlyTheConfirmedAmount`
(expected snake_case response field). The remaining 84 workflow tests passed.

## Create organization shows a gateway error

Lendora's dev server forwards `/api` to `http://127.0.0.1:8086`. A gateway error
means an upstream connection failed; it does not mean signup completed. The legacy
`start-demo.cmd` launcher does **not** start `lendora-service`; use
`start-lendora.cmd` (or `start-demo.cmd -Lendora`) for the complete account flow.

1. Create `lendora_db` in PostgreSQL, configure its credentials, and start
   `lendora-service` with `./mvnw spring-boot:run` (`.\mvnw.cmd spring-boot:run`
   on Windows). Resolve any database/Flyway startup error in that terminal.
2. Open `http://localhost:8086/api/v1/auth/me`. A JSON **401** before login is
   expected and confirms the service is reachable. Connection refused or a gateway
   error indicates it is not reachable at that address.
3. Start `borrower-service` on 8081 and `lender-service` on 8084. Set the same
   `LENDORA_PROVISIONING_KEY` on all three backends before starting them. Missing or
   mismatched keys prevent registration.
4. If the account backend runs on a different host or port, set `LENDORA_PROXY_TARGET`
   in `lendora/.env.local` to that address and restart Vite. For a production build,
   configure the hosting reverse proxy for `/api` or set `VITE_AUTH_API_URL` before
   building; Vite's development proxy is not part of the production bundle.

Keep Lendora on port 5176; the dev server now stops if it is occupied rather than
silently moving to a port that backend CORS does not allow. Stop the legacy test
frontend if it is using 5176. Configuration errors returned as JSON are now displayed
by the form, including missing provisioning keys and unreachable workspace services.
