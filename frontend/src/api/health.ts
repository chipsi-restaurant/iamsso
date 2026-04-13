import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8090'

interface ServiceHealth { name: string; port: number; status: 'up' | 'down' }

async function checkHealth(name: string, path: string, port: number): Promise<ServiceHealth> {
  try {
    await axios.get(`${GATEWAY_URL}${path}`, { timeout: 3000 })
    return { name, port, status: 'up' }
  } catch {
    return { name, port, status: 'down' }
  }
}

export function useServiceHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => Promise.all([
      checkHealth('Auth Service', '/health/auth', 8080),
      checkHealth('User Service', '/health/user', 8081),
      checkHealth('Policy Service', '/health/policy', 8082),
      checkHealth('MFA Service', '/health/mfa', 8083),
      checkHealth('Notification Service', '/health/notification', 8084),
      checkHealth('Audit Service', '/health/audit', 8085),
      checkHealth('Session Service', '/health/session', 8086),
      checkHealth('API Gateway', '/actuator/health', 8090),
    ]),
    refetchInterval: 30000,
  })
}
