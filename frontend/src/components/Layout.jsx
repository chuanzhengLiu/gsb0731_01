import { NavLink, Outlet } from 'react-router-dom'
import { getUser, logout } from '../lib/api'
import { ROLES } from '../lib/utils'
import { cn } from '../lib/utils'

const NAV_ITEMS = [
  { to: '/', label: '工作台', end: true },
  { to: '/podcasts', label: '节目管理' },
  { to: '/distribution', label: '分发管理' },
  { to: '/assets', label: '素材库' },
  { to: '/stats', label: '数据统计' },
  { to: '/members', label: '团队成员' },
]

export default function Layout() {
  const user = getUser()
  return (
    <div className="flex min-h-screen">
      <aside className="flex w-56 flex-col border-r bg-card">
        <div className="border-b p-4">
          <h1 className="text-base font-bold">播客协作系统</h1>
          <p className="mt-1 truncate text-xs text-muted-foreground">{user?.teamName || ''}</p>
        </div>
        <nav className="flex-1 space-y-1 p-2">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'block rounded-md px-3 py-2 text-sm font-medium',
                  isActive ? 'bg-primary text-primary-foreground' : 'hover:bg-accent',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t p-4">
          <p className="text-sm font-medium">{user?.name}</p>
          <p className="text-xs text-muted-foreground">{ROLES[user?.role] || user?.role}</p>
          <button onClick={logout} className="mt-2 text-xs text-destructive hover:underline">
            退出登录
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto p-6">
        <Outlet />
      </main>
    </div>
  )
}
