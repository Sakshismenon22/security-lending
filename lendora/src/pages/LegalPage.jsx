import { Link } from "react-router-dom";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
export default function LegalPage({ privacy = false }) {
  return (
    <div className="simple-page">
      <Header variant="login" />
      <main id="main-content" className="legal-main" tabIndex={-1}>
        <p className="eyebrow">Organization policies</p>
        <h1>{privacy ? "Privacy information" : "Terms of use"}</h1>
        {privacy ? (
          <>
            <h2>Your organization and account information</h2>
            <p>
              Registration asks for your organization’s legal name, registration
              number, country, base currency and address, together with the
              administrator’s name, work email and password.
            </p>
            <p>
              Account forms submit this information to Lendora’s account
              service. Passwords are not saved in your browser’s local storage.
            </p>
            <h2>Review your organization’s policy</h2>
            <p>
              Request the applicable privacy notice from your organization
              administrator for information about data retention, access and
              support.
            </p>
          </>
        ) : (
          <>
            <h2>Organization access</h2>
            <p>
              Lendora is a workspace for institutional securities lending.
              Organization registration and account access are subject to the
              applicable onboarding agreement.
            </p>
            <h2>Review the applicable agreement</h2>
            <p>
              Contact your organization administrator for the current terms of
              service before creating an account. This page is an overview and
              does not replace your organization’s agreement.
            </p>
          </>
        )}
        <Link className="button secondary" to="/signup">
          Return to organization registration
        </Link>
      </main>
      <Footer />
    </div>
  );
}
