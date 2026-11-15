import { useEffect, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { lenderGet, lenderPatch, lenderPost } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import { Badge } from '../components/reference/ReferenceTable'
import './ReferencePages.css'

const roles = ['ADMIN', 'LENDING_DESK', 'RISK', 'TREASURY', 'OPERATIONS', 'READ_ONLY']
const label = value => value == null ? '—' : String(value).replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase())
const timestamp = value => value ? new Date(value).toLocaleString('en-GB', { timeZone: 'UTC' }) : '—'

function MemberDialog({ member, onClose, onSaved }) {
  const { organizationId } = useDataSource()
  const [form, setForm] = useState({ name: member?.name ?? '', email: member?.email ?? '', role: member?.role ?? 'OPERATIONS', active: member?.active ?? true })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const update = (key, value) => setForm(current => ({ ...current, [key]: value }))
  async function submit(event) {
    event.preventDefault()
    setSaving(true); setError('')
    try {
      if (member) await lenderPatch(`/api/v1/organizations/${organizationId}/users/${member.id}`, { role: form.role, active: form.active })
      else await lenderPost(`/api/v1/organizations/${organizationId}/users`, { name: form.name.trim(), email: form.email.trim(), role: form.role })
      onSaved(member ? `${member.name} access was updated.` : `${form.name.trim()} was added to the organization.`)
    } catch (requestError) { setError(requestError.message) } finally { setSaving(false) }
  }
  return <div className="agreement-offer-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !saving) onClose() }}><section className="borrower-connect-dialog admin-member-dialog" role="dialog" aria-modal="true" aria-labelledby="member-dialog-title"><header><div><p>ACCESS CONTROL</p><h2 id="member-dialog-title">{member ? 'Update member access' : 'Add organization member'}</h2></div><button className="agreement-offer-close" type="button" onClick={onClose} disabled={saving} aria-label="Close">×</button></header><p>{member ? 'Change the member role or disable their account. The last active administrator cannot be removed.' : 'Create an active LendDesk member. The first member in an organization must be an administrator.'}</p><form onSubmit={submit}>{!member && <><label>Name<input value={form.name} maxLength="160" onChange={event => update('name', event.target.value)} required /></label><label>Email<input type="email" value={form.email} maxLength="254" onChange={event => update('email', event.target.value)} required /></label></>}<label>Role<select value={form.role} onChange={event => update('role', event.target.value)}>{roles.map(role => <option key={role} value={role}>{label(role)}</option>)}</select></label>{member && <label className="admin-member-toggle"><input type="checkbox" checked={form.active} onChange={event => update('active', event.target.checked)} /> Active member</label>}{error && <p className="live-error" role="alert">{error}</p>}<footer><button type="button" onClick={onClose} disabled={saving}>Cancel</button><button className="ref-primary" type="submit" disabled={saving}>{saving ? 'Saving…' : member ? 'Save access' : 'Add member'}</button></footer></form></section></div>
}

