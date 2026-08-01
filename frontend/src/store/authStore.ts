import { create } from 'zustand'
import api from '@/lib/api'

interface User {
  id: number
  email: string
  name: string
}

interface AuthState {
  user: User | null
  accessToken: string | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, name: string, teamName: string) => Promise<void>
  logout: () => Promise<void>
  loadUser: () => Promise<void>
  setTokens: (accessToken: string, refreshToken: string) => void
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: localStorage.getItem('accessToken'),
  isAuthenticated: !!localStorage.getItem('accessToken'),
  isLoading: true,

  setTokens: (accessToken: string, refreshToken: string) => {
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    set({ accessToken, isAuthenticated: true })
  },

  login: async (email: string, password: string) => {
    const res = await api.post('/auth/login', { email, password })
    const { accessToken, refreshToken, userId, email: userEmail, name } = res.data.data
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    set({
      accessToken,
      isAuthenticated: true,
      user: { id: userId, email: userEmail, name },
    })
  },

  register: async (email: string, password: string, name: string, teamName: string) => {
    const res = await api.post('/auth/register', { email, password, name, teamName })
    const { accessToken, refreshToken, userId, email: userEmail, name: userName } = res.data.data
    localStorage.setItem('accessToken', accessToken)
    localStorage.setItem('refreshToken', refreshToken)
    set({
      accessToken,
      isAuthenticated: true,
      user: { id: userId, email: userEmail, name: userName },
    })
  },

  logout: async () => {
    try {
      const refreshToken = localStorage.getItem('refreshToken')
      if (refreshToken) {
        await api.post('/auth/logout', { refreshToken })
      }
    } catch {
      // ignore
    }
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    set({ user: null, accessToken: null, isAuthenticated: false })
  },

  loadUser: async () => {
    try {
      const token = localStorage.getItem('accessToken')
      if (!token) {
        set({ isLoading: false })
        return
      }
      const res = await api.get('/users/me')
      set({ user: res.data.data, isAuthenticated: true, isLoading: false })
    } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      set({ user: null, isAuthenticated: false, isLoading: false })
    }
  },
}))
