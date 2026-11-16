const index = rows => new Map(rows.map(row => [String(row.id), row]));
export const cashAmount = row => Number(row.amount);
export const isCredit = row => cashAmount(row) > 0;
export const isDebit = row => cashAmount(row) < 0;
export const cashTotal = (rows, direction) => rows.filter(direction).reduce((sum, row) => sum + Math.abs(cashAmount(row)), 0);

// Ledger references identify different resources depending on the movement type.
// Never join unrelated payments merely because their IDs happen to match.
export function fundingRows(transactions, context) {
  const maps = Object.fromEntries(Object.entries(context).map(([name, rows]) => [name, index(rows)]));
  const find = (resource, id) => maps[resource]?.get(String(id));
  return transactions.map(row => {
    let loan, payment, order;
    const reference = row.reference;
    switch (row.type) {
      case 'COLLATERAL_PLEDGED': loan = find('loans', reference); break;
      case 'COLLATERAL_RELEASED': loan = find('loans', find('collateral', reference)?.loanId); break;
      case 'DEPOSIT_CONFIRMED':
      case 'PAYMENT_CASH_BLOCK':
      case 'PAYMENT_REFUNDED':
        payment = find('payments', reference);
        loan = find('loans', find('fees', payment?.feeId)?.loanId);
        break;
      case 'REBATE_RECEIVED': loan = find('loans', find('rebates', reference)?.loanId); break;
      case 'TRADE_BUY':
      case 'TRADE_SELL':
      case 'ORDER_CASH_RELEASE': order = find('orders', reference); break;
    }
    const agreement = find('agreements', loan?.mslaId);
    const security = find('securities', loan?.securityId ?? order?.securityId);
    return {
      ...row,
      lenderName: find('lenders', agreement?.lenderId)?.name ?? null,
      securitySymbol: security?.symbol ?? null,
      // A ledger movement is posted; the associated payment can still be pending.
      status: 'POSTED',
      paymentStatus: payment?.status ?? null,
      paymentType: payment?.type ?? null,
    };
  });
}
