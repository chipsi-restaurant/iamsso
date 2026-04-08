import axios from 'axios'
import { getStoredToken, clearTokens } from './auth'

const api = axios.create({
  baseURL: 'http://localhost:8090',
  headers: { 'Content-Type': 'application/json' },
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
    if (error.response?.status === 401) {
      clearTokens()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default api
