import { Link } from "react-router-dom";
import Brand from "../common/Brand";
export default function Footer({ compact = false }) {
  return (
    <footer className={`site-footer${compact ? " compact-footer" : ""}`}>
      <div className="footer-inner">
        {compact ? (
          <span>© {new Date().getFullYear()} Lendora</span>
        ) : (
          <Brand compact />
        )}
        <nav aria-label="Footer navigation">
          {!compact && <Link to="/#platform">Platform</Link>}
          <Link to="/privacy">Privacy</Link>
          <Link to="/terms">Terms</Link>
        </nav>
      </div>
    </footer>
  );
}
