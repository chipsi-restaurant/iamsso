import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { getStoredToken, parseJwt, clearTokens } from './auth'

interface User {
  sub: string
  role: string
  scope: string
  email?: string
}

interface AuthContextType {
  token: string | null
  user: User | null
  isAuthenticated: boolean
  setToken: (token: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextType | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(getStoredToken)

  const user = token ? (parseJwt(token) as unknown as User) : null
  const isAuthenticated = !!token

  const setToken = (newToken: string) => {
    localStorage.setItem('access_token', newToken)
    setTokenState(newToken)
  }

  const logout = () => {
    clearTokens()
    setTokenState(null)
    // Redirect через Gateway к auth-service logout для очистки SSO cookie
    window.location.href = 'http://localhost:8090/api/v1/sessions/logout?post_logout_redirect_uri=http://localhost:3000/login'
  }

  useEffect(() => {
    if (token) {
      try {
        const claims = parseJwt(token)
        const exp = claims.exp as number
        if (exp * 1000 < Date.now()) {
          logout()
        }
      } catch {
        logout()
      }
    }
  }, [])

  return (
    <AuthContext.Provider value={{ token, user, isAuthenticated, setToken, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
