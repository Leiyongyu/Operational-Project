import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

function buildUrl(path, query) {
  const url = API_BASE_URL ? new URL(`${API_BASE_URL}${path}`) : new URL(path, window.location.origin)

  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value === undefined || value === null || value === '') {
        return
      }
      url.searchParams.set(key, String(value))
    })
  }

  return url.toString()
}

export async function apiRequest({ path, method = 'GET', query, body, auth = true }) {
  const authStore = useAuthStore()
  const requestMethod = method.toUpperCase()
  const url = buildUrl(path, query)
  const headers = {}
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData

  if (auth) {
    const token = authStore.token
    const tokenType = authStore.tokenType || 'Bearer'

    if (!token) {
      await router.push('/login')
      throw new Error('请先登录')
    }

    headers.Authorization = `${tokenType} ${token}`
  }

  if (body !== undefined && !isFormData) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(url, {
    method: requestMethod,
    headers,
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
  })

  const responseText = await response.text()
  let responseData = null

  if (responseText) {
    try {
      responseData = JSON.parse(responseText)
    } catch {
      responseData = { message: responseText }
    }
  }

  if (response.status === 401) {
    await authStore.logout()
    await router.push('/login')
    throw new Error(responseData?.message || '登录已失效，请重新登录')
  }

  if (!response.ok) {
    throw new Error(responseData?.message || `请求失败，状态码：${response.status}`)
  }

  if (responseData && typeof responseData === 'object' && 'code' in responseData) {
    const codeValue = typeof responseData.code === 'number' ? responseData.code : Number(responseData.code)
    const isSuccess = codeValue === 0 || codeValue === 200

    if (!isSuccess) {
      throw new Error(responseData.message || '请求失败')
    }

    return responseData.data
  }

  return responseData
}

export function apiGet(path, query, options) {
  return apiRequest({ path, method: 'GET', query, ...options })
}

export function apiPost(path, body, options) {
  return apiRequest({ path, method: 'POST', body, ...options })
}

export function apiPut(path, body, options) {
  return apiRequest({ path, method: 'PUT', body, ...options })
}

export function apiDelete(path, options) {
  return apiRequest({ path, method: 'DELETE', ...options })
}
