import { apiGet, apiPost } from '@/api/request'

export function fetchInventoryOverview(query) {
  return apiGet('/api/inventory-overview', query)
}

/** 仅从DB重算快照，不拉外部接口 */
export function refreshSnapshot() {
  return apiPost('/api/inventory-overview/refresh-snapshot')
}
