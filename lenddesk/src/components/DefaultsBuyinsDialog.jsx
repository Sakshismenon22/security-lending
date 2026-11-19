import { useEffect, useRef, useState } from "react";
import { number, previewLoans, rupees } from "../data/defaultsBuyinsData";

export default function DefaultsBuyinsDialog({
  kind,
  record,
  onClose,
  onSave,
}) {
  const dialog = useRef(null);
  const [values, setValues] = useState({
    loan: previewLoans[0].id,
    trigger: "Recall overdue",
    quantity: String(
      kind === "default"
        ? previewLoans[0].quantity
        : (record.buyinDraft?.quantity ??
            record.quantity - (record.order?.filled || 0)),
    ),
    deadline: "2026-11-19T15:00",
    reference: "",
    price: String(
      record?.buyinDraft?.price ||
        record?.order?.limitPrice ||
        record?.price ||
        "",
    ),
    notice: "Default notice",
  });
  const [error, setError] = useState("");
  const title =
    kind === "default"
      ? "Record default"
      : kind === "notice"
        ? "Prepare notice"
        : record.order
          ? "Manage buy-in"
          : "Create buy-in";
  useEffect(() => {
    const previous = document.activeElement;
    const element = dialog.current;
    element.querySelector("input, select, button")?.focus();
    function keyboard(event) {
      if (event.key === "Escape") onClose();
      if (event.key !== "Tab") return;
      const controls = [
        ...element.querySelectorAll(
          "button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)",
        ),
      ];
      const first = controls[0],
        last = controls.at(-1);
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last?.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first?.focus();
      }
    }
    document.addEventListener("keydown", keyboard);
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", keyboard);
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, [onClose]);
  const loan = previewLoans.find((item) => item.id === values.loan);
  const filled = record?.order?.filled || 0;
  const maximum =
    kind === "default" ? loan.quantity : (record?.quantity || 0) - filled;
  function change(event) {
    const { name, value } = event.target;
    setValues((current) => ({
      ...current,
      [name]: value,
      ...(name === "loan"
        ? {
            quantity: String(
              previewLoans.find((item) => item.id === value).quantity,
            ),
          }
        : {}),
    }));
    setError("");
  }
  function submit(event) {
    event.preventDefault();
    const quantity = Number(values.quantity),
      price = Number(values.price);
    if (
      kind !== "notice" &&
      (!Number.isSafeInteger(quantity) || quantity <= 0 || quantity > maximum)
    ) {
      setError(`Enter a whole quantity from 1 to ${number(maximum)}.`);
      return;
    }
    if (
      kind === "default" &&
      (!values.deadline ||
        !Number.isFinite(Date.parse(values.deadline)) ||
        !values.reference.trim())
    ) {
      setError("Provide a valid deadline and source reference.");
      return;
    }
    if (
      kind === "buyin" &&
      (!Number.isFinite(price) ||
        price <= 0 ||
        Math.abs(price * 100 - Math.round(price * 100)) > 0.000001)
    ) {
      setError("Enter a positive limit price with at most two decimal places.");
      return;
    }
    onSave({
      ...values,
      quantity,
      price,
      loan,
      reference: values.reference.trim(),
    });
  }
  return (
    <div
      className="db-dialog-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="db-dialog-title"
        aria-describedby="db-dialog-description"
        className="db-dialog"
      >
        <header>
          <div>
            <span className="db-eyebrow">LOCAL PREVIEW</span>
            <h2 id="db-dialog-title">{title}</h2>
          </div>
          <button
            type="button"
            className="db-close"
            aria-label="Close dialog"
            onClick={onClose}
          >
            ×
          </button>
        </header>
        <p id="db-dialog-description">
          Save a draft in this page. No default, notice or market order will be
          submitted.
        </p>
        <form onSubmit={submit} noValidate>
          {kind === "default" ? (
            <>
              <label>
                Loan
                <select
                  name="loan"
                  aria-label="Loan"
                  value={values.loan}
                  onChange={change}
                >
                  {previewLoans.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.id} · {item.security} · {item.borrower}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Trigger
                <select name="trigger" value={values.trigger} onChange={change}>
                  {[
                    "Recall overdue",
                    "Margin shortfall",
                    "Settlement failure",
                  ].map((item) => (
                    <option key={item}>{item}</option>
                  ))}
                </select>
              </label>
              <div className="db-form-grid">
                <label>
                  Quantity
                  <input
                    name="quantity"
                    type="number"
                    min="1"
                    max={maximum}
                    step="1"
                    value={values.quantity}
                    onChange={change}
                  />
                </label>
                <label>
                  Obligation deadline (IST)
                  <input
                    name="deadline"
                    type="datetime-local"
                    value={values.deadline}
                    onChange={change}
                  />
                </label>
              </div>
              <label>
                Source reference
                <input
                  name="reference"
                  maxLength={160}
                  placeholder="Recall, margin call or settlement reference"
                  value={values.reference}
                  onChange={change}
                />
              </label>
              <small>
                Outstanding quantity: {number(maximum)} {loan.security}
              </small>
            </>
          ) : (
            <>
              <div className="db-dialog-summary">
                <b>
                  {record.id} / {record.security}
                </b>
                <span>
                  {record.borrower} · {record.loanId}
                </span>
              </div>
              {kind === "notice" ? (
                <>
                  <label>
                    Notice type
                    <select
                      name="notice"
                      value={values.notice}
                      onChange={change}
                    >
                      <option>Default notice</option>
                      <option>Buy-in intent</option>
                    </select>
                  </label>
                  <label>
                    Message
                    <textarea
                      name="reference"
                      rows="4"
                      maxLength={1000}
                      placeholder="Add a message for the borrower…"
                      value={values.reference}
                      onChange={change}
                    />
                  </label>
                  <small>
                    Recipient: {record.borrower}. The draft will not be sent.
                  </small>
                </>
              ) : (
                <>
                  <div className="db-form-grid">
                    <label>
                      Remaining order quantity
                      <input
                        name="quantity"
                        type="number"
                        min="1"
                        max={maximum}
                        step="1"
                        value={values.quantity}
                        onChange={change}
                      />
                    </label>
                    <label>
                      Limit price (INR)
                      <input
                        name="price"
                        type="number"
                        min="0.01"
                        step="0.01"
                        value={values.price}
                        onChange={change}
                      />
                    </label>
                  </div>
                  <small>
                    Obligation: {number(record.quantity)} · Already filled:{" "}
                    {number(filled)} · Maximum remaining: {number(maximum)}
                  </small>
                  <div className="db-order-estimate">
                    <span>Estimated remaining order value</span>
                    <strong>
                      {rupees(
                        Math.max(0, Number(values.quantity) || 0) *
                          Math.max(0, Number(values.price) || 0),
                      )}
                    </strong>
                  </div>
                  <p className="db-form-note">
                    Existing fills remain unchanged. Your edits are saved as a
                    draft and do not replace a working market order.
                  </p>
                </>
              )}
            </>
          )}
          {error && (
            <p className="db-form-error" role="alert">
              {error}
            </p>
          )}
          <footer>
            <button type="button" className="db-button" onClick={onClose}>
              Cancel
            </button>
            <button className="db-button db-primary" type="submit">
              Save{" "}
              {kind === "notice"
                ? "notice"
                : kind === "default"
                  ? "default"
                  : "buy-in"}{" "}
              draft
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
