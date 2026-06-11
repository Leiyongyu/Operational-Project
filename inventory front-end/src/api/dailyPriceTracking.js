import { apiGet, apiPost } from '@/api/request'

/** 分页查询每日跟价 */
export function fetchDailyPriceTracking({ page, size, site, sku, brand, operator }) {
  return apiGet('/api/daily-price-tracking', { page, size, site, sku, brand, operator })
}

/** 保存或更新备注（按 site+sku upsert） */
export function saveRemark(site, sku, remark) {
  return apiPost('/api/daily-price-tracking/remark', { site, sku, remark })
}

/** 刷新每日跟价快照（全量重算） */
export function refreshDailyPriceTrackingSnapshot() {
  return apiPost('/api/daily-price-tracking/refresh-table')
}

/** 保存或更新 OE 号 */
export function saveOe(site, sku, oeNumber) {
  return apiPost('/api/daily-price-tracking/oe', { site, sku, oeNumber })
}

/** 导入最低价 Excel */
export function importLowestPrice(file) {
  const form = new FormData()
  form.append('file', file)
  return apiPost('/api/daily-price-tracking/import-lowest-price', form)
}

/** 上传 Excel 导入 */
export function uploadDailyPriceTracking(file) {
  const form = new FormData()
  form.append('file', file)
  return apiPost('/api/daily-price-tracking/upload', form)
}

/** 导出 Excel */
export async function exportDailyPriceTracking({ site, sku, brand, operator, ids } = {}) {
  let token = ''
  try {
    const session = JSON.parse(localStorage.getItem('inventory-auth-session'))
    token = session?.token || ''
  } catch { /* ignore */ }
  const base = import.meta.env.VITE_API_BASE_URL || window.location.origin
  const params = new URLSearchParams()
  if (site) params.set('site', site)
  if (sku) params.set('sku', sku)
  if (brand) params.set('brand', brand)
  if (operator) params.set('operator', operator)
  if (ids) params.set('ids', ids)
  const qs = params.toString()
  const url = `${base}/api/daily-price-tracking/export${qs ? '?' + qs : ''}`
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!resp.ok) throw new Error('导出失败')
  const blob = await resp.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = '每日跟价导出.xlsx'
  a.click()
}
