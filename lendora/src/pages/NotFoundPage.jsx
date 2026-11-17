import { Link } from "react-router-dom";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
export default function NotFoundPage() {
  return (
    <div className="simple-page">
      <Header variant="login" />
      <main id="main-content" className="recovery-main" tabIndex={-1}>
        <div className="recovery-card">
          <p className="eyebrow">404 · Page not found</p>
          <h1>Let’s get you back.</h1>
          <p>This page may have moved, or the address may be incorrect.</p>
          <Link className="button primary" to="/">
            Back to home
          </Link>
        </div>
      </main>
      <Footer compact />
    </div>
  );
}
