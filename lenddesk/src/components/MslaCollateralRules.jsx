export default function MslaCollateralRules({
  rules,
  onChange,
  securities,
  loading,
  error,
  onRefresh,
  disabled,
}) {
  function update(index, patch) {
    onChange(
      rules.map((rule, i) => (i === index ? { ...rule, ...patch } : rule)),
    );
  }
  return (
    <fieldset className="msla-collateral-rules">
      <legend>Accepted collateral &amp; haircuts</legend>
      <p>
        Choose cash, specific securities, or both. Haircuts reduce eligible
        collateral value; cash has a fixed 0% haircut.
      </p>
      <div className="msla-rules-scroll">
        <table>
          <thead>
            <tr>
              <th>Collateral type</th>
              <th>Accepted asset</th>
              <th>Haircut (%)</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rules.map((rule, i) => (
              <tr key={rule.key}>
                <td>
                  <select
                    aria-label={`Collateral rule ${i + 1} type`}
                    disabled={disabled}
                    value={rule.assetType}
                    onChange={(event) =>
                      update(i, {
                        assetType: event.target.value,
                        securityId: "",
                        haircutPercent:
                          event.target.value === "CASH" ? "0" : "10",
                      })
                    }
                  >
                    <option value="CASH">Cash</option>
                    <option value="SECURITY">Security</option>
                  </select>
                </td>
                <td>
                  {rule.assetType === "CASH" ? (
                    <span>Cash (INR)</span>
                  ) : (
                    <select
                      aria-label={`Collateral rule ${i + 1} security`}
                      value={rule.securityId}
                      disabled={disabled || loading}
                      onChange={(event) =>
                        update(i, { securityId: event.target.value })
                      }
                      required
                    >
                      <option value="">Select a security</option>
                      {securities.map((security) => (
                        <option key={security.id} value={security.id}>
                          {security.symbol} · {security.name || security.id}
                        </option>
                      ))}
                    </select>
                  )}
                </td>
                <td>
                  <input
                    aria-label={`Collateral rule ${i + 1} haircut (%)`}
                    type="number"
                    min="0"
                    max="99.999999"
                    step="0.01"
                    disabled={disabled}
                    readOnly={rule.assetType === "CASH"}
                    value={rule.haircutPercent}
                    onChange={(event) =>
                      update(i, { haircutPercent: event.target.value })
                    }
                    required
                  />
                </td>
                <td>
                  <button
                    type="button"
                    aria-label={`Remove collateral rule ${i + 1}`}
                    disabled={disabled}
                    onClick={() =>
                      onChange(rules.filter((_, index) => index !== i))
                    }
                  >
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="msla-rules-actions">
        <button
          type="button"
          disabled={disabled}
          onClick={() =>
            onChange([
              ...rules,
              {
                key: crypto.randomUUID(),
                assetType: "SECURITY",
                securityId: "",
                haircutPercent: "10",
              },
            ])
          }
        >
          + Add collateral rule
        </button>
        <button
          type="button"
          disabled={disabled || loading}
          onClick={onRefresh}
        >
          {loading ? "Loading securities…" : "Refresh securities"}
        </button>
      </div>
      {error && (
        <p className="live-error" role="alert">
          {error}
        </p>
      )}
      {!loading && !error && !securities.length && (
        <p>
          No securities loaded. Import security references in Administration
          before choosing security collateral.
        </p>
      )}
    </fieldset>
  );
}
