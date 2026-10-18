import { Outlet, useLocation } from 'react-router'
import logo from '../assets/logo.png'

/** Same frame for every screen (background + logo); only the content in the middle changes. */
export default function Layout() {
  const isHome = useLocation().pathname === '/'

  return (
    <div className="layout">
      <header className={isHome ? 'layout__header layout__header--home' : 'layout__header'}>
        <img className="layout__logo" src={logo} alt="Buzz.Runner" />
      </header>
      <main className="layout__content">
        <Outlet />
      </main>
    </div>
  )
}
