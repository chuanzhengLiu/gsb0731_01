import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './lib/auth'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import AppLayout from './components/AppLayout'
import DashboardPage from './pages/DashboardPage'
import PodcastsPage from './pages/PodcastsPage'
import PodcastDetailPage from './pages/PodcastDetailPage'
import EpisodeWorkspace from './pages/EpisodeWorkspace'
import TeamPage from './pages/TeamPage'
import DistributionPage from './pages/DistributionPage'
import AssetsPage from './pages/AssetsPage'
import StatsPage from './pages/StatsPage'
import SharePage from './pages/SharePage'
import AcceptInvitePage from './pages/AcceptInvitePage'

function Protected({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/invite/:token" element={<AcceptInvitePage />} />
      <Route path="/share/:token" element={<SharePage />} />
      <Route
        path="/"
        element={
          <Protected>
            <AppLayout />
          </Protected>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="podcasts" element={<PodcastsPage />} />
        <Route path="podcasts/:podcastId" element={<PodcastDetailPage />} />
        <Route path="episodes/:episodeId" element={<EpisodeWorkspace />} />
        <Route path="team" element={<TeamPage />} />
        <Route path="distribution" element={<DistributionPage />} />
        <Route path="assets" element={<AssetsPage />} />
        <Route path="stats" element={<StatsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
