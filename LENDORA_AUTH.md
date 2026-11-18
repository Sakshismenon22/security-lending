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
newly registered organization. Lendora-mode FIX adapters resolve provisioned accounts through the private transport
directory. Manually started adapters retain their static routing unless account
routing is enabled as described below.

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

## PostgreSQL: “sorry, too many clients already”

PostgreSQL limits connections across the whole server, including all databases.
Lendora shares that limit with the six existing service processes (including both
FIX adapters), duplicate runs, pgAdmin and other applications. This error means no
connection slot is available; creating another database does not increase the limit.

1. Stop all running demo backend processes, including copies started manually or
   from your IDE. Closing a browser tab does not stop its Java backend.
2. Close unused pgAdmin/query sessions. If connections remain exhausted after the
   applications are stopped, restart the local PostgreSQL service, then start only
   one copy of the demo. Restarting PostgreSQL disconnects every client on that server.
3. Run `start-lendora.cmd`. Restart **all** services to apply the smaller pool defaults;
   restarting only Lendora leaves the older pools consuming their original budget.

Each service process now allows at most **5** pooled connections and keeps only
**1** idle connection. The normal seven-process setup therefore has a combined
pool limit of **35**, plus migration/startup connections, database tools and other
applications. PostgreSQL sessions are labeled with service name and port for diagnosis.
For a particularly small local server, set `DB_POOL_MAX_SIZE=3` and
`DB_POOL_MIN_IDLE=1` in the launcher terminal before starting the services. These
settings are per process. Production pool sizes should match workload and the total
connection budget.

If you can connect in pgAdmin's Query Tool, inspect usage:

```sql
SHOW max_connections;
SELECT datname, application_name, state, count(*) AS connections
FROM pg_stat_activity
WHERE backend_type = 'client backend'
GROUP BY datname, application_name, state
ORDER BY connections DESC;
```

Use these results to locate duplicate or idle applications. Increasing PostgreSQL's
`max_connections` is an optional server tuning change that uses more resources and
requires a restart; first remove duplicates and apply the pool limits above.

## Legacy API test console cannot load organizations

`security-lending-test-demo` is an anonymous local tool that exercises both business
services using arbitrary test organization/user IDs. It does not log in to Lendora,
and its selectors cannot grant access. With normal account authentication enabled,
its requests receive 401/403; a Lendora account can access only its own workspace and
organization. The console explicitly omits session cookies and shows an actionable
mode-mismatch error rather than silently using a platform session.

Stop borrower/lender backend processes and use `start-demo.cmd` **without** `-Lendora`
for the local test-console stack. It starts both backends with authentication disabled
for that explicit test mode. `start-lendora.cmd` enables authentication and starts the
account-facing portals. The legacy launcher now rejects an authenticated backend
already occupying 8081/8084. Both frontends default to 5176, and the launcher checks
that the expected frontend actually owns the port. For simultaneous use, configure
separate backend instances/ports and the test console's `DEMO_*_URL` targets.

## Connecting registered borrowers from LendDesk

**Borrowers → Add borrower** and the live `/borrowers/new` route list only provisioned
BORROWER accounts registered in Lendora. Accounts already connected to the signed-in
lender, including inactive connections, are excluded. A selection creates the existing
business-service counterparty connection and queues the lender-reference event.

- `GET /api/v1/organizations/{org}/borrower-directory` returns available entries.
- `POST /api/v1/organizations/{org}/borrower-directory/connections` accepts
  `{directoryId}`. The server resolves the real name/business borrower ID, verifies
  availability, serializes changes per lender and coalesces duplicate submissions.
- Lendora's `/internal/lendora/borrowers` and `/{directoryId}` endpoints require the
  private shared provisioning key. Only minimal borrower organization fields are
  exposed; no account users, credentials or session data are returned.

Restart **lendora-service**, **lender-service**, and LendDesk after pulling this
change. No database migration is needed. The existing shared key must be configured
on both services. Existing FIX institution configuration and organization allowlists
still apply; adding a connection does not itself configure a FIX session or approve
an agreement. `LENDORA_BORROWER_INSTITUTION_ID` on lender-service defaults to the
existing shared `BORROWER_DEMO` adapter identity.

## MSLA offers between registered accounts

Lendora account mode now enables `LENDORA_ROUTING_ENABLED=true` for both FIX adapters
through `start-lendora.cmd`. The adapters privately verify provisioned account IDs
and logical `LENDORA_…` lender identities against the account service, then use the
existing configured FIX sessions. Static session allowlists still apply in legacy
mode; unknown accounts and identities on another institution remain rejected.

After this update, stop **both FIX adapter processes**, borrower-service,
lender-service and lendora-service, then run `start-lendora.cmd`. Existing adapters
must restart to receive the shared provisioning key and routing mode. For manual
startup set `LENDORA_ROUTING_ENABLED=true` and the same `LENDORA_PROVISIONING_KEY`
on the FIX adapters as on the account/business services. Physical FIX sessions still
need to be logged on; this does not bypass session configuration or receipts.

LendDesk's offer form now lets you choose cash-only, security-only or mixed accepted
collateral. Add one rule per accepted security, choose it from imported references,
and enter its haircut in **percent** (15 means 15%, sent as 0.15). Cash uses a fixed
0% haircut. Duplicate rules and empty rule sets are rejected. Security master IDs
must exist in both business services, as required by the existing contract.

BorrowDesk refreshes the register every five seconds and includes `OFFERED` in its
Proposed tab. For an unanswered offer lost before this fix, use **Retry delivery**
in LendDesk's MSLA register after restarting the adapters. This requeues the lender
reference and unchanged offer, preserving the agreement ID and offer version, so
BorrowDesk does not create a duplicate agreement if it already received it. A
pending business outbox offer is coalesced. If a failed transport message is blocking
a FIX session, retry or close that message through Failed deliveries first.

## Admin deposit review and securities receipts

BorrowDesk **Funding & account → Deposit requests** lets active workspace admins
page through deposit requests and accept a pending deposit after confirming
receipt. Acceptance credits available cash through the existing deposit workflow,
refreshes the funding balance/history, and cannot credit a confirmed request twice.

LendDesk **Inventory → Add securities** lets active admins select a registered
security, enter a positive quantity and provide a custody receipt reference.
`POST /api/v1/organizations/{org}/inventory/receipts` creates a position or adds to
an existing position. It preserves loaned/reserved quantities and records inventory
history/audit events. Replaying the same reference/security/quantity recognizes the
already recorded receipt without adding stock again. Reusing a reference for
another security or quantity is rejected.

Both frontends load `GET /api/v1/organizations/{org}/admin-access`. Permissions are
resolved from the **verified session email and the business workspace's active
member/role**, not the client-supplied role or Lendora account user ID. Lendora and
business-service user IDs are independent. In authenticated mode the deposit
approval endpoint derives its approver from this identity and ignores a supplied
approver ID. The account identity's generic role label is not used to authorize
these actions.

Backend checks also protect inventory creation/adjustment/receipt endpoints and
member-role mutations; non-admins cannot grant themselves admin access. The
controls remain hidden for non-admin, inactive and unverified workspace identities.
Legacy trusted demo mode retains its existing API behaviour when authentication
is explicitly disabled; admin controls in these frontends require a verified
Lendora session. No Spring Security or new service is introduced.

Restart borrower-service and lender-service, then the two frontends. No database
migration is needed. Existing organization administrators provisioned by Lendora
are recognized through their registered email addresses.
