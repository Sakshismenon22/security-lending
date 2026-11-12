import { useLocation } from 'react-router-dom'
import { navigation } from '../data/navigation'

export default function PlaceholderPage() {
  const { pathname } = useLocation()
  const item = navigation.find(([, , path]) => path === pathname)
  return <section className="coming"><h1>{item?.[1] ?? 'Page not found'}</h1><p>This LendDesk screen is scheduled next. The Overview is fully implemented; each new screen will be built and reviewed one at a time.</p></section>
}
