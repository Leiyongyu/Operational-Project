import { apiGet, apiPost } from '@/api/request'

/** 获取当前用户 dashboard 列配置，后端无数据返回 null */
export async function loadColumnConfig(pageKey = 'dashboard') {
  try {
    const json = await apiGet('/api/user-column-config', { pageKey })
    if (json && typeof json === 'string') {
      const arr = JSON.parse(json)
      if (Array.isArray(arr) && arr.length > 0) return arr
    }
    return null
  } catch {
    return null
  }
}

/** 保存当前用户 dashboard 列配置到后端 */
export async function saveColumnConfig(keys, pageKey = 'dashboard') {
  try {
    await apiPost('/api/user-column-config', JSON.stringify(keys), { query: { pageKey } })
    return true
  } catch {
    return false
  }
}
