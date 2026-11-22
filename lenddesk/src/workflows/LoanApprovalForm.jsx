import { useRef, useState } from 'react'
import { lenderPost } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import { loanId, num } from '../utils/loanFormat'

export default function LoanApprovalForm({ loan, onDismiss, onComplete, onBusyChange }) {
  const { organizationId } = useDataSource()
  const submitting = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function approve(event) {
    event.preventDefault()
    if (submitting.current) return
    submitting.current = true
    setBusy(true)
    onBusyChange(true)
    setError('')
    try {
      await lenderPost(`/api/v1/organizations/${organizationId}/loans/${loan.id}/approve`, {})
      onComplete('Loan approved.')
    } catch (failure) {
      setError(failure.message)
    } finally {
      submitting.current = false
      setBusy(false)
      onBusyChange(false)
    }
  }

  return <form className="loan-approval-form" onSubmit={approve}>
    <p>{loanId(loan.id)} · {loan.symbol}</p>
    <dl>
      <div><dt>Borrower</dt><dd>{loan.borrower}</dd></div>
      <div><dt>Requested quantity</dt><dd>{num(loan.quantity)} shares</dd></div>
      <div><dt>Collateral coverage</dt><dd>{loan.coverage == null ? 'Not valued' : `${num(loan.coverage)}%`}</dd></div>
    </dl>
    <p className="loan-approval-note">Approval records the lending decision. Settlement follows separately.</p>
    {error && <p className="al-error" role="alert">{error}</p>}
    <footer>
      <button type="button" disabled={busy} onClick={onDismiss}>Cancel</button>
      <button type="submit" className="loan-approval-confirm" disabled={busy}>{busy ? 'Approving…' : 'Confirm approval'}</button>
    </footer>
  </form>
}
