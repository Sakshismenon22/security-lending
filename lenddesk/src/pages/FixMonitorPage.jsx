import { useDataSource } from '../hooks/useDataSource'
import LiveFixMonitorPage from './LiveFixMonitorPage'
import OperationalRegisterPage from '../components/OperationalRegisterPage'
import { fixMonitorConfig } from '../data/remainingData'
export default function FixMonitorPage() { const { mode } = useDataSource(); return mode === 'live' ? <LiveFixMonitorPage /> : <OperationalRegisterPage config={fixMonitorConfig} /> }
