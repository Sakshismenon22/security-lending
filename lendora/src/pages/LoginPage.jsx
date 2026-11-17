import { useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
import FormField from "../components/common/FormField";
import PasswordField from "../components/common/PasswordField";
import FormErrors from "../components/common/FormErrors";
import Icon from "../components/common/Icon";
import ConnectionIllustration from "../components/marketing/ConnectionIllustration";
import { authApi } from "../api/authApi";
import useAuthSubmission from "../hooks/useAuthSubmission";
import { validateLogin } from "../utils/formValidation";
import "../styles/auth.css";

export default function LoginPage() {
  const location = useLocation();
  const [values, setValues] = useState({
    email: location.state?.email ?? "",
    password: "",
    rememberMe: false,
  });
  const [errors, setErrors] = useState({}),
    [result, setResult] = useState(null);
  const summary = useRef(null),
    { submit, pending, error } = useAuthSubmission(authApi.login);
  async function handleSubmit(event) {
    event.preventDefault();
    const next = validateLogin(values);
    setErrors(next);
    if (Object.keys(next).length) {
      requestAnimationFrame(() => summary.current?.focus());
      return;
    }
    const response = await submit({ ...values, email: values.email.trim() });
    if (response) setResult(response);
    else requestAnimationFrame(() => summary.current?.focus());
  }
  function change(event) {
    setValues((current) => ({
      ...current,
      [event.target.name]: event.target.value,
    }));
  }
  const workspace = result?.organization?.type ?? result?.workspace;
  const workspaceUrl =
    workspace === "LENDER"
      ? import.meta.env.VITE_LENDDESK_URL
      : workspace === "BORROWER"
        ? import.meta.env.VITE_BORROWDESK_URL
        : null;
  return (
    <div className="auth-page">
      <Header variant="login" />
      <main id="main-content" className="login-layout" tabIndex={-1}>
        <aside className="login-story">
          <p className="eyebrow">Institutional securities lending</p>
          <h2>
            Your lending
            <br />
            workspace, connected.
          </h2>
          <p>
            Manage agreements, locates, loans and collateral in one connected
            workspace.
          </p>
          <ConnectionIllustration />
        </aside>
        <section className="login-content">
          <div className="login-form-container">
            {result ? (
              <div className="success-panel" role="status">
                <span className="success-icon">
                  <Icon name="check" size={28} />
                </span>
                <h1>Welcome back.</h1>
                <p>
                  You are signed in
                  {result.organization?.name
                    ? ` to ${result.organization.name}`
                    : ""}
                  .
                </p>
                {workspaceUrl ? (
                  <a className="button primary" href={workspaceUrl}>
                    Open {workspace === "LENDER" ? "LendDesk" : "BorrowDesk"}
                    <Icon name="arrow" />
                  </a>
                ) : (
                  <p>
                    Your organization administrator can provide your workspace
                    link.
                  </p>
                )}
                <Link to="/">Back to home</Link>
              </div>
            ) : (
              <>
                <header className="form-heading">
                  <h1>Welcome back</h1>
                  <p>Sign in to your organization.</p>
                </header>
                <form className="login-form" onSubmit={handleSubmit} noValidate>
                  <FormErrors
                    errors={errors}
                    serverError={error}
                    summaryRef={summary}
                  />
                  <FormField
                    disabled={pending}
                    label="Work email"
                    name="email"
                    type="email"
                    autoComplete="username"
                    placeholder="name@organization.com"
                    maxLength={254}
                    required
                    value={values.email}
                    onChange={change}
                    error={errors.email}
                  />
                  <PasswordField
                    disabled={pending}
                    required
                    autoComplete="current-password"
                    placeholder="Enter your password"
                    value={values.password}
                    onChange={change}
                    error={errors.password}
                  />
                  <div className="login-options">
                    <label className="checkbox-label">
                      <input
                        type="checkbox"
                        disabled={pending}
                        checked={values.rememberMe}
                        onChange={(e) =>
                          setValues((current) => ({
                            ...current,
                            rememberMe: e.target.checked,
                          }))
                        }
                      />
                      Remember me
                    </label>
                    <Link to="/forgot-password">Forgot password?</Link>
                  </div>
                  <button
                    className="button primary full-width"
                    type="submit"
                    disabled={pending}
                  >
                    {pending ? "Signing in…" : "Log in"}
                  </button>
                </form>
                <div className="auth-switch">
                  <span>
                    New to Lendora?{" "}
                    <Link to="/signup">Create an organization</Link>
                  </span>
                </div>
              </>
            )}
          </div>
          <Footer compact />
        </section>
      </main>
    </div>
  );
}
