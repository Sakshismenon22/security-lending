# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Defaults & Buy-ins

Open `/defaults-buyins` from the **Defaults & Buy-ins** sidebar entry. Signed-in
lenders see their organization's real default projections, source recalls/margin
calls, buy-in reports and persisted lifecycle requests. The page refreshes every
10 seconds and on demand. It supports filtering, sorting, paging, case details and
CSV export. Failed loads never fall back to sample records; refresh errors identify
when the page is displaying its last loaded snapshot.

Use **Request buy-in** for an open default. The form queues the existing
`POST /api/v1/organizations/{org}/defaults/{id}/buy-in-requests` contract with a stable
client request ID. Pending/accepted requests cannot be submitted again. Failed or
rejected requests expose their reason and permit a new attempt. Request status is
loaded from the database on reload; report updates are displayed without inferring
cash settlement, collateral liquidation or loan close-out.

The backend owns eligibility, tenant isolation and duplicate-request checks. The
screen uses the new `GET /api/v1/organizations/{org}/lenddesk/defaults-buyins` read
model, which correlates records using owner default IDs, not coincidental local IDs.
Only reported values are displayed; unknown quantities/exposures/deadlines show `—`.
The current command contract does **not** accept exchange-order quantity or limit
price. Manual default creation and notice delivery are not supported in live mode.
The lifecycle service in this repository does not implement a buy-in execution
consumer: a lifecycle/execution owner must process `BUY_IN_REQUESTED` and publish
`BUY_IN_REPORTED` for execution results. Queue acceptance alone is not execution.

The **Sample preview** link opens `/defaults-buyins/preview`. This retains the
frontend-only design with sample cases, draft defaults/notices/buy-ins and execution
examples. Drafts stay in page state and disappear on reload; they never call business
write endpoints. Legacy demo data mode also uses the sample screen.

- Screen: `src/pages/DefaultsBuyinsPage.jsx` and its stylesheet.
- Live API and normalization: `src/api/defaultsBuyinsApi.js`.
- Forms and report cards: `src/components/*Buyin*.jsx`.
- Sample records: `src/data/defaultsBuyinsData.js`.
- Browser coverage: `../lendora/tests/e2e/defaultsBuyins*.spec.js`.
  Run from the repo root: `npm --prefix lendora run test:e2e -- defaultsBuyins`.
- Frontend checks: `npm run lint`, `npm run build`.
