import './App.css'
import AppRoutes from './routes/AppRoutes'
import { DataSourceProvider } from './context/DataSourceContext'

export default function App({ identity }) {
  return <DataSourceProvider identity={identity}><AppRoutes /></DataSourceProvider>
}
