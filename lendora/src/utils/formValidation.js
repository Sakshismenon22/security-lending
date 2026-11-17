export const countries = [
  "India",
  "United States",
  "United Kingdom",
  "Singapore",
];
export const currencies = ["INR", "USD", "GBP", "SGD"];
const emailValid = (value) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
export function validateLogin(values) {
  const errors = {};
  if (!emailValid(values.email))
    errors.email = "Enter a valid work email address.";
  if (!values.password) errors.password = "Enter your password.";
  return errors;
}
export function validateRegistration(values) {
  const errors = {};
  for (const [name, message] of [
    ["organizationName", "Enter your legal organization name."],
    ["registrationNumber", "Enter your registration number."],
    ["registeredAddress", "Enter your registered address."],
    ["fullName", "Enter your full name."],
  ]) {
    if (!values[name].trim()) errors[name] = message;
  }
  if (!["LENDER", "BORROWER"].includes(values.organizationType))
    errors.organizationType = "Choose your organization type.";
  if (!countries.includes(values.country)) errors.country = "Select a country.";
  if (!currencies.includes(values.baseCurrency))
    errors.baseCurrency = "Select a base currency.";
  if (!emailValid(values.email))
    errors.email = "Enter a valid work email address.";
  if (values.password.length < 12 || !values.password.trim())
    errors.password = "Use at least 12 characters.";
  if (!values.confirmPassword || values.password !== values.confirmPassword)
    errors.confirmPassword = "Passwords must match.";
  if (!values.agreeTerms)
    errors.agreeTerms =
      "Please agree to the terms and privacy policy to continue.";
  return errors;
}
export function validateReset(values) {
  return emailValid(values.email)
    ? {}
    : { email: "Enter a valid work email address." };
}
