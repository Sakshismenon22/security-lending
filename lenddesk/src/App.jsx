import './App.css'
import AppRoutes from './routes/AppRoutes'
import { DataSourceProvider } from './context/DataSourceContext'

export default function App() {
  return <DataSourceProvider><AppRoutes /></DataSourceProvider>
}
