# Apex Securities Lending — Lender Operations Portal

## Product character

This product is a desktop-first institutional securities-lending operations workstation for a lender, custodian, or mutual fund house. It is used by operations teams, securities-finance traders, collateral managers, risk analysts, and settlement teams.

The interface should feel precise, calm, dense, and trustworthy. It should resemble an institutional workflow tool such as Bloomberg AIM, Calypso, Murex, Broadridge, ION, or FlexTrade—not a retail investing app, crypto dashboard, portfolio tracker, or consumer SaaS product.

The visual idea is **“an operations control room built around relational data.”** The user should be able to open a borrower, agreement, loan, recall, fee, or collateral position and inspect all related records without losing operational context.

## Primary design principle

Data grids are the product.

The dominant components are compact sortable tables, master-detail layouts, inline-expanded child tables, tabbed record inspectors, filter strips, and audit timelines. Avoid card-first layouts. A small KPI strip may exist at the top of the overview, but no large metric cards, oversized charts, or decorative empty space.

## Layout and density

- Target desktop widths from 1440px to 1680px.
- Use the entire viewport; avoid unused whitespace.
- Use a fixed left sidebar, compact top utility bar, persistent context/filter bar, and a flexible data workspace.
- Standard grid row height: 32–36px.
- Nested/child grid row height: 28–32px.
- Sticky grid headers, pinned identifier columns, horizontal scrolling, visible row counts, pagination, saved views, column chooser, and CSV export are expected.
- Main content uses 12px–16px spacing. Do not use oversized padding.
- A selected parent row stays visible while users inspect child data.
- Prefer right-side detail drawers and inline row expansion over full-page navigation.
- Use split panes for operational queues: list on the left or top, selected-record inspector on the right or bottom.

## Relationship model

Every important record must make its hierarchy clear.

```text
Borrower
  └─ MSLA Agreement
       ├─ Eligible Collateral Rules
       ├─ Locate Requests
       │    └─ Reservations
       └─ Loans
            ├─ Collateral Positions
            ├─ Daily Valuations
            ├─ Returns
            │    └─ Recall reference, when applicable
            ├─ Margin Calls
            ├─ Recalls
            ├─ Lending Fees and Fee Payments
            ├─ Rebate Accruals and Rebate Payments
            ├─ Default / Buy-in records
            ├─ Settlement Ledger Entries
            └─ Lifecycle / FIX / Audit history
```

Use master-child tables to expose this hierarchy. For example, expanding an MSLA agreement row displays active loans, recent locates/reservations, eligible collateral rules, and billing terms in nested tables. Expanding a loan displays collateral, returns, recalls, valuations, fees, rebates, and lifecycle events.

## Navigation

The left navigation should contain only practical operational destinations:

```text
Overview
Agreements
Borrowers
Inventory
Locates
Reservations
Loans
Collateral
Returns
Valuations
Margin Calls
Recalls
Fees & Rebates
Defaults & Buy-ins
Ledger
FIX Monitor
Audit & Notifications
Administration
```

The top bar contains an organization selector, UTC clock, global search, saved-view selector, refresh, export, notifications, and user menu.

The context bar below it always shows the active organization and, where applicable, borrower, MSLA, security, and date range.

## Visual language

### Palette

| Token | Color | Use |
|---|---:|---|
| Application chrome | `#071B2D` | Header and main application shell |
| Sidebar | `#081A2B` | Left navigation |
| Main background | `#F4F7FA` | Workspace background |
| Grid surface | `#FFFFFF` | Tables and inspectors |
| Primary action | `#2857C5` | Selected rows, primary actions, links |
| Teal accent | `#0A8F8F` | Neutral-positive operational emphasis |
| Success | `#16794A` | Settled, paid, confirmed |
| Warning | `#C98500` | Due, pending, expiry approaching |
| Risk | `#B42318` | Rejected, overdue, default, buy-in |
| Border | `#CBD5E1` | Fine table borders and dividers |
| Muted text | `#64748B` | Secondary metadata |

Use restrained color. Status color should supplement a text label, never replace it.

### Typography

- Use IBM Plex Sans, Inter, or another sober enterprise sans-serif.
- Use tabular numerals for quantities, prices, percentages, timestamps, rates, and currency.
- Grid headers: 11px–12px, uppercase or small caps, restrained tracking.
- Grid body: 12px–13px.
- Page titles: 18px–22px. Avoid large marketing headings.
- Use a monospace font only for FIX messages, IDs, hashes, and technical payload inspectors.

### Surfaces and controls

- White data surfaces on a cool light-gray workspace.
- Fine borders, almost no shadow.
- Small 4px–6px corner radius; avoid pill-shaped panels.
- Buttons are compact and rectangular: primary cobalt, secondary white with border, destructive red outline.
- Status chips are compact, square-ish, and text-first.
- Use dividers between operational sections instead of large spacing.

