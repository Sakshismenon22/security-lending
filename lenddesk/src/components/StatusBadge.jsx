export default function StatusBadge({ children, tone = 'neutral' }) {
  return <span className={`badge ${tone}`}>{children}</span>
}
