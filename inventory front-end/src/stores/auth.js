import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'inventory-auth-session'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

function getStoredSession() {
  const rawValue = localStorage.getItem(STORAGE_KEY)

  if (!rawValue) {
    return null
  }

  try {
    return JSON.parse(rawValue)
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}

function isSessionExpired(session) {
  if (!session?.expiresAtMillis) {
    return false
  }

  return Date.now() >= Number(session.expiresAtMillis)
}

function normalizeRole(role) {
  if (!role) {
    return ''
  }

  if (role === '管理员') {
    return 'admin'
  }

  if (role === '用户') {
    return 'user'
  }

  const normalized = String(role).trim().toLowerCase()

  if (normalized === 'admin') {
    return 'admin'
  }

  if (normalized === 'user' || normalized === 'viewer') {
    return 'user'
  }

  return String(role).trim()
}

export const useAuthStore = defineStore('auth', () => {
  const storedSession = getStoredSession()
  const session = ref(storedSession && !isSessionExpired(storedSession) ? storedSession : null)

  if (storedSession && !session.value) {
    localStorage.removeItem(STORAGE_KEY)
  }

  const user = computed(() => (session.value ? session.value.user : null))
  const token = computed(() => (session.value ? session.value.token : ''))
  const tokenType = computed(() => (session.value ? session.value.tokenType : 'Bearer'))
  const isLoggedIn = computed(() => Boolean(token.value) && !isSessionExpired(session.value))
  const isAdmin = computed(() => normalizeRole(user.value?.role) === 'admin')
  const ownerName = computed(() => session.value?.user?.ownerName || session.value?.user?.account || '')

  async function login(account, password) {
    const payload = {
      account: account.trim(),
      password,
    }

    try {
      const response = await fetch(`${API_BASE_URL}/api/user/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(payload),
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

      if (!response.ok) {
        return {
          success: false,
          message: responseData?.message || `登录失败，状态码：${response.status}`,
        }
      }

      if (!responseData || responseData.code !== 0) {
        return {
          success: false,
          message: responseData?.message || '登录失败，请检查账号密码。',
        }
      }

      const tokenValue = responseData.data?.token

      if (!tokenValue) {
        return {
          success: false,
          message: '登录失败，未获取到 token。',
        }
      }

      const accountValue = responseData.data?.account || payload.account
      const roleValue = normalizeRole(responseData.data?.role)

      const nextSession = {
        token: tokenValue,
        tokenType: responseData.data?.tokenType || 'Bearer',
        expiresAtMillis: responseData.data?.expiresAtMillis || null,
        user: {
          account: accountValue,
          name: accountValue,
          role: roleValue,
          ownerName: responseData.data?.ownerName || '',
        },
      }

      session.value = nextSession
      localStorage.setItem(STORAGE_KEY, JSON.stringify(nextSession))

      return {
        success: true,
        message: '登录成功',
      }
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '登录失败，请检查后端服务是否启动。'

      return { success: false, message }
    }
  }

  async function logout() {
    const currentToken = token.value
    const currentTokenType = tokenType.value || 'Bearer'

    session.value = null
    localStorage.removeItem(STORAGE_KEY)

    if (!currentToken) {
      return
    }

    try {
      await fetch(`${API_BASE_URL}/api/user/logout`, {
        method: 'POST',
        headers: {
          Authorization: `${currentTokenType} ${currentToken}`,
        },
      })
    } catch {
      return
    }
  }

  return {
    user,
    isLoggedIn,
    isAdmin,
    ownerName,
    token,
    tokenType,
    login,
    logout,
  }
})
