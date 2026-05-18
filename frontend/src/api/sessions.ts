import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export interface Session { sessionId: string; userId: string; clientIds: string[]; createdAt: string; lastActivityAt: string; expiresAt: string }

export function useCurrentSession() { return useQuery({ queryKey: ['session'], queryFn: () => api.get<Session>('/api/v1/sessions/current').then(r => r.data), retry: false }) }
export function useMySessions() { return useQuery({ queryKey: ['sessions', 'my'], queryFn: () => api.get<Session[]>('/api/v1/sessions/my').then(r => r.data), retry: false }) }
export function useLogout() { return useMutation({ mutationFn: () => api.post('/api/v1/sessions/logout') }) }
export function useLogoutAll() { return useMutation({ mutationFn: () => api.post('/api/v1/sessions/logout-all') }) }
export function useRevokeSession() {
    const qc = useQueryClient()
    return useMutation({
        mutationFn: (sessionId: string) => api.delete(`/api/v1/sessions/${sessionId}`),
        onSuccess: () => qc.invalidateQueries({ queryKey: ['sessions', 'my'] }),
    })
}
