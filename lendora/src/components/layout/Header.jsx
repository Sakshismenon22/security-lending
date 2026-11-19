import { useState } from "react";
import { Link } from "react-router-dom";
import Brand from "../common/Brand";
import Icon from "../common/Icon";
export default function Header({ variant = "marketing" }) {
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <header
      className={`site-header ${variant !== "marketing" ? "auth-header" : ""}`}
    >
      <div className="header-inner">
        <Brand />
        {variant === "marketing" ? (
          <>
            <button
              className="menu-toggle"
              aria-label={menuOpen ? "Close navigation" : "Open navigation"}
              aria-expanded={menuOpen}
              aria-controls="main-navigation"
              onClick={() => setMenuOpen(!menuOpen)}
            >
              <Icon name={menuOpen ? "close" : "menu"} />
            </button>
            <nav
              id="main-navigation"
              className={menuOpen ? "marketing-nav is-open" : "marketing-nav"}
              aria-label="Main navigation"
            >
              <div className="nav-links">
                {[
                  ["Platform", "/#platform"],
                  ["How it works", "/#how-it-works"],
                  ["For lenders", "/#for-lenders"],
                  ["For borrowers", "/#for-borrowers"],
                ].map(([text, href]) => (
                  <Link key={href} to={href} onClick={() => setMenuOpen(false)}>
                    {text}
                  </Link>
                ))}
              </div>
              <div className="nav-actions">
                <Link to="/login" onClick={() => setMenuOpen(false)}>
                  Log in
                </Link>
                <Link
                  className="button primary small"
                  to="/signup"
                  onClick={() => setMenuOpen(false)}
                >
                  Create organization
                </Link>
              </div>
            </nav>
          </>
        ) : variant === "signup" ? (
          <span className="header-prompt">
            Already registered? <Link to="/login">Log in</Link>
          </span>
        ) : (
          <Link className="back-link" to="/">
            <Icon name="back" />
            Back to home
          </Link>
        )}
      </div>
    </header>
  );
}
