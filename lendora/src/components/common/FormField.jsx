export default function FormField({
  label,
  name,
  error,
  hint,
  children,
  ...props
}) {
  const describedBy =
    [hint ? `${name}-hint` : "", error ? `${name}-error` : ""]
      .filter(Boolean)
      .join(" ") || undefined;
  return (
    <div className="form-field">
      <label htmlFor={name}>{label}</label>
      {children || (
        <input
          id={name}
          name={name}
          aria-invalid={!!error}
          aria-describedby={describedBy}
          {...props}
        />
      )}
      {hint && (
        <small id={`${name}-hint`} className="field-hint">
          {hint}
        </small>
      )}
      {error && (
        <small id={`${name}-error`} className="field-error">
          {error}
        </small>
      )}
    </div>
  );
}
