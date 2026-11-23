import { Link } from 'react-router-dom'
import { LoanBadge } from '../components/loans/LoanCards'
import { date, display, loanId, money, num } from '../utils/loanFormat'
import './ManageLoanScreen.css'

const percent = (value) => value == null ? '—' : `${num(value)}%`
const rate = (value) => value == null ? '—' : `${num(value)} bps`
const shares = (value) => value == null ? '—' : `${num(value)} shares`

export default function ManageLoanScreen({ data, action, reason, onAction, onReason, onSubmit, notice, error, busy }) {
  const { loan, collateral, returns, activity } = data
  const workspace = `/loans/${loanId(loan.id)}`
  const pending = loan.status === 'PENDING_REVIEW'
  const canSettle = loan.status === 'APPROVED_PENDING_SETTLEMENT'
  const pledges = collateral.filter((lot) => lot.status === 'PLEDGE_PENDING')
  const releases = collateral.filter((lot) => lot.status === 'RELEASE_REQUESTED')
  const pendingReturns = returns.filter((entry) => entry.status === 'PENDING')
  const mainOperations = [
    { key: 'approve', label: 'Approve loan', enabled: pending, review: 'Review approval', detail: 'Approval records the lending decision. Settlement follows separately.', confirm: 'Confirm approval' },
    { key: 'reject', label: 'Reject loan', enabled: pending, review: 'Review rejection', detail: 'The reason will be sent to the borrower.', confirm: 'Confirm rejection', needsReason: true },
    { key: 'settle', label: 'Confirm settlement', enabled: canSettle, review: 'Review settlement', detail: 'Confirm only after verifying delivery of the securities to the borrower.', confirm: 'Confirm settlement' },
  ]
  const requestGroups = [
    { label: 'Collateral confirmations', rows: pledges, actions: [['collateralConfirm', 'Confirm receipt'], ['collateralReject', 'Reject pledge']] },
    { label: 'Collateral releases', rows: releases, actions: [['collateralRelease', 'Accept release'], ['collateralReleaseReject', 'Reject release']] },
    { label: 'Returns', rows: pendingReturns, actions: [['return', 'Confirm receipt'], ['returnReject', 'Reject return']] },
  ]
  const requestOperations = requestGroups.flatMap(({ rows, actions }) =>
    rows.flatMap((entry) => actions.map(([type, label]) => ({
      key: `${type}:${entry.id}`, label, enabled: true,
      review: `Review ${label.toLowerCase()}`,
      detail: `${type.startsWith('return') ? 'Return' : 'Collateral lot'} ${entry.id} · ${entry.assetType === 'CASH' ? money(entry.requestedReleaseCash ?? entry.cashAmount, loan.currency) : shares(entry.requestedReleaseQuantity ?? entry.quantity)}.`,
      confirm: label, needsReason: type.endsWith('Reject'),
    }))),
  )
  const selected = [...mainOperations, ...requestOperations].find((operation) => operation.key === action && operation.enabled)
  const facts = [
    ['Borrower', loan.borrower], ['Security', loan.symbol],
    ['Agreement', loan.mslaId ? <Link to={`/agreements/${loan.mslaId}`}>{loan.agreementNumber}</Link> : null],
    ['Reservation', loan.reservationId], ['Fee', rate(loan.feeBps)], ['Rebate', rate(loan.rebateBps)],
    ['Day count', loan.dayBasis ? `Actual/${loan.dayBasis}` : null], ['Billing', display(loan.billing)],
  ]
  const metrics = [
    ['Requested quantity', shares(loan.quantity)], ['Outstanding quantity', shares(loan.outstandingQuantity)],
    ['Loan market value', money(loan.marketValue, loan.currency)], ['Collateral coverage', percent(loan.coverage)],
    ['Annual lending fee', rate(loan.feeBps)],
  ]
  return (
    <section className="manage-loan wf-page">
      <nav className="wf-breadcrumb" aria-label="Breadcrumb">
        <Link to="/loans">Loans</Link><span>/</span><Link to={workspace}>{loanId(loan.id)}</Link><span>/</span><span aria-current="page">Manage loan</span>
      </nav>
      <header className="manage-loan-header">
        <div>
          <div className="manage-loan-title"><h1>Manage loan</h1><LoanBadge>{display(loan.status)}</LoanBadge></div>
          <p>{loanId(loan.id)} · {loan.symbol}</p>
        </div>
        <Link to={workspace}>← Back to loan workspace</Link>
      </header>
      {notice && <p className="wf-notice" role="status">{notice}</p>}
      <dl className="manage-loan-metrics">
        {metrics.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd>
          {label === 'Collateral coverage' && loan.requiredRatio != null && <small>Required {percent(loan.requiredRatio)}</small>}
        </div>)}
      </dl>
      <div className="manage-loan-grid">
        <article className="manage-loan-card">
          <h2>Loan operations</h2>
          <div className="manage-loan-operations">
            {mainOperations.map((operation) => <button key={operation.key} type="button" aria-pressed={action === operation.key}
              disabled={busy || !operation.enabled} onClick={() => onAction(operation.key)}>
              <span>{operation.label}{operation.key === 'settle' && pending && <small>Available after approval</small>}</span>
              <span aria-hidden="true">›</span>
            </button>)}
          </div>
          <section className="manage-loan-section">
            <h3>Pending requests</h3>
            {requestGroups.map(({ label, rows, actions }) => <details key={label} className="manage-loan-request">
              <summary>{label} <strong>{rows.length}</strong></summary>
              {rows.length ? rows.map((entry) => <div key={entry.id} className="manage-loan-request-row">
                <span>#{entry.id}</span>
                {actions.map(([type, title]) => <button key={type} type="button" disabled={busy}
                  aria-pressed={action === `${type}:${entry.id}`} onClick={() => onAction(`${type}:${entry.id}`)}>{title}</button>)}
              </div>) : <p>No pending requests.</p>}
            </details>)}
          </section>
          {selected && <form className="manage-loan-section manage-loan-review" onSubmit={(event) => { event.preventDefault(); onSubmit() }}>
            <h3>{selected.review}</h3>
            <p>{selected.detail}</p>
            {selected.needsReason && <label>Reason<textarea required maxLength={500} value={reason} disabled={busy} onChange={(event) => onReason(event.target.value)} /></label>}
            {error && <p role="alert" className="al-error">{error}</p>}
            <footer>
              <button type="button" disabled={busy} onClick={() => onAction('')}>Cancel</button>
              <button className="wf-primary" type="submit" disabled={busy || (selected.needsReason && !reason.trim())}>{busy ? 'Submitting…' : selected.confirm}</button>
            </footer>
          </form>}
        </article>
        <aside className="manage-loan-aside">
          <article className="manage-loan-card"><h2>Loan summary</h2>
            <dl className="manage-loan-facts">{facts.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}</dl>
          </article>
          <article className="manage-loan-card"><h2>{pending ? 'Proposed collateral' : 'Collateral'}</h2>
            <div className="manage-loan-table-scroll"><table className="wf-table">
              <thead><tr><th>Lot</th><th>Asset</th><th>Amount / quantity</th><th>Haircut</th><th>Status</th></tr></thead>
              <tbody>{collateral.length ? collateral.map((lot) => <tr key={lot.id}>
                <td>{lot.id}</td><td>{lot.assetType === 'CASH' ? 'Cash' : 'Securities'}</td>
                <td>{lot.assetType === 'CASH' ? money(lot.cashAmount, loan.currency) : shares(lot.quantity)}</td>
                <td>{percent(lot.haircut == null ? null : Number(lot.haircut) * 100)}</td><td>{display(lot.status)}</td>
              </tr>) : <tr><td colSpan={5}>No collateral recorded.</td></tr>}</tbody>
            </table></div>
            <p className="manage-loan-total">Eligible total <strong>{money(loan.eligibleCollateral, loan.currency)}</strong></p>
          </article>
        </aside>
      </div>
      <article className="manage-loan-card">
        <h2>Activity &amp; settlement</h2>
        <div className="manage-loan-table-scroll"><table className="wf-table">
          <thead><tr><th>Time (IST)</th><th>Event</th><th>Reference</th></tr></thead>
          <tbody>{activity.length ? activity.map((entry, index) => <tr key={`${entry.at}:${index}`}>
            <td>{date(entry.at)}</td><td>{entry.event.includes('_') ? display(entry.event) : entry.event}</td><td>{entry.reference ?? '—'}</td>
          </tr>) : <tr><td colSpan={3}>No activity recorded.</td></tr>}</tbody>
        </table></div>
      </article>
    </section>
  )
}
