import { useDataSource } from '../hooks/useDataSource'
import LifecycleExceptionsPage from './LifecycleExceptionsPage'
import LiveRecallsPage from './LiveRecallsPage'
export default function RecallsPage() {
  const { mode } = useDataSource()
  return mode === 'live' ? <LiveRecallsPage /> : <LifecycleExceptionsPage view="recalls" />
}
