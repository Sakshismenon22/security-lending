# Lending lab — backend workflow tester

A deliberately simple React frontend for the existing borrower, lender, FIX adapter and market-data services. It sends real HTTP requests, exposes editable request bodies, and displays the actual HTTP status and raw response. Nothing is automatically approved, funded, settled or paid.

## Run

```powershell
cd D:\Project\SecurityLending\security-lending-test-demo
npm install
npm run dev
```

Open http://127.0.0.1:5176. This port does not conflict with the Kafka portal or borrower frontend. `npm run build` creates the bundle; `npm run preview` serves it with the same development proxy on port 4176. A static-only web host needs equivalent `/bridge/*` proxy routes.

Default proxy targets:

| Service              | Port |
| -------------------- | ---- |
| Market data          | 8080 |
| Borrower             | 8081 |
| Borrower FIX adapter | 8082 |
| Lender FIX adapter   | 8083 |
| Lender               | 8084 |

Copy `.env.example` to `.env.local` to change targets, then restart Vite. Requests use a same-origin local Vite proxy, so backend CORS changes are unnecessary. The proxy strips the browser Origin before forwarding. This is a development tool bound to loopback, not a production deployment.

## What is available

- Separate borrower/lender organization registration, user registration, role/status changes, and selection of registered organizations/users.
- MSLA proposal/approval/rejection, locate availability, separate reservation approval/rejection/release.
- Loan request with cash/security collateral, collateral receipt confirmation, loan approval/rejection, securities settlement, partial/full return requests and return settlements.
- Indicative haircut-adjusted collateral previews, authoritative valuation reads, additional collateral, margin-call responses, collateral release requests and confirmations.
- Recall requests and borrower return responses, fee payment requests with explicit idempotency keys, rebate-payment requests, accrual/payment/ledger views, CSV statements.
- Paper buy/sell orders, cancel orders, positions, holdings, watchlists, OHLC history reads.
- Inventory, security reference import/price refresh, dashboards, notifications, audit, default/buy-in projections and buy-in requests.
- Both FIX adapters' status, inbox/outbox/publication views and retry operations.
- Paginated reads, per-request JSON inspection, in-memory request history/export, a read-only smoke suite and a manually verified end-to-end checklist.

No financial write is sent without checking its write checkbox and clicking Send request. The smoke suite performs GET requests only. Changing platforms keeps organization IDs separate. Use the local record ID returned by the selected backend; borrower and lender resource IDs can differ. Rates are decimal fractions (3% = 0.03), collateral ratios are multipliers (105% = 1.05), and security collateral must reference the actual security ID.

## Current backend limits

**Authentication is not implemented.** There are no login/password/token endpoints or role authorization checks. The disabled Login control explains this; selecting a test identity only changes local UI context. No password is collected and no authentication result is fabricated. Only context IDs are saved in local storage. Responses/history stay in memory until exported or the page is refreshed.

**The lifecycle/payment owner is absent from this workspace.** EOD ratio checks, margin-call creation, daily fee/rebate accrual, reservation/recall deadlines, defaults, buy-in execution and payment settlement cannot complete through this frontend alone. Deposit requests remain pending until their external owner confirms funding, which blocks cash pledging and trading if the account has no confirmed funds. Recall/rebate/buy-in requests are delegated to that owner. The frontend can send the implemented requests and inspect owner-fed projections, but cannot create authoritative owner reports. There is no hidden Kafka event injector or mock business state in the app.

Collateral previews are indicative initial/review checks, not authoritative EOD risk checks. Settlement buttons record an externally confirmed transfer; they do not execute a custody or bank transfer. Use clearly designated DEMO references in a development environment. An HTTP 200 may mean REQUESTED, PROPOSED, or PENDING; inspect both services after confirmations arrive.

## Kafka topics and startup order

The currently checked-in Spring services do **not** declare NewTopic/TopicBuilder beans. The portable broker intentionally has `auto.create.topics.enable=false`, so create topics first: open the Kafka UI and click **Set up lending topics**. This creates the 16 borrower/lender/lifecycle/market and `.DLT` topics used by the project, preserving existing topics. If creating them manually, use the names in `../kafka-ui-portal/kafka-ui-portal/server/config.js`; every dead-letter topic needs at least as many partitions as its source topic because the handlers retain the source partition. The preset uses one partition/replica for this single-broker demo.

1. Start Kafka through its portal and create lending topics.
2. Start PostgreSQL and the market-data service using their existing configuration.
3. Start borrower on 8081 and lender on 8084 using `.\mvnw.cmd spring-boot:run` in each service folder. Kafka is enabled at localhost:9092 and database credentials are postgres/postgres in application.properties; no environment variables are needed.
4. Run TWO adapter instances from fix-adapter-service: `.\run-lender.cmd` first, then `.\run-borrower.cmd` in another terminal. They select the proper properties profiles and separate databases. Their default HTTP ports are 8082/8083 and the FIX session uses port 9876. Verify institution IDs and borrower organization allowlists match your registered organizations.
5. Start this frontend. Click Check connections, then use Guided workflow. Register organizations/admins, connect borrower on lender, import security references, and add inventory. After each asynchronous action, use the corresponding read action on both sides to verify its state.
6. Connect the separate lifecycle/payment service when implemented; its workflows will then populate these existing views.

The market-data service currently supplies HTTP prices/history; importing security references does not itself publish market Kafka events. Kafka topics do not implement a missing lifecycle service.

## Tests

```powershell
npm test
npm run lint
npm run build
npx playwright install chromium
npm run test:e2e
```

Contract tests cover scoped request paths, pagination, financial defaults, unique request keys, response envelopes, and HTTP error/idempotency handling. Browser tests use **controlled HTTP responses**, checking frontend registration, context isolation, MSLA/locate/loan/payment request wiring, errors and mobile layout. They are not a claim of live cross-service workflow completion. Screenshots appear in `test-results`.

