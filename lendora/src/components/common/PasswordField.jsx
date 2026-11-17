import { useState } from "react";
import FormField from "./FormField";
import Icon from "./Icon";
export default function PasswordField({
  label = "Password",
  name = "password",
  error,
  hint,
  ...props
}) {
  const [visible, setVisible] = useState(false);
  return (
    <FormField label={label} name={name} error={error} hint={hint}>
      <div className="password-input">
        <input
          id={name}
          name={name}
          type={visible ? "text" : "password"}
          aria-invalid={!!error}
          aria-describedby={
            [hint && `${name}-hint`, error && `${name}-error`]
              .filter(Boolean)
              .join(" ") || undefined
          }
          {...props}
        />
        <button
          type="button"
          aria-label={`${visible ? "Hide" : "Show"} ${label.toLowerCase()}`}
          aria-pressed={visible}
          onClick={() => setVisible(!visible)}
        >
          <Icon name={visible ? "eyeOff" : "eye"} />
        </button>
      </div>
    </FormField>
  );
}
