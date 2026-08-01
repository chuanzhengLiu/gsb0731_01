import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import { cn } from '@/lib/utils'
import {
  LayoutDashboard,
  Mic2,
  ListTodo,
  Send,
  FolderOpen,
  BarChart3,
  Users,
  LogOut,
} from 'lucide-react'

const navItems = [
  { to: '/', label: '工作台', icon: LayoutDashboard, end: true },
  { to: '/podcasts', label: '节目管理', icon: Mic2, end: false },
  { to: '/tasks', label: '任务看板', icon: ListTodo, end: false },
  { to: '/distribution', label: '分发管理', icon: Send, end: false },
  { to: '/assets', label: '素材库', icon: FolderOpen, end: false },
  { to: '/stats', label: '数据统计', icon: BarChart3, end: false },
  { to: '/team', label: '团队设置', icon: Users, end: false },
]

export default function MainLayout() {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <div className="flex h-screen bg-background">
      <aside className="w-60 border-r bg-card flex flex-col shrink-0">
        <div className="h-14 flex items-center px-6 border-b">
          <Mic2 className="h-6 w-6 text-primary mr-2" />
          <span className="font-bold text-lg">播客协作</span>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t p-3">
          <div className="flex items-center gap-3 px-3 py-2">
            <div className="h-8 w-8 rounded-full bg-primary/10 flex items-center justify-center text-sm font-medium">
              {user?.name?.charAt(0) || 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate">{user?.name}</p>
              <p className="text-xs text-muted-foreground truncate">{user?.email}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-colors"
          >
            <LogOut className="h-4 w-4" />
            退出登录
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
