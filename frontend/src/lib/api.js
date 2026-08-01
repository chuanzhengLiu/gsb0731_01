import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// access_token 过期时自动用 refresh_token 换新
let refreshing = null
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config
    if (error.response?.status === 401 && !original._retried) {
      const refreshToken = localStorage.getItem('refresh_token')
      if (refreshToken) {
        original._retried = true
        try {
          refreshing = refreshing || api.post('/auth/refresh', { refreshToken })
          const { data } = await refreshing
          refreshing = null
          localStorage.setItem('access_token', data.accessToken)
          localStorage.setItem('refresh_token', data.refreshToken)
          localStorage.setItem('user', JSON.stringify(data.user))
          return api(original)
        } catch (e) {
          refreshing = null
          localStorage.clear()
          window.location.href = '/login'
        }
      }
    }
    return Promise.reject(error)
  },
)

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem('user'))
  } catch {
    return null
  }
}

export function saveAuth(data) {
  localStorage.setItem('access_token', data.accessToken)
  localStorage.setItem('refresh_token', data.refreshToken)
  localStorage.setItem('user', JSON.stringify(data.user))
}

export function logout() {
  const refreshToken = localStorage.getItem('refresh_token')
  if (refreshToken) {
    api.post('/auth/logout', { refreshToken }).catch(() => {})
  }
  localStorage.clear()
  window.location.href = '/login'
}

export default api
