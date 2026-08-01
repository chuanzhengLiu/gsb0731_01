import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { PodcastsPage } from "./pages/PodcastsPage";
import { EpisodesPage } from "./pages/EpisodesPage";
import { EpisodeWorkspacePage } from "./pages/EpisodeWorkspacePage";
import { MembersPage } from "./pages/MembersPage";
import { DistributionPage } from "./pages/DistributionPage";
import { AssetsPage } from "./pages/AssetsPage";
import { StatsPage } from "./pages/StatsPage";
import { SharePage } from "./pages/SharePage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { AcceptInvitePage } from "./pages/AcceptInvitePage";
import { Layout } from "./components/Layout";
import type { ReactNode } from "react";

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return <Layout>{children}</Layout>;
}

export default function App() {
  return (
    <Routes>
      {/* Public routes */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="/accept-invite" element={<AcceptInvitePage />} />
      <Route path="/share" element={<SharePage />} />

      {/* Authenticated routes */}
      <Route path="/" element={<RequireAuth><PodcastsPage /></RequireAuth>} />
      <Route path="/members" element={<RequireAuth><MembersPage /></RequireAuth>} />
      <Route path="/distribution" element={<RequireAuth><DistributionPage /></RequireAuth>} />
      <Route path="/assets" element={<RequireAuth><AssetsPage /></RequireAuth>} />
      <Route path="/stats" element={<RequireAuth><StatsPage /></RequireAuth>} />
      <Route path="/podcasts/:podcastId" element={<RequireAuth><EpisodesPage /></RequireAuth>} />
      <Route path="/episodes/:episodeId" element={<RequireAuth><EpisodeWorkspacePage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
