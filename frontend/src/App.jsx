import { Navigate, Route, Routes } from 'react-router-dom'
import { getUser } from './lib/api'
import Layout from './components/Layout'
import Login from './pages/Login'
import Register from './pages/Register'
import AcceptInvite from './pages/AcceptInvite'
import ForgotPassword from './pages/ForgotPassword'
import ResetPassword from './pages/ResetPassword'
import Dashboard from './pages/Dashboard'
import Podcasts from './pages/Podcasts'
import PodcastDetail from './pages/PodcastDetail'
import EpisodeDetail from './pages/EpisodeDetail'
import Members from './pages/Members'
import Distribution from './pages/Distribution'
import Assets from './pages/Assets'
import Stats from './pages/Stats'
import ShareView from './pages/ShareView'

function RequireAuth({ children }) {
  return getUser() ? children : <Navigate to="/login" replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/invite" element={<AcceptInvite />} />
      <Route path="/forgot-password" element={<ForgotPassword />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/share/:token" element={<ShareView />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="podcasts" element={<Podcasts />} />
        <Route path="podcasts/:id" element={<PodcastDetail />} />
        <Route path="episodes/:id" element={<EpisodeDetail />} />
        <Route path="members" element={<Members />} />
        <Route path="distribution" element={<Distribution />} />
        <Route path="assets" element={<Assets />} />
        <Route path="stats" element={<Stats />} />
      </Route>
    </Routes>
  )
}
