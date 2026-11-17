import Icon from "../common/Icon";
export default function ConnectionIllustration() {
  return (
    <div className="connection-illustration" aria-hidden="true">
      <div className="connection-lines">
        <i />
        <i />
        <i />
        <i />
      </div>
      <div className="document-card agreement-card">
        <header>
          <Icon name="handshake" size={28} />
          <b>Agreement</b>
        </header>
        <span />
        <span />
        <span />
        <small>Signature</small>
      </div>
      <div className="document-card loan-card">
        <header>
          <Icon name="coins" size={28} />
          <b>Loan</b>
        </header>
        <span />
        <span />
        <span />
        <span />
      </div>
      <div className="securities-card">
        <header>
          <Icon name="grid" />
          <b>Securities</b>
        </header>
        {["AAPL", "MSFT", "AMZN", "NVDA"].map((symbol) => (
          <div key={symbol}>
            {symbol}
            <span />
            <i />
          </div>
        ))}
      </div>
    </div>
  );
}
