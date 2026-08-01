import { useEffect } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/authStore'
import MainLayout from '@/layouts/MainLayout'
import { LoginPage } from '@/pages/LoginPage'
import { RegisterPage } from '@/pages/RegisterPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { PodcastsPage } from '@/pages/PodcastsPage'
import { PodcastDetailPage } from '@/pages/PodcastDetailPage'
import { EpisodeDetailPage } from '@/pages/EpisodeDetailPage'
import { TasksPage } from '@/pages/TasksPage'
import { DistributionPage } from '@/pages/DistributionPage'
import { AssetsPage } from '@/pages/AssetsPage'
import { StatsPage } from '@/pages/StatsPage'
import { TeamPage } from '@/pages/TeamPage'
import { SharePage } from '@/pages/SharePage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuthStore()
  if (isLoading) return <div className="flex h-screen items-center justify-center">加载中...</div>
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <>{children}</>
}

function PublicRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, isLoading } = useAuthStore()
  if (isLoading) return <div className="flex h-screen items-center justify-center">加载中...</div>
  if (isAuthenticated) return <Navigate to="/" replace />
  return <>{children}</>
}

export default function App() {
  const loadUser = useAuthStore((s) => s.loadUser)

  useEffect(() => {
    loadUser()
  }, [loadUser])

  return (
    <Routes>
      <Route path="/login" element={<PublicRoute><LoginPage /></PublicRoute>} />
      <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
      <Route path="/share/:token" element={<SharePage />} />
      <Route
        path="/"
        element={
          <PrivateRoute>
            <MainLayout />
          </PrivateRoute>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="podcasts" element={<PodcastsPage />} />
        <Route path="podcasts/:id" element={<PodcastDetailPage />} />
        <Route path="episodes/:id" element={<EpisodeDetailPage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="distribution" element={<DistributionPage />} />
        <Route path="assets" element={<AssetsPage />} />
        <Route path="stats" element={<StatsPage />} />
        <Route path="team" element={<TeamPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
