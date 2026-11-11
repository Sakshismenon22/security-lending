import { Fragment, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { agreements } from '../data/agreementData'

const toneFor = (status) => ({ Active: 'good', 'Under review': 'warn', Suspended: 'risk' }[status] ?? 'neutral')

export default function AgreementsPage() {
  const { searchTerm } = useOutletContext()
  const [status, setStatus] = useState('All statuses')
  const [selectedId, setSelectedId] = useState('MSLA-00124')
  const [expandedId, setExpandedId] = useState('MSLA-00124')
  const filteredAgreements = useMemo(() => agreements.filter((agreement) => (status === 'All statuses' || agreement.status === status) && Object.values(agreement).join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [status, searchTerm])
  const selectedAgreement = agreements.find((agreement) => agreement.id === selectedId) ?? agreements[0]

  const selectAgreement = (id) => {
    setSelectedId(id)
    setExpandedId((current) => current === id ? null : id)
  }

  return <>
    <section className="heading agreement-heading"><h1>Master Securities Loan Agreements</h1><div><span>Active agreements <b>48</b></span><span>Active counterparties <b>31</b></span><span>On-loan value <b>$1.84B</b></span></div><button className="primary-action" type="button">+ New agreement</button></section>
    <section className="agreement-filters panel"><div><label>Counterparty<input placeholder="Search counterparty, MSLA or LEI" /></label><label>Jurisdiction<select><option>All jurisdictions</option><option>New York</option><option>English law</option></select></label><label>Status<select value={status} onChange={(event) => setStatus(event.target.value)}><option>All statuses</option><option>Active</option><option>Under review</option><option>Suspended</option></select></label></div><div><button className="link" type="button" onClick={() => setStatus('All statuses')}>Clear filters</button><button className="outline-action" type="button">Export register</button></div></section>
    <section className="agreement-workspace">
      <article className="panel agreement-register"><div className="panel-title"><h2>Agreement register <small>{filteredAgreements.length} shown</small></h2><small>Expand a record to view its active loans</small></div><div className="scroll"><table><thead><tr><th /><th>MSLA ID</th><th>Counterparty</th><th>LEI identifier</th><th>Framework</th><th>Jurisdiction</th><th>Loans</th><th>On-loan value</th><th>Coverage</th><th>Status</th><th>Updated</th></tr></thead><tbody>{filteredAgreements.map((agreement) => <Fragment key={agreement.id}>
        <tr key={agreement.id} className={selectedId === agreement.id ? 'selected' : ''} onClick={() => selectAgreement(agreement.id)}>
          <td><button className="row-expander" type="button" aria-label={`${expandedId === agreement.id ? 'Collapse' : 'Expand'} ${agreement.id}`}>{expandedId === agreement.id ? '⌄' : '›'}</button></td><td><a>{agreement.id}</a></td><td><strong>{agreement.borrower}</strong></td><td className="mono">{agreement.lei}</td><td>{agreement.type}</td><td>{agreement.jurisdiction}</td><td className="num">{agreement.loans}</td><td className="num">{agreement.value}</td><td className="coverage">{agreement.coverage}</td><td><StatusBadge tone={toneFor(agreement.status)}>{agreement.status}</StatusBadge></td><td>{agreement.updated}</td>
        </tr>
        {expandedId === agreement.id && <tr className="child-row" key={`${agreement.id}-loans`}><td colSpan="11"><div className="child-table"><div><b>Active loans under {agreement.id}</b><span>{agreement.loanRows.length} positions shown</span></div>{agreement.loanRows.length ? <table><thead><tr><th>Loan ID</th><th>Security</th><th>Quantity</th><th>On-loan value</th><th>Collateral ratio</th><th>Status</th></tr></thead><tbody>{agreement.loanRows.map((loan) => <tr key={loan[0]}><td><a>{loan[0]}</a></td><td>{loan[1]}</td><td className="num">{loan[2]}</td><td className="num">{loan[3]}</td><td className="coverage">{loan[4]}</td><td><StatusBadge tone="good">{loan[5]}</StatusBadge></td></tr>)}</tbody></table> : <p>No active loans are linked to this agreement.</p>}</div></td></tr>}
      </Fragment>)}</tbody></table></div><footer>Showing {filteredAgreements.length} of 48 agreements · Click a row to select it and expose its linked loans</footer></article>
      <aside className="panel agreement-inspector"><div className="inspector-header"><div><small>SELECTED AGREEMENT</small><h2>{selectedAgreement.id}</h2><p>{selectedAgreement.borrower}</p></div><StatusBadge tone={toneFor(selectedAgreement.status)}>{selectedAgreement.status}</StatusBadge></div><div className="inspector-tabs"><button className="active" type="button">Terms</button><button type="button">Collateral</button><button type="button">Loans ({selectedAgreement.loans})</button><button type="button">Audit</button></div><div className="inspector-content"><h3>Commercial terms</h3><dl><div><dt>Lending fee</dt><dd>{selectedAgreement.terms.fee}</dd></div><div><dt>Cash rebate</dt><dd>{selectedAgreement.terms.rebate}</dd></div><div><dt>Fee settlement</dt><dd>{selectedAgreement.terms.settlement}</dd></div><div><dt>Eligible collateral</dt><dd>{selectedAgreement.terms.collateral}</dd></div><div><dt>Minimum collateralization</dt><dd>{selectedAgreement.terms.margin}</dd></div><div><dt>Legal jurisdiction</dt><dd>{selectedAgreement.jurisdiction}</dd></div></dl><div className="agreement-note"><b>Agreement controls</b><p>Recall and margin-call rights are enabled. Collateral substitution requires lender approval.</p></div><h3>Authorized signatories</h3><div className="signatory"><span>JM</span><div><b>James Morgan</b><small>Head of securities finance</small></div><em>Signed</em></div><div className="signatory"><span>AR</span><div><b>Anita Rao</b><small>Operations director</small></div><em>Signed</em></div></div><footer><button className="outline-action" type="button">Download MSLA</button><button className="primary-action" type="button">Amend agreement</button></footer></aside>
    </section>
  </>
}
