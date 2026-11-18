import { useEffect, useRef, useId } from "react";

export default function DefaultsBuyinsModal({
  title,
  description,
  onClose,
  children,
}) {
  const dialog = useRef(null),
    titleId = useId(),
    descriptionId = useId();
  useEffect(() => {
    const previous = document.activeElement,
      element = dialog.current;
    (
      element.querySelector(
        "button:not(:disabled), input:not(:disabled), select:not(:disabled)",
      ) || element
    ).focus();
    function keyboard(event) {
      if (event.key === "Escape") onClose?.();
      if (event.key !== "Tab") return;
      const controls = [
        ...element.querySelectorAll(
          "button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)",
        ),
      ];
      if (!controls.length) {
        event.preventDefault();
        element.focus();
        return;
      }
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
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    document.addEventListener("keydown", keyboard);
    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener("keydown", keyboard);
      previous?.focus();
    };
  }, [onClose]);
  return (
    <div
      className="db-dialog-backdrop"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose?.();
      }}
    >
      <section
        className="db-dialog"
        ref={dialog}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descriptionId}
      >
        <header>
          <h2 id={titleId}>{title}</h2>
          <button
            className="db-close"
            aria-label="Close dialog"
            disabled={!onClose}
            onClick={onClose}
          >
            ×
          </button>
        </header>
        <p id={descriptionId}>{description}</p>
        {children}
      </section>
    </div>
  );
}