export default function LiveAdministrationPage() {
  const { searchTerm = '' } = useOutletContext()
  const { organizationId, refreshKey, refresh } = useDataSource()
  const [tab, setTab] = useState('members')
  const [state, setState] = useState({ scope: '', data: null, error: '' })
  const [dialog, setDialog] = useState(null)
  const [notice, setNotice] = useState('')
  const scope = `${organizationId}:${refreshKey}`
  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      lenderGet(`/api/v1/organizations/${organizationId}`, controller.signal),
      lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/users?page=0&size=200`, controller.signal),
      lenderGet('/api/v1/organizations/roles', controller.signal),
    ]).then(([organization, users, availableRoles]) => {
      if (!controller.signal.aborted) setState({ scope, data: { organization, users: users.content ?? [], roles: availableRoles }, error: '' })
    }).catch(error => { if (!controller.signal.aborted) setState({ scope, data: null, error: error.message }) })
    return () => controller.abort()
  }, [organizationId, refreshKey, scope])
  const loading = state.scope !== scope
  const members = useMemo(() => {
    if (!state.data) return []
    const roleById = new Map(state.data.roles.map(role => [role.id, role.roleName]))
    const query = searchTerm.toLowerCase()
    return state.data.users.map(user => ({ ...user, role: roleById.get(user.roleId) ?? `Role ${user.roleId}` })).filter(user => Object.values(user).join(' ').toLowerCase().includes(query))
  }, [state.data, searchTerm])
  if (loading) return <section className="ref-page"><p className="ref-empty" role="status">Loading administration data…</p></section>
  if (state.error) return <section className="ref-page"><p className="live-error" role="alert">Administration data is unavailable: {state.error}</p></section>
  const { organization, roles: availableRoles } = state.data
  const activeCount = state.data.users.filter(user => user.active).length
  const saveComplete = message => { setDialog(null); setNotice(message); refresh() }
  return <section className="ref-page live-administration-page"><header className="ref-heading"><h1>Administration</h1><span className="ref-active-count">{activeCount} active users</span><button className="ref-primary" type="button" onClick={() => { setNotice(''); setDialog({}) }}>Add user</button></header>{notice && <p className="live-agreement-notice" role="status">{notice}</p>}<nav className="ref-tabs admin-tabs" aria-label="Administration sections">{[['members', `Users (${state.data.users.length})`], ['organization', 'Organization'], ['roles', `Roles (${availableRoles.length})`], ['connectivity', 'Service connectivity']].map(([id, title]) => <button key={id} type="button" aria-selected={tab === id} onClick={() => setTab(id)}>{title}</button>)}</nav>{tab === 'members' && <div className="ref-scroll"><table className="ref-main-table admin-member-table"><thead><tr><th>Member</th><th>Email</th><th>Role</th><th>State</th><th>Created UTC</th><th aria-label="Actions" /></tr></thead><tbody>{members.map(member => <tr key={member.id}><td><b>{member.name}</b></td><td>{member.email}</td><td><Badge tone="neutral">{label(member.role)}</Badge></td><td><Badge tone={member.active ? 'good' : 'risk'}>{member.active ? 'Active' : 'Inactive'}</Badge></td><td>{timestamp(member.createdAt)}</td><td><button className="admin-edit" type="button" onClick={() => { setNotice(''); setDialog(member) }}>Manage</button></td></tr>)}{!members.length && <tr><td className="ref-empty" colSpan="6">No members match the current search.</td></tr>}</tbody></table></div>}{tab === 'organization' && <section className="admin-read-panel"><h2>Organization profile</h2><dl><div><dt>Name</dt><dd>{organization.name}</dd></div><div><dt>Organization ID</dt><dd>{organization.id}</dd></div><div><dt>External ID</dt><dd>{organization.externalId}</dd></div><div><dt>Public reference ID</dt><dd>{organization.publicReferenceId}</dd></div><div><dt>Created UTC</dt><dd>{timestamp(organization.createdAt)}</dd></div></dl></section>}{tab === 'roles' && <section className="admin-read-panel"><h2>Available access roles</h2><div className="ref-scroll"><table className="ref-main-table"><thead><tr><th>Role ID</th><th>Role</th><th>Usage</th></tr></thead><tbody>{availableRoles.map(role => <tr key={role.id}><td>{role.id}</td><td><Badge tone="neutral">{label(role.roleName)}</Badge></td><td>{state.data.users.filter(user => user.roleId === role.id).length} assigned member(s)</td></tr>)}</tbody></table></div></section>}{tab === 'connectivity' && <section className="admin-read-panel"><h2>Backend connectivity</h2><p>This view confirms the LendDesk connection to the lender-service administration API. FIX session traffic remains available in the dedicated FIX Monitor.</p><dl><div><dt>Lender-service API</dt><dd><Badge tone="good">Connected</Badge></dd></div><div><dt>Data scope</dt><dd>Organization {organizationId}</dd></div><div><dt>Live user directory</dt><dd>{state.data.users.length} records received</dd></div><div><dt>FIX operations</dt><dd>Open FIX Monitor for message-level status</dd></div></dl></section>}{dialog && <MemberDialog member={dialog.id ? dialog : null} onClose={() => setDialog(null)} onSaved={saveComplete} />}</section>
}
