import { useQuery } from '@tanstack/react-query'
import axios from 'axios'

interface ServiceHealth { name: string; port: number; status: 'up' | 'down' }

async function checkHealth(name: string, port: number): Promise<ServiceHealth> {
  try {
    await axios.get(`http://localhost:${port}/actuator/health`, { timeout: 3000 })
    return { name, port, status: 'up' }
  } catch {
    return { name, port, status: 'down' }
  }
}

export function useServiceHealth() {
  return useQuery({
    queryKey: ['health'],
    queryFn: () => Promise.all([
      checkHealth('Auth Service', 8080),
      checkHealth('User Service', 8081),
      checkHealth('Policy Service', 8082),
      checkHealth('API Gateway', 8090),
    ]),
    refetchInterval: 30000,
  })
}
