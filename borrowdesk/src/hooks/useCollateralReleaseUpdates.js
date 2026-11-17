import { useEffect, useRef } from "react";
import { collateralApi } from "../api/collateralApi";
import { unwrapRows } from "../api/apiClient";

// Compare release state, not quote changes, before refreshing the heavier loan screens.
const signature = (positions) =>
  JSON.stringify(
    positions
      .map((p) => [
        p.id,
        p.status,
        p.cash_amount ?? p.cashAmount,
        p.quantity,
        p.requested_release_cash ?? p.requestedReleaseCash,
        p.requested_release_quantity ?? p.requestedReleaseQuantity,
        p.release_request_status ?? p.releaseRequestStatus,
        p.release_rejection_reason ?? p.releaseRejectionReason,
      ])
      .sort((a, b) => Number(a[0]) - Number(b[0])),
  );

export default function useCollateralReleaseUpdates(positions, onRefresh) {
  const latest = useRef({ positions, onRefresh });
  useEffect(() => {
    latest.current = { positions, onRefresh };
  }, [positions, onRefresh]);
  useEffect(() => {
    let stopped = false,
      checking = false;
    async function check() {
      if (document.hidden || checking) return;
      checking = true;
      try {
        const current = unwrapRows(await collateralApi.list());
        if (
          !stopped &&
          signature(current) !== signature(latest.current.positions)
        )
          await latest.current.onRefresh(false);
      } catch {
        /* Keep the visible data; check again when the connection recovers. */
      } finally {
        checking = false;
      }
    }
    const timer = window.setInterval(check, 5000);
    window.addEventListener("focus", check);
    document.addEventListener("visibilitychange", check);
    return () => {
      stopped = true;
      clearInterval(timer);
      window.removeEventListener("focus", check);
      document.removeEventListener("visibilitychange", check);
    };
  }, []);
}
