# BorrowDesk UI

React + Vite borrower operations workspace.

## Run locally

```bash
npm install
cp .env.example .env
npm run dev
```

Set `VITE_API_BASE_URL` (default `http://localhost:8081`) and `VITE_ORG_ID` (default `1`). The borrower service must allow the frontend origin through its existing CORS configuration.

## Project structure

- `src/pages/` — one page module per BorrowDesk workspace screen.
- `src/components/common/` — reusable tables, detail panels, badges, page headers, search and action dialogs.
- `src/components/layout/` — application shell, sidebar, topbar and service status footer.
- `src/api/` — endpoint modules grouped by backend domain; `apiClient.js` centralizes base URL, organization scope, response envelope handling and HTTP requests.
- `src/routes/AppRoutes.jsx` — React Router DOM route definitions.
- `src/hooks/` — reusable React hooks.
- `src/utils/` — formatting and utility functions.
- `src/styles/` — global styles and shared design variables.

## Routes

`/blotter`, `/trading`, `/locates`, `/reservations`, `/loans`, `/collateral`, `/margin-calls`, `/funding`, `/fees-rebates`, `/returns-recalls`, `/agreements`, `/reports-audit`.

## Backend integration

The UI calls borrower-service REST endpoints only. Lender decisions, FIX execution and lifecycle confirmations are not simulated. Empty API responses remain empty states; missing backend fields are not replaced with synthetic values.


## Purple theme

The shared visual layer is `src/styles/purpleTheme.css`, loaded after the base and page styles. It applies the reference designs across all twelve screens while preserving the existing workflows and semantic status colors. Core colors remain in `src/styles/variables.css`.

Design references: [borrowdesk-ui-designs on main](https://github.com/code-with-niranjan-v/security-lending/tree/main/borrowdesk-ui-designs). The generated designs use sample data; unsupported controls shown in those images are not added by the styling layer.
