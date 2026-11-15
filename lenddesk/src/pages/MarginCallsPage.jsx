import { useDataSource } from '../hooks/useDataSource'
import LifecycleExceptionsPage from './LifecycleExceptionsPage'
import LiveMarginCallsPage from './LiveMarginCallsPage'
export default function MarginCallsPage() {
  const { mode } = useDataSource()
  return mode === 'live' ? <LiveMarginCallsPage /> : <LifecycleExceptionsPage view="margin-calls" />
}
