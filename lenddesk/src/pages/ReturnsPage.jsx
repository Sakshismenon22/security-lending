import LifecycleExceptionsPage from './LifecycleExceptionsPage'
import { useDataSource } from '../hooks/useDataSource'
import LiveReturnsPage from './LiveReturnsPage'
export default function ReturnsPage() { const { mode } = useDataSource(); return mode === 'live' ? <LiveReturnsPage /> : <LifecycleExceptionsPage view="returns" /> }
