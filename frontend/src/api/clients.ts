import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export interface OAuthClient { clientId: string; clientName: string; grantTypes: string[]; redirectUris: string[]; scopes: string[]; tokenEndpointAuthMethod: string; accessTokenTtlSeconds: number; refreshTokenTtlSeconds: number; createdAt: string; clientSecret?: string }

export function useClients() { return useQuery({ queryKey: ['clients'], queryFn: () => api.get<OAuthClient[]>('/api/v1/clients').then(r => r.data) }) }
export function useCreateClient() { const qc = useQueryClient(); return useMutation({ mutationFn: (data: { clientName: string; grantTypes: string[]; redirectUris: string[]; scopes?: string[] }) => api.post<OAuthClient>('/api/v1/clients', data).then(r => r.data), onSuccess: () => qc.invalidateQueries({ queryKey: ['clients'] }) }) }
export function useRotateSecret(clientId: string) { const qc = useQueryClient(); return useMutation({ mutationFn: () => api.post<OAuthClient>(`/api/v1/clients/${clientId}/secret/rotate`).then(r => r.data), onSuccess: () => qc.invalidateQueries({ queryKey: ['clients'] }) }) }
export function useDeleteClient(clientId: string) { const qc = useQueryClient(); return useMutation({ mutationFn: () => api.delete(`/api/v1/clients/${clientId}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['clients'] }) }) }
