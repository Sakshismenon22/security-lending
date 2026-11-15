import { useDataSource } from '../hooks/useDataSource'
import LedgerDemoPage from './LedgerDemoPage'
import LiveLedgerPage from './LiveLedgerPage'
export default function LedgerPage() {
 const {mode}=useDataSource()
 return mode==='live'?<LiveLedgerPage/>:<LedgerDemoPage/>
}
