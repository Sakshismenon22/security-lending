import { useDataSource } from '../hooks/useDataSource'
import LiveAuditNotificationsPage from './LiveAuditNotificationsPage'
import OperationalRegisterPage from '../components/OperationalRegisterPage'
import { auditConfig } from '../data/remainingData'
export default function AuditNotificationsPage() { const { mode } = useDataSource(); return mode === 'live' ? <LiveAuditNotificationsPage /> : <OperationalRegisterPage paginate config={auditConfig} /> }
