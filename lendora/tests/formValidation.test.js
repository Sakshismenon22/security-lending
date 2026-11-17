import test from "node:test";
import assert from "node:assert/strict";
import {
  validateLogin,
  validateRegistration,
  validateReset,
} from "../src/utils/formValidation.js";
const valid = {
  organizationName: "Example Capital",
  organizationType: "LENDER",
  registrationNumber: "REG-001",
  country: "India",
  baseCurrency: "INR",
  registeredAddress: "100 Market Road, Mumbai",
  fullName: "Example Admin",
  email: "admin@example.com",
  password: "valid-passphrase-2026",
  confirmPassword: "valid-passphrase-2026",
  agreeTerms: true,
};
test("lender and borrower registration accept valid organization and administrator details", () => {
  assert.deepEqual(validateRegistration(valid), {});
  assert.deepEqual(
    validateRegistration({ ...valid, organizationType: "BORROWER" }),
    {},
  );
});
test("whitespace organization details, invalid choices, and missing consent are refused", () => {
  const errors = validateRegistration({
    ...valid,
    organizationName: "  ",
    fullName: " ",
    organizationType: "ADMIN",
    country: "Unknown",
    baseCurrency: "ZZZ",
    agreeTerms: false,
  });
  assert.deepEqual(
    Object.keys(errors).sort(),
    [
      "organizationName",
      "fullName",
      "organizationType",
      "country",
      "baseCurrency",
      "agreeTerms",
    ].sort(),
  );
});
test("registration enforces password length and exact confirmation without trimming passwords", () => {
  assert.ok(
    validateRegistration({
      ...valid,
      password: "short",
      confirmPassword: "short",
    }).password,
  );
  assert.ok(
    validateRegistration({
      ...valid,
      password: "            ",
      confirmPassword: "            ",
    }).password,
  );
  assert.ok(
    validateRegistration({ ...valid, confirmPassword: `${valid.password} ` })
      .confirmPassword,
  );
});
test("login and recovery validate email without imposing new signup rules on existing passwords", () => {
  assert.deepEqual(
    validateLogin({ email: " admin@example.com ", password: "legacy-pass" }),
    {},
  );
  assert.ok(validateLogin({ email: "not-an-email", password: "" }).email);
  assert.ok(validateLogin({ email: valid.email, password: "" }).password);
  assert.deepEqual(validateReset({ email: valid.email }), {});
  assert.ok(validateReset({ email: "admin@" }).email);
});
