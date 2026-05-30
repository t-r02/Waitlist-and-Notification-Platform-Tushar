import { Routes, Route } from "react-router-dom";
import { Toaster } from "sonner";
import { Navbar } from "@/components/Navbar";
import { HomePage } from "@/pages/Home";
import { LeaderboardPage } from "@/pages/Leaderboard";
import { ProfilePage } from "@/pages/Profile";
import { NotFoundPage } from "@/pages/NotFound";
import { LandingPage } from "@/pages/Landing";
import { VerifyPage } from "@/pages/Verify";
import { AdminLoginPage } from "@/pages/admin/Login";
import { AdminDashboardPage } from "@/pages/admin/Dashboard";
import { AdminEntryDetailPage } from "@/pages/admin/EntryDetail";
import { FraudDashboardPage } from "@/pages/admin/FraudDashboard";
import { RequireAuth } from "@/pages/admin/RequireAuth";

export function App() {
  return (
    <>
      <Navbar />
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/leaderboard" element={<LeaderboardPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/access" element={<LandingPage />} />
          <Route path="/verify" element={<VerifyPage />} />
          <Route path="/admin/login" element={<AdminLoginPage />} />
          <Route element={<RequireAuth />}>
            <Route path="/admin" element={<AdminDashboardPage />} />
            <Route
              path="/admin/entries/:id"
              element={<AdminEntryDetailPage />}
            />
            <Route path="/admin/fraud" element={<FraudDashboardPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <Toaster richColors position="top-right" />
    </>
  );
}
