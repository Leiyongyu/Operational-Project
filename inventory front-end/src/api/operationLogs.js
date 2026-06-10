import { apiGet } from '@/api/request'

export function fetchOperationLogs({ page, size }) {
  return apiGet('/api/operation-logs', { page, size })
}
