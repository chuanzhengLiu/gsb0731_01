const TOKEN_KEY = 'podcast_access_token'
const REFRESH_KEY = 'podcast_refresh_token'
const USER_KEY = 'podcast_user'

export interface AuthUser {
  id: number
  email: string
  name: string
  activeTeamId: number | null
  roleInTeam: string | null
}

export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY)
}

export function getUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? JSON.parse(raw) : null
}

export function setSession(access: string, refresh: string, user: AuthUser) {
  localStorage.setItem(TOKEN_KEY, access)
  localStorage.setItem(REFRESH_KEY, refresh)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function setUser(user: AuthUser) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(USER_KEY)
}

const API_BASE = '/api'

export class ApiError extends Error {
  status: number
  payload: unknown
  constructor(status: number, message: string, payload?: unknown) {
    super(message)
    this.status = status
    this.payload = payload
  }
}

interface RequestOptions {
  method?: string
  body?: unknown
  form?: FormData
  signal?: AbortSignal
}

async function request<T>(path: string, options: RequestOptions = {}, retry = true): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getAccessToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

  let body: BodyInit | undefined
  if (options.form) {
    body = options.form
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const res = await fetch(`${API_BASE}${path}`, {
    method: options.method || 'GET',
    headers,
    body,
    signal: options.signal,
  })

  if (res.status === 401 && retry && getRefreshToken()) {
    const ok = await tryRefresh()
    if (ok) return request<T>(path, options, false)
  }

  const contentType = res.headers.get('content-type') || ''
  const data = contentType.includes('application/json') ? await res.json() : await res.text()

  if (!res.ok) {
    const message =
      (data && typeof data === 'object' && 'message' in data && (data as { message: string }).message) ||
      `Request failed (${res.status})`
    throw new ApiError(res.status, message, data)
  }
  return data as T
}

async function tryRefresh(): Promise<boolean> {
  const refresh = getRefreshToken()
  if (!refresh) return false
  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
    })
    if (!res.ok) {
      clearSession()
      return false
    }
    const data = (await res.json()) as {
      accessToken: string
      refreshToken: string
      user: AuthUser
    }
    setSession(data.accessToken, data.refreshToken, data.user)
    return true
  } catch {
    clearSession()
    return false
  }
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  postForm: <T>(path: string, form: FormData) => request<T>(path, { method: 'POST', form }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
