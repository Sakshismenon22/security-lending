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

## Account-service integration

The repository does **not** currently provide an authentication or combined
organization/admin registration service. The frontend contains an API adapter;
real account creation, credential verification, sessions, password-reset delivery,
workspace authorization, and final legal policies still require backend integration.
The UI never reports account creation or login success until the service confirms it.
The policy routes provide onboarding information, not finalized legal agreements.

`VITE_AUTH_API_URL` defaults to `/api/v1`. The adapter expects the following **new
account-service contract**, which is not an existing borrower/lender-service API:

| POST path | JSON request |
| --- | --- |
| `/auth/login` | `{ email, password, rememberMe }` |
| `/organizations/register` | `{ organization: { name, type, registrationNumber, country, baseCurrency, registeredAddress }, administrator: { fullName, email, password }, acceptedTerms }` |
| `/auth/password-reset` | `{ email }` |

`type` is `LENDER` or `BORROWER`. Successful responses must be JSON with
`{ "success": true, "data": { ... } }`. Login data may contain
`{ organization: { name, type } }` or `{ workspace: "LENDER" | "BORROWER" }`.
Errors may return `{ success: false, message }` or an HTTP error with `detail`.
Requests include cookies; the service should manage sessions securely. The frontend
stores no passwords or session tokens in local storage. Password confirmation is
validated locally and is not sent to the server.

Set `VITE_LENDDESK_URL` and `VITE_BORROWDESK_URL` to the existing workspace URLs.
Only the workspace matching the authenticated response is linked after login.

## Verification

```sh
npm run build
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
```

Browser tests mock the proposed account API and cover desktop/mobile routes,
preview controls, validation, pending/error/success states, and password recovery.
For an installed Chromium binary, set `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.
These mocks do not verify a real authentication backend.
