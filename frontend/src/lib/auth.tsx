import * as React from 'react'
import { api, clearSession, getAccessToken, getUser, setSession, setUser, type AuthUser } from './api'

interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, name: string, teamName?: string) => Promise<void>
  logout: () => void
  refreshUser: () => Promise<void>
  updateUser: (user: AuthUser) => void
}

const AuthContext = React.createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUserState] = React.useState<AuthUser | null>(() =>
    getAccessToken() ? getUser() : null
  )
  const [loading, setLoading] = React.useState(false)

  const refreshUser = React.useCallback(async () => {
    if (!getAccessToken()) return
    try {
      const me = await api.get<AuthUser>('/auth/me')
      setUser(me)
      setUserState(me)
    } catch {
      clearSession()
      setUserState(null)
    }
  }, [])

  const login = React.useCallback(async (email: string, password: string) => {
    setLoading(true)
    try {
      const res = await api.post<{
        accessToken: string
        refreshToken: string
        user: AuthUser
      }>('/auth/login', { email, password })
      setSession(res.accessToken, res.refreshToken, res.user)
      setUserState(res.user)
    } finally {
      setLoading(false)
    }
  }, [])

  const register = React.useCallback(
    async (email: string, password: string, name: string, teamName?: string) => {
      setLoading(true)
      try {
        const res = await api.post<{
          accessToken: string
          refreshToken: string
          user: AuthUser
        }>('/auth/register', { email, password, name, teamName })
        setSession(res.accessToken, res.refreshToken, res.user)
        setUserState(res.user)
      } finally {
        setLoading(false)
      }
    },
    []
  )

  const logout = React.useCallback(() => {
    const refresh = localStorage.getItem('podcast_refresh_token')
    if (refresh) api.post('/auth/logout', { refreshToken: refresh }).catch(() => {})
    clearSession()
    setUserState(null)
  }, [])

  const updateUser = React.useCallback((u: AuthUser) => {
    setUser(u)
    setUserState(u)
  }, [])

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, refreshUser, updateUser }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = React.useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
