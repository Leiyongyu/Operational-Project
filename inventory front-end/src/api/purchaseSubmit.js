import { apiDelete, apiGet, apiPost, apiPut } from '@/api/request'

/** 分页查询采购计划提交记录 */
export function fetchSubmitPage({ page, size, sku, creator }) {
  return apiGet('/api/purchase-plan-submit', { page, size, sku, creator })
}

/** 批量提交采购计划（仅保存到库，不调领星） */
export function submitPlans(items) {
  return apiPost('/api/purchase-plan-submit', items)
}

/** 删除单条 */
export function deleteSubmit(id) {
  return apiDelete(`/api/purchase-plan-submit/${id}`)
}

/** 更新采购计划 */
export function updateSubmit(id, data) {
  return apiPut(`/api/purchase-plan-submit/${id}`, data)
}

/** 批量更新状态 */
export function batchUpdateStatus(ids, status) {
  return apiPut('/api/purchase-plan-submit/batch-status', { ids, status })
}

/** 导出 Excel */
export async function exportExcel() {
  let token = ''
  try {
    const session = JSON.parse(localStorage.getItem('inventory-auth-session'))
    token = session?.token || ''
  } catch { /* ignore */ }
  const base = import.meta.env.VITE_API_BASE_URL || window.location.origin
  const resp = await fetch(`${base}/api/purchase-plan-submit/export`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok) throw new Error('导出失败')
  const blob = await resp.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '采购计划导出.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}
