import { useEffect, useState } from 'react'
import { lendDeskApi } from '../api/lendDeskApi'
import { useDataSource } from './useDataSource'

export function useLenderData(resource) {
  const { mode, organizationId, refreshKey } = useDataSource()
  const [state, setState] = useState({ data: null, loading: false, error: null })
  useEffect(() => {
    if (mode !== 'live') return undefined
    const controller = new AbortController()
    const request = resource === 'dashboard' ? lendDeskApi.dashboard(organizationId, controller.signal) : lendDeskApi.list(organizationId, resource, controller.signal)
    request.then((data) => setState({ data: data.content ?? data, loading: false, error: null })).catch((error) => { if (error.name !== 'AbortError') setState({ data: null, loading: false, error: error.message }) })
    return () => controller.abort()
  }, [mode, organizationId, refreshKey, resource])
  return mode === 'live' ? state : { data: null, loading: false, error: null }
}
