import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/lib/auth-context'
import { LayoutDashboard, Users, Shield, Key, Monitor, ShieldCheck, LogOut } from 'lucide-react'

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Панель' },
  { to: '/users', icon: Users, label: 'Пользователи' },
  { to: '/policies', icon: Shield, label: 'Политики' },
  { to: '/clients', icon: Key, label: 'Клиенты' },
  { to: '/sessions', icon: Monitor, label: 'Сессии' },
  { to: '/mfa', icon: ShieldCheck, label: 'MFA' },
]

export default function SidebarLayout() {
  const { user, logout } = useAuth()

  return (
    <div className="flex h-screen">
      <aside className="w-64 border-r border-border bg-white flex flex-col">
        <div className="h-16 flex items-center px-6 border-b border-border">
          <span className="text-lg font-medium text-navy">iam<span className="text-purple">sso</span></span>
        </div>
        <nav className="flex-1 py-4 px-3">
          {navItems.map(({ to, icon: Icon, label }) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => `flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors mb-1 ${isActive ? 'bg-purple-bg text-purple font-medium' : 'text-body hover:text-navy hover:bg-surface'}`}>
              <Icon size={18} />{label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-border p-4">
          <div className="flex items-center justify-between">
            <div className="min-w-0">
              <p className="text-sm font-medium text-navy truncate">{user?.email || user?.sub || 'User'}</p>
              <p className="text-xs text-body">{user?.role || 'user'}</p>
            </div>
            <button onClick={logout} className="p-2 rounded-md text-body hover:text-ruby hover:bg-red-50 transition-colors" title="Выйти">
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>
      <main className="flex-1 overflow-auto bg-surface">
        <div className="max-w-[1080px] mx-auto p-8"><Outlet /></div>
      </main>
    </div>
  )
}
