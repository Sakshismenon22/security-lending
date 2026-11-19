import { useEffect } from "react";
import {
  BrowserRouter,
  Navigate,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import LandingPage from "../pages/LandingPage";
import LoginPage from "../pages/LoginPage";
import SignupPage from "../pages/SignupPage";
import ForgotPasswordPage from "../pages/ForgotPasswordPage";
import LegalPage from "../pages/LegalPage";
import NotFoundPage from "../pages/NotFoundPage";
function RouteEffects() {
  const { pathname, hash } = useLocation();
  useEffect(() => {
    const titles = {
      "/": "Institutional securities lending",
      "/login": "Log in",
      "/signup": "Create your organization",
      "/forgot-password": "Reset your password",
      "/privacy": "Privacy information",
      "/terms": "Terms of use",
    };
    document.title = `${titles[pathname] ?? "Page not found"} | Lendora`;
    const frame = requestAnimationFrame(() => {
      if (hash)
        document
          .getElementById(hash.slice(1))
          ?.scrollIntoView({ behavior: "auto", block: "start" });
      else {
        window.scrollTo(0, 0);
        document.getElementById("main-content")?.focus({ preventScroll: true });
      }
    });
    return () => cancelAnimationFrame(frame);
  }, [pathname, hash]);
  return null;
}
export default function AppRoutes() {
  return (
    <BrowserRouter>
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <RouteEffects />
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/register" element={<Navigate to="/signup" replace />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/privacy" element={<LegalPage privacy />} />
        <Route path="/terms" element={<LegalPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
