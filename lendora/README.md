# Lendora frontend

React + Vite frontend based on the landing, login, and signup references in
`../lendora-ui-design`. Development is contained in the existing `lendora` project.

## Run

```sh
npm ci
cp .env.example .env.local
npm run dev -- --host 0.0.0.0 --port 5176
```

Routes: `/`, `/login`, `/signup` (`/register` redirects here), `/forgot-password`,
`/privacy`, and `/terms`. Unknown routes show a recoverable 404 page. Configure your
production host to serve `index.html` for application routes.

## Structure

The project follows the other frontend applications in the repository:

- `src/api`: account-service HTTP requests.
- `src/components/common`: branding, icons, fields, password controls, and error summaries.
- `src/components/layout`: header, navigation, and footer.
- `src/components/marketing`: workspace preview and connection illustration.
- `src/pages`: landing, login, signup, recovery, policy information, and 404 views.
- `src/routes`: browser routes, titles, scrolling, and focus handling.
- `src/hooks`: cancellable account form submissions.
- `src/data`: clearly marked sample data used by the marketing preview.
- `src/styles`: shared theme tokens and responsive marketing/authentication layouts.
- `src/utils`: form validation.

## Signup, login and workspace access

The `lendora-service` backend now implements signup, login, current-session lookup
and logout **without Spring Security**. See [LENDORA_AUTH.md](../LENDORA_AUTH.md) for
service startup, database/key configuration, endpoints and tests.

There are exactly two types: **LENDER → LendDesk** and **BORROWER → BorrowDesk**.
Signup provisions the organization's business account and first administrator;
login uses the stored type and automatically opens the matching workspace.
Both workspaces verify the session and use its actual organization ID. Passwords
are hashed by the backend; the browser uses an HttpOnly cookie and stores no tokens.

`VITE_AUTH_API_URL` defaults to `/api/v1`. The development server proxies it to
`lendora-service` on port 8086. `VITE_LENDDESK_URL` and `VITE_BORROWDESK_URL` default
to `http://localhost:5175` and `http://localhost:5173` respectively. Current business
services support INR, so signup's base-currency selector is limited to INR.

The API adapter exposes `/organizations/register`, `/auth/login`, `/auth/me`, and
`/auth/logout`. Password recovery remains unavailable until mail/reset delivery is
implemented; the interface never reports that an email was sent when the service
is unavailable. Policy pages remain onboarding information, not finalized agreements.

## Verification

```sh
npm run build
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
```

Browser tests mock the account API and cover desktop/mobile routes,
preview controls, validation, pending/error/success states, and password recovery.
For an installed Chromium binary, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.
Backend behavior is covered by the account service integration tests.
