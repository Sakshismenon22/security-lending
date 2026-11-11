import { BrowserRouter, Route, Routes } from 'react-router-dom'
import PortalLayout from '../components/PortalLayout'
import AgreementsPage from '../pages/AgreementsPage'
import BorrowersPage from '../pages/BorrowersPage'
import InventoryPage from '../pages/InventoryPage'
import LocatesPage from '../pages/LocatesPage'
import ReservationsPage from '../pages/ReservationsPage'
import LoansPage from '../pages/LoansPage'
import CollateralPage from '../pages/CollateralPage'
import ReturnsPage from '../pages/ReturnsPage'
import ValuationsPage from '../pages/ValuationsPage'
import OverviewPage from '../pages/OverviewPage'
import PlaceholderPage from '../pages/PlaceholderPage'

export default function AppRoutes() {
  return <BrowserRouter><Routes><Route element={<PortalLayout />}><Route index element={<OverviewPage />} /><Route path="agreements" element={<AgreementsPage />} /><Route path="borrowers" element={<BorrowersPage />} /><Route path="inventory" element={<InventoryPage />} /><Route path="locates" element={<LocatesPage />} /><Route path="reservations" element={<ReservationsPage />} /><Route path="loans" element={<LoansPage />} /><Route path="collateral" element={<CollateralPage />} /><Route path="returns" element={<ReturnsPage />} /><Route path="valuations" element={<ValuationsPage />} /><Route path="*" element={<PlaceholderPage />} /></Route></Routes></BrowserRouter>
}
