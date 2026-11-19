import { Link } from "react-router-dom";
export default function Brand({ compact = false }) {
  return (
    <Link
      className={`brand${compact ? " brand-small" : ""}`}
      to="/"
      aria-label="Lendora home"
    >
      <svg
        width="34"
        height="38"
        viewBox="0 0 38 42"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M16 3H5v28h13v-7h-6V10h4V3Z"
          fill="currentColor"
          transform="skewX(-18) translate(9)"
        />
        <path
          d="M24 11h-7v28h17v-8H24V11Z"
          fill="currentColor"
          opacity=".65"
          transform="skewX(-18) translate(9)"
        />
      </svg>
      <span>Lendora</span>
    </Link>
  );
}
