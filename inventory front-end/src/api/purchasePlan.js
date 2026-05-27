import { apiGet, apiPost } from '@/api/request'

export async function uploadPurchasePlan(file) {
  const form = new FormData()
  form.append('file', file)
  return apiPost('/api/purchase-plan/upload', form)
}

export async function searchSkus(keyword) {
  const res = await apiGet('/api/purchase-plan/skus', { keyword: keyword || '' })
  return Array.isArray(res) ? res : []
}

export async function searchStores(keyword) {
  const res = await apiGet('/api/purchase-plan/stores', { keyword: keyword || '' })
  return Array.isArray(res) ? res : []
}

export async function searchWarehouses(keyword) {
  const res = await apiGet('/api/purchase-plan/warehouses', { keyword: keyword || '' })
  return Array.isArray(res) ? res : []
}

export async function createPurchasePlan(items) {
  return apiPost('/api/purchase-plan/create', items)
}
