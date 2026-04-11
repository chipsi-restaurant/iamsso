import axios from 'axios'
import { getStoredToken, clearTokens } from './auth'

const api = axios.create({
  baseURL: 'http://localhost:8090',
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const url: string = error.config?.url || ''
    // Don't force logout for session endpoints — they depend on SSO cookie which may not be available
    const isSessionEndpoint = url.includes('/api/v1/sessions/')
    if (error.response?.status === 401 && !isSessionEndpoint) {
      clearTokens()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default api
