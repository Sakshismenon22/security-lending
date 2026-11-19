import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
import FormField from "../components/common/FormField";
import FormErrors from "../components/common/FormErrors";
import Icon from "../components/common/Icon";
import useAuthSubmission from "../hooks/useAuthSubmission";
import { authApi } from "../api/authApi";
import { validateReset } from "../utils/formValidation";
import "../styles/auth.css";
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState(""),
    [errors, setErrors] = useState({}),
    [sent, setSent] = useState(false);
  const summary = useRef(null),
    { submit, pending, error } = useAuthSubmission(authApi.resetPassword);
  async function handleSubmit(event) {
    event.preventDefault();
    const next = validateReset({ email });
    setErrors(next);
    if (Object.keys(next).length) {
      requestAnimationFrame(() => summary.current?.focus());
      return;
    }
    const result = await submit({ email: email.trim() });
    if (result) setSent(true);
    else requestAnimationFrame(() => summary.current?.focus());
  }
  return (
    <div className="simple-page">
      <Header variant="login" />
      <main id="main-content" className="recovery-main" tabIndex={-1}>
        <div className="recovery-card">
          {sent ? (
            <div className="success-panel" role="status">
              <span className="success-icon">
                <Icon name="check" size={28} />
              </span>
              <h1>Check your inbox</h1>
              <p>
                If an account exists for <strong>{email}</strong>, you’ll
                receive instructions to reset your password.
              </p>
              <Link className="button primary" to="/login">
                Back to log in
              </Link>
            </div>
          ) : (
            <>
              <p className="eyebrow">Account recovery</p>
              <h1>Forgot your password?</h1>
              <p>Enter your work email and we’ll send you a reset link.</p>
              <form onSubmit={handleSubmit} noValidate>
                <FormErrors
                  errors={errors}
                  serverError={error}
                  summaryRef={summary}
                />
                <FormField
                  disabled={pending}
                  name="email"
                  label="Work email"
                  type="email"
                  autoComplete="email"
                  required
                  maxLength={254}
                  placeholder="name@organization.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  error={errors.email}
                />
                <button
                  type="submit"
                  className="button primary full-width"
                  disabled={pending}
                >
                  {pending
                    ? "Sending instructions…"
                    : "Send reset instructions"}
                </button>
              </form>
              <Link className="back-link" to="/login">
                <Icon name="back" size={17} />
                Back to log in
              </Link>
            </>
          )}
        </div>
      </main>
      <Footer compact />
    </div>
  );
}
