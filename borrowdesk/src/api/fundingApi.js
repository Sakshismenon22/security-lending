import { get, post, orgUrl, unwrapRows } from './apiClient';
import { fundingRows } from '../utils/fundingRows';
const b = orgUrl('');
async function list(resource) {
  const records = [];
  let page = 0, result;
  do {
    result = await get(`${b}/${resource}?page=${page++}&size=200`);
    records.push(...unwrapRows(result));
  } while (page < (result?.totalPages ?? 1));
  return records;
}
export const fundingApi = {
  account: () => get(b + '/account'),
  transactions: () => list('transactions'),
  payments: () => list('payments'),
  audit: () => list('audit'),
  async snapshot() {
    const resources = ['payments', 'audit', 'loans', 'collateral', 'fees', 'rebates', 'orders', 'agreements', 'lenders', 'securities'];
    const [account, transactions, results] = await Promise.all([
      this.account(), this.transactions(),
      Promise.allSettled(resources.map(name => name === 'securities' ? get('/api/v1/securities').then(unwrapRows) : list(name))),
    ]);
    const context = Object.fromEntries(results.map((result, i) => [resources[i], result.status === 'fulfilled' ? result.value : []]));
    return {
      account, transactions: fundingRows(transactions, context),
      payments: context.payments, audit: context.audit,
      missing: results.flatMap((result, i) => result.status === 'rejected' ? [resources[i]] : []),
    };
  },
  deposit: x => post(b + '/deposits', x),
  withdraw: x => post(b + '/withdrawals', x),
  approveDeposit: (id, x) => post(b + '/deposits/' + id + '/approve', x),
  statement: () => get(b + '/statement.csv'),
};
