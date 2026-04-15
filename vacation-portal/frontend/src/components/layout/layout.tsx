import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth-context'
import { CalendarDays, ClipboardList, LogOut, Plus } from 'lucide-react'

export default function Layout() {
  const { user, isManager, logout } = useAuth()
  const navigate = useNavigate()

  const navItems = [
    { to: '/my-requests', icon: ClipboardList, label: 'Мои заявки' },
  ]
  if (isManager) navItems.push({ to: '/all-requests', icon: CalendarDays, label: 'Все заявки' })

  return (
    <div className="min-h-screen flex">
      <aside className="w-64 bg-white border-r border-border flex flex-col">
        <div className="px-6 py-5 border-b border-border">
          <Link to="/" className="text-lg font-semibold text-navy">
            Портал <span className="text-purple">отпусков</span>
          </Link>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors ${
                  isActive ? 'bg-purple text-white' : 'text-navy hover:bg-surface'
                }`
              }
            >
              <Icon size={16} />
              {label}
            </NavLink>
          ))}
          <button
            onClick={() => navigate('/new-request')}
            className="w-full flex items-center gap-3 px-3 py-2 rounded-md text-sm text-navy hover:bg-surface transition-colors"
          >
            <Plus size={16} />
            Новая заявка
          </button>
        </nav>
        <div className="p-3 border-t border-border">
          <div className="px-3 py-2 text-xs text-body">
            <div className="text-navy font-medium truncate">{user?.email || user?.sub}</div>
            <div className="capitalize">{user?.role || 'user'}</div>
          </div>
          <button
            onClick={logout}
            className="w-full flex items-center gap-3 px-3 py-2 rounded-md text-sm text-ruby hover:bg-surface transition-colors"
          >
            <LogOut size={16} />
            Выход
          </button>
        </div>
      </aside>
      <main className="flex-1 p-8 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
