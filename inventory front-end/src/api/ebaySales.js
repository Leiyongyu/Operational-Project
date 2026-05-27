const BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

function getToken() {
  try {
    const raw = localStorage.getItem('inventory-auth-session')
    if (!raw) return ''
    const session = JSON.parse(raw)
    return session.token || ''
  } catch {
    return ''
  }
}

export async function uploadEbaySales(file) {
  const form = new FormData()
  form.append('file', file)

  const token = getToken()
  const resp = await fetch(`${BASE}/api/ebay-sales/upload`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  })
  const data = await resp.json()
  if (!resp.ok || (data.code !== 0 && data.code !== 200)) {
    throw new Error(data.message || '上传失败')
  }
  return data.data
}
