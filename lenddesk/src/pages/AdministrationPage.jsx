import OperationalRegisterPage from '../components/OperationalRegisterPage'
import { administrationConfig } from '../data/remainingData'
import { useDataSource } from '../hooks/useDataSource'
import LiveAdministrationPage from './LiveAdministrationPage'

export default function AdministrationPage() {
  const { mode } = useDataSource()
  return mode === 'live' ? <LiveAdministrationPage /> : <OperationalRegisterPage config={administrationConfig} />
}
