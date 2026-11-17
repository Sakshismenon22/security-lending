import "./CollateralReleaseRequest.css";

export default function CollateralReleaseStatus({ positions = [] }) {
  const decisions = positions.filter(
    (p) =>
      p.status === "RELEASE_REQUESTED" ||
      (p.release_request_status ?? p.releaseRequestStatus) === "REJECTED",
  );
  if (!decisions.length) return null;
  return (
    <div className="collateral-release-status" role="status" aria-live="polite">
      {decisions.map((p) => (
        <p key={p.id}>
          {p.status === "RELEASE_REQUESTED"
            ? `Lot ${p.id}: collateral release awaiting lender decision.`
            : `Lot ${p.id}: collateral release rejected — ${p.release_rejection_reason ?? p.releaseRejectionReason ?? "Rejected by lender"}. Collateral remains pledged. You can submit a new request.`}
        </p>
      ))}
    </div>
  );
}
