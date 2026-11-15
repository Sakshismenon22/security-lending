import { get, orgUrl, unwrapRows } from './apiClient'

async function list(resource) {
  const records = []
  let page = 0
  let result
  do {
    result = await get(orgUrl(`/${resource}?page=${page++}&size=200`))
    records.push(...unwrapRows(result))
  } while (page < (result.totalPages ?? 1))
  return records
}

export const reportsApi = {
  audit: () => list('audit'), loans: () => list('loans'),
  collateral: () => list('collateral'), marginCalls: () => list('margin-calls'),
  fees: () => list('fees'), returns: () => list('returns'), recalls: () => list('recalls'),
  agreements: () => list('agreements'), account: () => get(orgUrl('/account')),
}
