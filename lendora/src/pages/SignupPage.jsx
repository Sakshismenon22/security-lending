import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import Header from "../components/layout/Header";
import Footer from "../components/layout/Footer";
import FormField from "../components/common/FormField";
import PasswordField from "../components/common/PasswordField";
import FormErrors from "../components/common/FormErrors";
import Icon from "../components/common/Icon";
import { authApi } from "../api/authApi";
import useAuthSubmission from "../hooks/useAuthSubmission";
import {
  countries,
  currencies,
  validateRegistration,
} from "../utils/formValidation";
import "../styles/auth.css";
const initial = {
  organizationName: "",
  organizationType: "LENDER",
  registrationNumber: "",
  country: "India",
  baseCurrency: "INR",
  registeredAddress: "",
  fullName: "",
  email: "",
  password: "",
  confirmPassword: "",
  agreeTerms: false,
};
export default function SignupPage() {
  const [values, setValues] = useState(initial),
    [errors, setErrors] = useState({}),
    [created, setCreated] = useState(false);
  const summary = useRef(null),
    { submit, pending, error } = useAuthSubmission(authApi.register);
  function change(event) {
    setValues((current) => ({
      ...current,
      [event.target.name]: event.target.value,
    }));
  }
  async function handleSubmit(event) {
    event.preventDefault();
    const next = validateRegistration(values);
    setErrors(next);
    if (Object.keys(next).length) {
      requestAnimationFrame(() => summary.current?.focus());
      return;
    }
    const response = await submit({
      organization: {
        name: values.organizationName.trim(),
        type: values.organizationType,
        registrationNumber: values.registrationNumber.trim(),
        country: values.country,
        baseCurrency: values.baseCurrency,
        registeredAddress: values.registeredAddress.trim(),
      },
      administrator: {
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        password: values.password,
      },
      acceptedTerms: values.agreeTerms,
    });
    if (response) setCreated(true);
    else requestAnimationFrame(() => summary.current?.focus());
  }
  const field = (name, label, placeholder, extra = {}) => (
    <FormField
      disabled={pending}
      name={name}
      label={label}
      placeholder={placeholder}
      required
      value={values[name]}
      onChange={change}
      error={errors[name]}
      {...extra}
    />
  );
  return (
    <div className="signup-page">
      <Header variant="signup" />
      <main id="main-content" className="signup-main" tabIndex={-1}>
        <header className="signup-heading">
          <p className="eyebrow">Get started with Lendora</p>
          <h1>Create your organization</h1>
          <p>
            Register your organization and create its first administrator
            account.
          </p>
        </header>
        {created ? (
          <section className="signup-success success-panel" role="status">
            <span className="success-icon">
              <Icon name="check" size={30} />
            </span>
            <h2>Your organization is ready.</h2>
            <p>
              {values.organizationName} and your administrator account have been
              created.
            </p>
            <Link
              className="button primary"
              to="/login"
              state={{ email: values.email }}
            >
              Continue to log in
              <Icon name="arrow" />
            </Link>
          </section>
        ) : (
          <form className="signup-form" onSubmit={handleSubmit} noValidate>
            <FormErrors
              errors={errors}
              serverError={error}
              summaryRef={summary}
            />
            <div className="signup-columns">
              <section aria-labelledby="organization-heading">
                <h2 id="organization-heading">
                  <span className="step-number">01</span>Organization details
                </h2>
                {field(
                  "organizationName",
                  "Legal organization name",
                  "Enter registered organization name",
                  { autoComplete: "organization", maxLength: 160 },
                )}
                <fieldset
                  className="organization-types"
                  id="organizationType"
                  tabIndex={-1}
                >
                  <legend>Organization type</legend>
                  <div>
                    {[
                      ["LENDER", "Institutional lender"],
                      ["BORROWER", "Institutional borrower"],
                    ].map(([value, text]) => (
                      <label
                        key={value}
                        className={
                          values.organizationType === value ? "selected" : ""
                        }
                      >
                        <input
                          disabled={pending}
                          type="radio"
                          name="organizationType"
                          value={value}
                          checked={values.organizationType === value}
                          onChange={change}
                        />
                        {text}
                      </label>
                    ))}
                  </div>
                  {errors.organizationType && (
                    <small className="field-error">
                      {errors.organizationType}
                    </small>
                  )}
                </fieldset>
                {field(
                  "registrationNumber",
                  "Registration number",
                  "Enter registration number",
                  { maxLength: 100 },
                )}
                <div className="field-grid">
                  {[
                    ["country", "Country", countries],
                    ["baseCurrency", "Base currency", currencies],
                  ].map(([name, text, choices]) => (
                    <FormField
                      key={name}
                      name={name}
                      label={text}
                      error={errors[name]}
                    >
                      <select
                        disabled={pending}
                        id={name}
                        name={name}
                        value={values[name]}
                        onChange={change}
                        aria-invalid={!!errors[name]}
                        aria-describedby={
                          errors[name] ? `${name}-error` : undefined
                        }
                      >
                        {choices.map((choice) => (
                          <option key={choice}>{choice}</option>
                        ))}
                      </select>
                    </FormField>
                  ))}
                </div>
                <FormField
                  name="registeredAddress"
                  label="Registered address"
                  error={errors.registeredAddress}
                >
                  <textarea
                    disabled={pending}
                    id="registeredAddress"
                    name="registeredAddress"
                    required
                    maxLength={500}
                    autoComplete="street-address"
                    rows={3}
                    placeholder="Enter registered address"
                    value={values.registeredAddress}
                    onChange={change}
                    aria-invalid={!!errors.registeredAddress}
                    aria-describedby={
                      errors.registeredAddress
                        ? "registeredAddress-error"
                        : undefined
                    }
                  />
                </FormField>
              </section>
              <section aria-labelledby="administrator-heading">
                <h2 id="administrator-heading">
                  <span className="step-number">02</span>Administrator account
                </h2>
                {field("fullName", "Full name", "Enter your full name", {
                  autoComplete: "name",
                  maxLength: 100,
                })}
                {field("email", "Work email", "Enter your work email", {
                  type: "email",
                  autoComplete: "email",
                  maxLength: 254,
                })}
                <PasswordField
                  disabled={pending}
                  required
                  autoComplete="new-password"
                  hint="Use at least 12 characters."
                  value={values.password}
                  onChange={change}
                  error={errors.password}
                />
                <PasswordField
                  disabled={pending}
                  label="Confirm password"
                  name="confirmPassword"
                  required
                  autoComplete="new-password"
                  value={values.confirmPassword}
                  onChange={change}
                  error={errors.confirmPassword}
                />
                <div className="admin-note">
                  <Icon name="user" size={25} />
                  <p>
                    This account will be your organization’s administrator. You
                    can add other users after signing in.
                  </p>
                </div>
              </section>
            </div>
            <div className="signup-actions">
              <div>
                <label className="checkbox-label">
                  <input
                    disabled={pending}
                    id="agreeTerms"
                    type="checkbox"
                    checked={values.agreeTerms}
                    onChange={(e) =>
                      setValues((current) => ({
                        ...current,
                        agreeTerms: e.target.checked,
                      }))
                    }
                    aria-invalid={!!errors.agreeTerms}
                    aria-describedby={
                      errors.agreeTerms ? "agreeTerms-error" : undefined
                    }
                  />
                  <span>
                    I agree to the{" "}
                    <Link to="/terms" target="_blank" rel="noreferrer">
                      Terms of Use
                    </Link>{" "}
                    and{" "}
                    <Link to="/privacy" target="_blank" rel="noreferrer">
                      Privacy Policy
                    </Link>
                    .
                  </span>
                </label>
                {errors.agreeTerms && (
                  <small id="agreeTerms-error" className="field-error">
                    {errors.agreeTerms}
                  </small>
                )}
              </div>
              <button
                type="submit"
                className="button primary"
                disabled={pending}
              >
                {pending
                  ? "Creating organization…"
                  : "Create organization & admin account"}
              </button>
            </div>
          </form>
        )}
        <p className="signup-footnote">
          Your team can be managed from Administration after registration.
        </p>
      </main>
      <Footer />
    </div>
  );
}
