# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Defaults & Buy-ins preview

Open `/defaults-buyins` from the **Defaults & Buy-ins** sidebar entry. The page
follows the existing LendDesk purple theme and contains sample default cases,
linked obligations and notices, buy-in execution, recovery and an activity trail.
Filter/sort cases, switch between Defaults / Buy-ins / Resolved, page through the
register, and export the filtered rows as CSV.

**This page is frontend-only, including when the rest of LendDesk uses live data.**
Record default, prepare notice and manage buy-in actions save drafts in component
state. They do not call business endpoints, execute orders, send notices, change
existing fills or close loans. Reloading or leaving the page discards edits.
Quantity validation caps a buy-in draft at the outstanding obligation minus
existing fills. Resolved cases cannot create buy-ins or notices.

- Screen: `src/pages/DefaultsBuyinsPage.jsx` and its stylesheet.
- Forms: `src/components/DefaultsBuyinsDialog.jsx`.
- Sample records: `src/data/defaultsBuyinsData.js`.
- Browser coverage: `../lendora/tests/e2e/defaultsBuyins.spec.js`, using the existing
  three-frontend Playwright setup. Run from the repository root:
  `npm --prefix lendora run test:e2e -- defaultsBuyins`.
- Frontend checks: `npm run lint`, `npm run build`.
