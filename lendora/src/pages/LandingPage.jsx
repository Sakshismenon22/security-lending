import { Link } from "react-router-dom";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
import Icon from "../components/common/Icon";
import WorkspacePreview, {
  PreviewTable,
} from "../components/marketing/WorkspacePreview";
import { desks } from "../data/marketingData";
import "../styles/marketing.css";
export default function LandingPage() {
  return (
    <>
      <Header />
      <main id="main-content" tabIndex={-1}>
        <section className="hero-section">
          <svg
            className="building-lines"
            viewBox="0 0 1100 460"
            fill="none"
            aria-hidden="true"
          >
            <path d="m10 450 1050-260M130 440v-65l290-80v130m210-30V150L900 10l160 130v280M630 150l270-110 160 125M900 10v410M630 230l430-20M630 310l430-35" />
            {[0, 1, 2, 3, 4, 5, 6].map((n) => (
              <path
                key={n}
                d={`M${665 + n * 32} ${132 - n * 13}v${288 + n * 13}M${925 + n * 19} ${30 + n * 16}v${390 - n * 16}`}
              />
            ))}
          </svg>
          <div className="container hero-grid">
            <div className="hero-copy">
              <p className="eyebrow">Institutional securities lending</p>
              <h1>
                Securities lending.
                <br />
                One connected workspace.
              </h1>
              <p className="hero-description">
                Connect institutional lenders and borrowers. Manage agreements,
                locates, collateral and the full loan lifecycle.
              </p>
              <div className="hero-actions">
                <Link className="button primary" to="/signup">
                  Create organization
                </Link>
                <Link className="button secondary" to="/#platform">
                  Explore the platform
                </Link>
              </div>
            </div>
            <WorkspacePreview />
          </div>
        </section>
        <section
          className="capability-strip"
          id="platform"
          aria-label="Platform capabilities"
        >
          <div className="container">
            {[
              ["handshake", "Agreements & counterparties"],
              ["search", "Locates & reservations"],
              ["layers", "Collateral & margin"],
              ["coins", "Fees & rebates"],
            ].map(([icon, text]) => (
              <div key={text}>
                <Icon name={icon} size={26} />
                <span>{text}</span>
              </div>
            ))}
          </div>
        </section>
        <section className="container desks-section">
          <h2>Two sides. One coordinated lifecycle.</h2>
          <div className="desks-grid">
            {desks.map((desk, index) => (
              <article id={desk.id} className="desk-overview" key={desk.id}>
                <h3>
                  <span>{desk.title}</span> <small>{desk.audience}</small>
                </h3>
                <p>{desk.description}</p>
                <div className="desk-content">
                  <ul>
                    {desk.points.map((point) => (
                      <li key={point}>
                        <Icon name="check" size={17} />
                        {point}
                      </li>
                    ))}
                  </ul>
                  <PreviewTable borrower={index === 1} compact />
                </div>
              </article>
            ))}
          </div>
          <ol id="how-it-works" className="onboarding-steps">
            {[
              "Register your organization",
              "Create your admin account",
              "Invite your team",
            ].map((step, index) => (
              <li key={step}>
                <span>{String(index + 1).padStart(2, "0")}</span>
                {step}
              </li>
            ))}
          </ol>
        </section>
        <section className="closing-cta">
          <div className="container">
            <h2>Bring your lending operations together.</h2>
            <Link className="button primary" to="/signup">
              Create organization
            </Link>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}