## Grid specification

All major grids should include these baseline behaviours:

- global search plus column filters
- sortable headers
- pinned first identifier column
- multi-select rows where batch actions are realistic
- compact contextual action menu at far right
- row expansion chevron at far left when child data exists
- UTC timestamps and audit correlation IDs
- subtle alternating row hover only; no heavy zebra striping
- footer with result count, totals where appropriate, and pagination

Numeric columns are right-aligned. IDs, statuses, symbols, and names are left-aligned. Dates are center or right aligned. Use negative/shortfall exposure values carefully with warning or risk treatment.

## Key operational screens

### Overview

Use a thin KPI strip, then two dense queues:

- Operational Exceptions: priority, type, borrower, MSLA, loan, security, amount/quantity, due time, owner, action.
- Today’s Settlement Queue: return settlements, collateral releases, fee payments, rebate payments, and confirmations.

Below these, show a compact lifecycle event timeline and recent FIX traffic.

### Agreements

The MSLA grid is a master table. Columns include agreement ID, borrower, status, effective date, expiry date, lending-fee rate, billing frequency, rebate frequency, collateral ratio, haircut, recall permissions, active-loan count, and exposure.

Expanded agreement rows show nested tables for collateral rules, active loans, recent locates/reservations, and billing configuration. A detail drawer uses tabs: Terms, Collateral Rules, Loans, Billing, Audit.

### Loans

Loans are the central workstation. The grid includes loan ID, borrower, MSLA, security, quantity, outstanding quantity, market price, market value, lending fee rate, accrued fee, rebate accrued, collateral value, collateral ratio, margin status, recall status, and settlement status.

Expanding a loan opens a dense tabbed child area:

```text
Collateral | Returns | Fees | Rebates | Valuations | Margin Calls | Recalls | Lifecycle | FIX Messages
```

Every tab contains a table, not cards.

### Inventory / Locates / Reservations

Show security availability as a grid. Expanded security rows reveal locates, reservations, on-loan positions, inventory movements, and a compact valuation chart.

Locate rows expand to a reservation table. Reservation expiry and available quantity are prominent.

### Returns / Recalls / Margin Calls

Use a three-tab exception workstation. Visually connect recall-linked returns using a shared recall ID column and hierarchy indicator. Show whether fee payment, collateral release, and rebate payment are pending, submitted, confirmed, or settled.

### Fees and Rebates

Use two stacked or split grids. Lending-fee records show daily accruals, due amount, payment request, settlement status, and ledger reference. Rebate records show cash collateral basis, annual rate, daily accrual, settlement frequency, payment amount, and payment status.

### FIX Monitor

This is a first-class operational tool, not a hidden technical page.

The grid columns are:

```text
UTC time | direction | sequence | sender Comp ID | target Comp ID |
business message | business FIX type | transport type | event ID |
delivery state | acknowledgement state
```

Selecting a row opens a right-side inspector containing:

1. a pipe-delimited raw FIX message in a dark monospaced panel;
2. a tag / field name / value / meaning grid;
3. business mapping information;
4. decoded JSON including `fixMsg`;
5. acknowledgement and retry history.

The raw display should resemble:

```ini
8=FIX.4.4|9=...|35=AE|49=BORROWER_DEMO|56=LENDER_DEMO|34=...|52=...|11=...|55=...|38=...|60=...|10=...|
```

## Status language

Use explicit labels such as:

```text
ACTIVE, PENDING, APPROVED, REJECTED, RESERVED, EXPIRED,
SETTLED, CLOSED, OPEN, OVERDUE, DUE, PAYMENT_PENDING,
SETTLEMENT_SUBMITTED, PAID, DEFAULTED, BUY_IN_PENDING
```

Show state progression through compact timeline rows or sequence indicators, not oversized stepper graphics.

## Data realism

Use realistic institutional sample data:

- securities: AAPL, MSFT, NVDA, AMZN, TSLA
- identifiers: ISIN, CUSIP, internal security ID
- currencies: USD and INR if needed
- quantities with thousands separators
- rates to four decimal places where relevant
- cash collateral and non-cash equity collateral
- haircuts, collateral ratios, market values, daily accrual values
- UTC timestamps
- linked IDs such as MSLA-00124, LOAN-000006, RECALL-000001, FEE-2000000000003

## Strict exclusions

Do not create:

- retail brokerage widgets
- consumer portfolio summaries
- crypto visuals
- large hero banners
- floating gradient blobs
- oversized rounded cards
- decorative illustrations
- social activity feeds
- generic “AI assistant” chat panels
- mobile-first layouts
- large unused whitespace
- dashboard layouts made mainly from cards

The final result must look like a dense, disciplined institutional securities-lending control system where tables, linked records, exception queues, and settlement evidence are always central.
