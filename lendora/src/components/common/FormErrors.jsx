export default function FormErrors({ errors, serverError, summaryRef }) {
  if (!Object.keys(errors).length && !serverError) return null;
  return (
    <div
      className="form-error-summary"
      role="alert"
      tabIndex={-1}
      ref={summaryRef}
    >
      {!Object.keys(errors).length ? (
        <p>{serverError}</p>
      ) : (
        <>
          <strong>Please check the following:</strong>
          <ul>
            {Object.entries(errors).map(([name, message]) => (
              <li key={name}>
                <a href={`#${name}`}>{message}</a>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
