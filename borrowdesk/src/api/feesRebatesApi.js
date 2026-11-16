import { request, orgUrl, unwrapRows } from './apiClient';

async function list(path, signal) {
  const records = [];
  let page = 0;
  let result;
  do {
    result = await request(`${path}?page=${page++}&size=200`, { signal });
    records.push(...unwrapRows(result));
  } while (page < (result?.totalPages ?? 1));
  return records;
}

export const feesRebatesApi = {
  async load(signal) {
    const resources = {
      accruals: orgUrl('/accruals'), fees: orgUrl('/fees'),
      rebates: orgUrl('/rebates'), payments: orgUrl('/rebate-payments'), feePayments: orgUrl('/payments'),
      loans: orgUrl('/loans'), agreements: orgUrl('/agreements'),
      lenders: orgUrl('/lenders'), securities: '/api/v1/securities',
    };
    const entries = await Promise.all(Object.entries(resources).map(async ([name, path]) => [name, await list(path, signal)]));
    return { ...Object.fromEntries(entries), audit: [] };
  },
};
