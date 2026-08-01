import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '@/lib/auth'
import { cn } from '@/lib/utils'
import {
  LayoutDashboard,
  Radio,
  Users,
  Send,
  Library,
  BarChart3,
  LogOut,
  Mic2,
} from 'lucide-react'

const nav = [
  { to: '/', label: '工作台', icon: LayoutDashboard, end: true },
  { to: '/podcasts', label: '节目与单集', icon: Radio },
  { to: '/distribution', label: '分发管理', icon: Send },
  { to: '/assets', label: '素材库', icon: Library },
  { to: '/stats', label: '数据统计', icon: BarChart3 },
  { to: '/team', label: '团队成员', icon: Users },
]

export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="min-h-screen flex">
      <aside className="w-60 border-r bg-card flex flex-col">
        <div className="p-5 border-b flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-md bg-primary/10">
            <Mic2 className="h-4 w-4 text-primary" />
          </div>
          <span className="font-semibold">播客协作</span>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-foreground'
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="p-3 border-t">
          <div className="px-2 py-1.5 text-sm">
            <div className="font-medium truncate">{user?.name}</div>
            <div className="text-xs text-muted-foreground truncate">{user?.email}</div>
          </div>
          <button
            onClick={handleLogout}
            className="mt-1 flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            <LogOut className="h-4 w-4" />
            退出登录
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto">
        <div className="container py-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
