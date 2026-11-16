export function recallProgress(recall, returns) {
  const linkedReturns = returns.filter(row => String(row.recallId) === String(recall.id));
  const pendingQuantity = linkedReturns
    .filter(row => row.status === 'PENDING')
    .reduce((total, row) => total + Number(row.quantity || 0), 0);
  const remaining = Math.max(0, Number(recall.quantity || 0) - Number(recall.fulfilledQuantity || 0));
  return {
    linkedReturns,
    pendingQuantity,
    remaining,
    availableToRespond: Math.max(0, remaining - pendingQuantity),
  };
}
