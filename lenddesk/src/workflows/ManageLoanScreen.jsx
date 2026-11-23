import { useState } from 'react'
import { Link } from 'react-router-dom'
import { LoanBadge } from '../components/loans/LoanCards'
import RunValuationButton from '../components/loans/RunValuationButton'
import { useDataSource } from '../hooks/useDataSource'
import { date, display, loanId, money, num } from '../utils/loanFormat'
import LoanWorkflowDialog from './LoanWorkflowDialog'
import './ManageLoanScreen.css'

const percent = (value) => value == null ? '—' : `${num(value)}%`
const rate = (value) => value == null ? '—' : `${num(value)} bps`
const shares = (value) => value == null ? '—' : `${num(value)} shares`

function Facts({ rows, compact = false }) {
  return <dl className={compact ? 'manage-loan-facts compact' : 'manage-loan-facts'}>
    {rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}
  </dl>
}

export default function ManageLoanScreen({ data, action, reason, onAction, onReason, onSubmit, onComplete, notice, error, busy }) {
  const { loan, collateral, returns, recalls, activity } = data
  const { refresh } = useDataSource()
  const [filter, setFilter] = useState('all')
  const [workflow, setWorkflow] = useState(null)
  const id = loanId(loan.id)
  const pending = loan.status === 'PENDING_REVIEW'
  const amount = (entry, release = false) => entry.assetType === 'CASH'
    ? money((release ? entry.requestedReleaseCash : null) ?? entry.cashAmount, loan.currency)
    : shares((release ? entry.requestedReleaseQuantity : null) ?? entry.quantity)
  const operations = [
    { key: 'approve', label: 'Approve loan', enabled: pending, detail: 'Approval records the lending decision. Settlement follows separately.', confirm: 'Confirm approval' },
    { key: 'reject', label: 'Reject loan', enabled: pending, detail: 'The reason will be sent to the borrower.', confirm: 'Confirm rejection', needsReason: true },
    { key: 'settle', label: 'Confirm settlement', enabled: loan.status === 'APPROVED_PENDING_SETTLEMENT', detail: 'Confirm only after verifying delivery of the securities to the borrower.', confirm: 'Confirm settlement' },
  ].filter((operation) => operation.enabled)
  const requestGroups = [
    { category: 'returns', rows: returns.filter((entry) => entry.status === 'PENDING'), type: 'Return receipt', confirm: 'Confirm receipt', acceptKey: 'return', reject: 'Reject return', rejectKey: 'returnReject', detail: 'Confirm only after the securities have been received.' },
    { category: 'collateral', rows: collateral.filter((entry) => entry.status === 'PLEDGE_PENDING'), type: 'Collateral receipt', confirm: 'Confirm receipt', acceptKey: 'collateralConfirm', reject: 'Reject pledge', rejectKey: 'collateralReject', detail: 'Confirm only after verifying the custody receipt.' },
    { category: 'collateral', rows: collateral.filter((entry) => entry.status === 'RELEASE_REQUESTED'), type: 'Collateral release', confirm: 'Accept release', acceptKey: 'collateralRelease', reject: 'Reject release', rejectKey: 'collateralReleaseReject', detail: 'Acceptance checks current collateral coverage.' },
  ]
  const requests = requestGroups.flatMap(({ rows, ...group }) => rows.map((entry) => ({
    ...group, entry, value: amount(entry, group.acceptKey === 'collateralRelease'),
    acceptAction: `${group.acceptKey}:${entry.id}`, rejectAction: `${group.rejectKey}:${entry.id}`,
  })))
  const request = requests.find((row) => action === row.acceptAction || action === row.rejectAction)
  const rejecting = request?.rejectAction === action
  const operation = operations.find(({ key }) => key === action)
  const needsReason = rejecting || operation?.needsReason
  const filters = [['all', 'All'], ['returns', 'Returns'], ['collateral', 'Collateral']]
  const agreement = loan.mslaId ? <Link to={`/agreements/${loan.mslaId}`}>{loan.agreementNumber ?? loan.mslaId}</Link> : null
  const facts = [
    ['Security', loan.symbol], ['Borrower', loan.borrower], ['ISIN', loan.isin], ['Agreement', agreement],
    ['Requested quantity', shares(loan.quantity)], ['Returned quantity', shares(loan.returnedQuantity)],
    ['Security price', money(loan.price, loan.currency)], ['Price time (IST)', date(loan.priceAt)],
    ['Created (IST)', date(loan.createdAt)], ['Maturity (IST)', date(loan.maturityAt)],
    ['Settled (IST)', date(loan.settledAt)], ['Settlement reference', loan.settlementReference],
    ['Reservation', loan.reservationId], ['Rebate', rate(loan.rebateBps)],
    ['Day count', loan.dayBasis ? `Actual/${loan.dayBasis}` : null], ['Billing', display(loan.billing)],
    ['Required collateral', money(loan.requiredCollateral, loan.currency)], ['Valuation time (IST)', date(loan.valuationAt)],
    ['Open recalls', loan.openRecallCount], ['Open margin calls', loan.openMarginCount],
  ]
  const metrics = [
    ['Outstanding', shares(loan.outstandingQuantity)],
    ['On-loan value', money(loan.marketValue, loan.currency)],
    ['Eligible collateral', money(loan.eligibleCollateral, loan.currency)],
    ['Coverage', percent(loan.coverage)],
    ['Annual lending fee', rate(loan.feeBps)],
  ]
  const returnQuantity = request?.entry.quantity
  const projectedOutstanding = request?.category === 'returns' && returnQuantity != null
    && loan.outstandingQuantity != null && Number(returnQuantity) <= Number(loan.outstandingQuantity)
    ? shares(Number(loan.outstandingQuantity) - Number(returnQuantity)) : null

  const visibleRequests = requests.filter((row) => filter === 'all' || row.category === filter)
  const review = (request || operation) && (
      <form className="manage-loan-review" onSubmit={(event) => { event.preventDefault(); onSubmit() }}>
        <h3>{request ? (rejecting ? request.reject : `Review ${request.type.toLowerCase()}`) : operation.label}</h3>
        {request && <Facts rows={[
          ['Reference', `#${request.entry.id}`],
          ['Quantity / amount', request.value],
          ...(request.category === 'returns' ? [
            ['Current outstanding', shares(loan.outstandingQuantity)],
            ...(!rejecting && projectedOutstanding != null ? [['Outstanding after settlement', projectedOutstanding]] : []),
          ] : []),
        ]} />}
        <p className="manage-loan-guidance">{rejecting ? 'The reason will be sent to the borrower.' : (request ?? operation).detail}</p>
        {needsReason && <label className="wf-field">Reason<textarea required maxLength={500} value={reason} disabled={busy} onChange={(event) => onReason(event.target.value)} /></label>}
        {error && <p role="alert" className="al-error">{error}</p>}
        <footer className="manage-loan-buttons">
          <button type="button" disabled={busy} onClick={() => onAction(rejecting ? request.acceptAction : '')}>{rejecting ? 'Back' : 'Cancel'}</button>
          {request && !rejecting && <button type="button" disabled={busy} onClick={() => onAction(request.rejectAction)}>{request.reject}</button>}
          <button className="wf-primary" type="submit" disabled={busy || (needsReason && !reason.trim())}>
            {busy ? 'Submitting…' : rejecting ? 'Confirm rejection' : (request ?? operation).confirm}
          </button>
        </footer>
      </form>
  )

  return (
    <section className="wf-page manage-loan">
      <nav className="wf-breadcrumb" aria-label="Breadcrumb">
        <Link to="/loans">Loans</Link><span>/</span><span>{id}</span><span>/</span><span aria-current="page">Manage loan</span>
      </nav>
      <header className="manage-loan-header">
        <div>
          <h1>Manage loan</h1>
          <p>{id} · {loan.symbol} · {loan.borrower} <LoanBadge>{display(loan.status)}</LoanBadge></p>
        </div>
        <Link className="manage-loan-link" to="/loans">← Back to loans</Link>
      </header>
      {notice && <p className="wf-notice" role="status">{notice}</p>}
      <dl className="manage-loan-metrics">
        {metrics.map(([label, value]) => <div key={label}>
          <dt>{label}</dt>
          <dd className={label === 'Coverage' ? `manage-loan-coverage ${loan.coverageStatus?.toLowerCase() ?? ''}` : undefined}>{value}</dd>
          {label === 'Coverage' && loan.requiredRatio != null && <small>Required {percent(loan.requiredRatio)}</small>}
        </div>)}
      </dl>
      <div className="manage-loan-grid">
        <div className="manage-loan-summary">
          <section className="manage-loan-card">
            <h2>Loan terms &amp; settlement</h2>
            <Facts rows={facts} compact />
          </section>
          <section className="manage-loan-card">
            <h2>{pending ? 'Proposed collateral' : 'Collateral lots'} ({collateral.length})</h2>
            <div className="manage-loan-table-scroll"><table className="wf-table">
              <thead><tr><th>Lot</th><th>Asset</th><th>Amount / quantity</th><th>Haircut</th><th>Status</th></tr></thead>
              <tbody>{collateral.length ? collateral.map((lot) => <tr key={lot.id}>
                <td>{lot.id}</td><td>{lot.assetType === 'CASH' ? 'Cash' : 'Securities'}</td><td>{amount(lot)}</td>
                <td>{percent(lot.haircut == null ? null : Number(lot.haircut) * 100)}</td><td>{display(lot.status)}</td>
              </tr>) : <tr><td colSpan={5}>No collateral recorded.</td></tr>}</tbody>
              <tfoot><tr className="wf-total"><th scope="row" colSpan={3}>Eligible total</th><td colSpan={2}>{money(loan.eligibleCollateral, loan.currency)}</td></tr></tfoot>
            </table></div>
          </section>
          {(returns.length > 0 || recalls?.length > 0) && <section className="manage-loan-card">
            <h2>Returns &amp; recalls</h2>
            <div className="manage-loan-table-scroll"><table className="wf-table">
              <thead><tr><th>Reference</th><th>Quantity</th><th>Status</th></tr></thead>
              <tbody>
                {returns.map((entry) => <tr key={`return:${entry.id}`}>
                  <td>Return #{entry.id}</td><td>{shares(entry.quantity)}</td><td>{display(entry.status)}</td>
                </tr>)}
                {recalls?.map((entry) => <tr key={`recall:${entry.id}`}>
                  <td>Recall #{entry.id}</td><td>{shares(entry.quantity - entry.fulfilledQuantity)} remaining</td><td>{display(entry.status)}</td>
                </tr>)}
              </tbody>
            </table></div>
          </section>}
          <section className="manage-loan-card">
            <h2>Activity &amp; settlement</h2>
            <div className="manage-loan-table-scroll"><table className="wf-table">
              <thead><tr><th>Time (IST)</th><th>Event</th><th>Reference</th></tr></thead>
              <tbody>{activity.length ? activity.map((entry, index) => <tr key={`${entry.at}:${index}`}>
                <td>{date(entry.at)}</td><td>{entry.event.includes('_') ? display(entry.event) : entry.event}</td><td>{entry.reference ?? '—'}</td>
              </tr>) : <tr><td colSpan={3}>No activity recorded.</td></tr>}</tbody>
            </table></div>
          </section>
        </div>
        <div className="manage-loan-actions">
          <section className="manage-loan-card">
            <h2>Loan operations</h2>
            <div className="manage-loan-buttons">
              {operations.map((item) => <button key={item.key} type="button" disabled={busy}
                aria-pressed={action === item.key} onClick={() => onAction(item.key)}>{item.label}</button>)}
              {loan.status === 'ACTIVE' && <>
                <button className="wf-primary" type="button" disabled={busy || Boolean(loan.openMarginCount) || loan.pendingMarginRequest}
                  onClick={() => setWorkflow('margin')}>Issue margin call</button>
                <button type="button" disabled={busy || !loan.canRecall} onClick={() => setWorkflow('recall')}>Create recall</button>
              </>}
              {agreement && <Link className="manage-loan-link" to={`/agreements/${loan.mslaId}`}>View MSLA</Link>}
              {loan.canRunValuation && <RunValuationButton loanIds={[loan.id]} pendingRunId={loan.pendingValuationRunId}
                lastFailure={loan.valuationFailureReason} onFinished={refresh} />}
              {!operations.length && loan.status !== 'ACTIVE' && <p>No loan operations available.</p>}
            </div>
            {operation && review}
          </section>
          <section className="manage-loan-card">
            <h2>Pending requests</h2>
            <div className="manage-loan-buttons manage-loan-filters" role="group" aria-label="Filter pending requests">
              {filters.map(([key, label]) => <button key={key} type="button" aria-pressed={filter === key} disabled={busy} onClick={() => {
                setFilter(key)
                if (request && key !== 'all' && key !== request.category) onAction('')
              }}>{label} ({requests.filter((row) => key === 'all' || row.category === key).length})</button>)}
            </div>
            <div className="manage-loan-table-scroll"><table className="wf-table">
              <thead><tr><th>Request</th><th>Type</th><th>Quantity / amount</th><th>Status</th></tr></thead>
              <tbody>
                {visibleRequests.map((row) => (
                  <tr key={row.acceptAction} className={row === request ? 'selected' : undefined}>
                    <td><button type="button" className="manage-loan-request-link" disabled={busy} aria-pressed={row === request}
                      aria-label={`Review ${row.type.toLowerCase()} ${row.entry.id}`}
                      onClick={() => onAction(row.acceptAction)}>#{row.entry.id}</button></td>
                    <td>{row.type}</td><td>{row.value}</td><td><span className="wf-tag warn">Pending</span></td>
                  </tr>
                ))}
                {!visibleRequests.length && <tr><td colSpan={4}>No pending requests.</td></tr>}
              </tbody>
            </table></div>
            {request && review}
          </section>
        </div>
      </div>
      {workflow && <LoanWorkflowDialog kind={workflow} id={id} onDismiss={() => setWorkflow(null)} onComplete={onComplete} />}
    </section>
  )
}
