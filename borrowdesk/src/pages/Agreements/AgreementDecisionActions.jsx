import { useRef, useState } from 'react';
import { CheckCircle2, X } from 'lucide-react';
import { lendingApi } from '../../api/lendingApi';

export default function AgreementDecisionActions({ agreement, onDecision }) {
  const [decision, setDecision] = useState('');
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const submitting = useRef(false);
  const offered = agreement?.status === 'OFFERED';
  const close = () => { if (!submitting.current) { setDecision(''); setError(''); } };

  async function submit(event) {
    event.preventDefault();
    if (submitting.current || !offered) return;
    submitting.current = true;
    setBusy(true);
    setError('');
    try {
      const result = await lendingApi.decideAgreement(agreement.id, decision, decision === 'reject' ? { reason: reason.trim() } : {});
      onDecision({ ...agreement, ...result });
      setDecision('');
      setNotice(`${decision === 'accept' ? 'Acceptance' : 'Rejection'} submitted. Awaiting lender confirmation.`);
    } catch (failure) {
      setError(failure.message || 'Unable to submit the agreement decision.');
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }

  return <>
    {offered && <div className="ag-decision-actions">
      <button type="button" className="ag-decision-accept" disabled={busy} onClick={() => { setDecision('accept'); setError(''); }}><CheckCircle2 size={13} />Accept offer</button>
      <button type="button" className="ag-decision-reject" disabled={busy} onClick={() => { setDecision('reject'); setError(''); setReason(''); }}><X size={13} />Reject offer</button>
    </div>}
    {notice && <div className="ag-decision-notice" role="status">{notice}</div>}
    {decision && <div className="ag-backdrop" onClick={close}>
      <form className="ag-modal ag-decision-modal" role="dialog" aria-modal="true" aria-labelledby="ag-decision-title" onSubmit={submit} onClick={event => event.stopPropagation()}>
        <header><div><h2 id="ag-decision-title">{decision === 'accept' ? 'Accept' : 'Reject'} MSLA offer</h2><p>{agreement.number || `MSLA ${agreement.id}`}</p></div><button type="button" onClick={close} disabled={busy} aria-label="Close decision dialog"><X size={14} /></button></header>
        <p className="ag-decision-explanation">{decision === 'accept' ? 'Accept the lender’s terms, including the eligible collateral and haircuts. The agreement becomes active after lender confirmation.' : 'Decline these terms. The rejection will be sent to the lender.'}</p>
        {decision === 'reject' && <label className="ag-decision-reason">Rejection reason (optional)<textarea value={reason} onChange={event => setReason(event.target.value)} maxLength={500} disabled={busy} rows={3} /></label>}
        {error && <p className="ag-error" role="alert">{error}</p>}
        <footer><button type="button" onClick={close} disabled={busy}>Cancel</button><button type="submit" className="primary" disabled={busy || !offered}>{busy ? 'Submitting…' : decision === 'accept' ? 'Confirm acceptance' : 'Confirm rejection'}</button></footer>
      </form>
    </div>}
  </>;
}
