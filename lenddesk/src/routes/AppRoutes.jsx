import { BrowserRouter, Route, Routes } from 'react-router-dom'
import PortalLayout from '../components/PortalLayout'
import AgreementsPage from '../pages/AgreementsPage'
import OverviewPage from '../pages/OverviewPage'
import PlaceholderPage from '../pages/PlaceholderPage'

export default function AppRoutes() {
  return <BrowserRouter><Routes><Route element={<PortalLayout />}><Route index element={<OverviewPage />} /><Route path="agreements" element={<AgreementsPage />} /><Route path="*" element={<PlaceholderPage />} /></Route></Routes></BrowserRouter>
}
