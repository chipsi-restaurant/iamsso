import { useQuery, useMutation } from '@tanstack/react-query'
import api from '@/lib/api'

export interface Session { sessionId: string; userId: string; clientIds: string[]; createdAt: string; lastActivityAt: string; expiresAt: string }

export function useCurrentSession() { return useQuery({ queryKey: ['session'], queryFn: () => api.get<Session>('/api/v1/sessions/current').then(r => r.data), retry: false }) }
export function useLogout() { return useMutation({ mutationFn: () => api.post('/api/v1/sessions/logout') }) }
export function useLogoutAll() { return useMutation({ mutationFn: () => api.post('/api/v1/sessions/logout-all') }) }
