import { useEffect, useMemo, useState } from 'react'
import { DataSourceContext } from './dataSource'

export function DataSourceProvider({ children, identity }) {
  const [mode, setMode] = useState(() => localStorage.getItem('lenddesk-data-mode') ?? 'demo')
  const [organizationId, setOrganizationId] = useState(() => localStorage.getItem('lenddesk-organization-id') ?? '1')
  const [refreshKey, setRefreshKey] = useState(0)
  useEffect(() => localStorage.setItem('lenddesk-data-mode', mode), [mode])
  useEffect(() => localStorage.setItem('lenddesk-organization-id', organizationId), [organizationId])
  const value = useMemo(() => ({ identity, mode: identity ? 'live' : mode, organizationId: identity ? String(identity.organization.platformOrganizationId) : organizationId, setMode: identity ? () => {} : setMode, setOrganizationId: identity ? () => {} : setOrganizationId, refresh: () => setRefreshKey((current) => current + 1), refreshKey }), [identity, mode, organizationId, refreshKey])
  return <DataSourceContext.Provider value={value}>{children}</DataSourceContext.Provider>
}

