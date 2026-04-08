import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export interface Role { id: string; name: string; description: string | null; createdAt: string; updatedAt: string }
export interface PolicyCondition { field: string; operator: string; value: string }
export interface Policy { id: string; name: string; description: string | null; role: string; effect: 'ALLOW' | 'DENY'; action: string; resourcePattern: string; conditions: PolicyCondition[] | null; priority: number; enabled: boolean; createdAt: string; updatedAt: string }
export interface EvaluateResult { allowed: boolean; policyId: string | null; reason: string }

export function useRoles() { return useQuery({ queryKey: ['roles'], queryFn: () => api.get<Role[]>('/api/v1/roles').then(r => r.data) }) }
export function useCreateRole() { const qc = useQueryClient(); return useMutation({ mutationFn: (data: { name: string; description?: string }) => api.post<Role>('/api/v1/roles', data).then(r => r.data), onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }) }) }
export function useDeleteRole(id: string) { const qc = useQueryClient(); return useMutation({ mutationFn: () => api.delete(`/api/v1/roles/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['roles'] }) }) }
export function usePolicies(page = 0, size = 50) { return useQuery({ queryKey: ['policies', page, size], queryFn: () => api.get<{ content: Policy[]; totalElements: number }>(`/api/v1/policies?page=${page}&size=${size}`).then(r => r.data) }) }
export function useCreatePolicy() { const qc = useQueryClient(); return useMutation({ mutationFn: (data: Omit<Policy, 'id' | 'createdAt' | 'updatedAt'>) => api.post<Policy>('/api/v1/policies', data).then(r => r.data), onSuccess: () => qc.invalidateQueries({ queryKey: ['policies'] }) }) }
export function useUpdatePolicy(id: string) { const qc = useQueryClient(); return useMutation({ mutationFn: (data: Partial<Policy>) => api.put<Policy>(`/api/v1/policies/${id}`, data).then(r => r.data), onSuccess: () => qc.invalidateQueries({ queryKey: ['policies'] }) }) }
export function useDeletePolicy(id: string) { const qc = useQueryClient(); return useMutation({ mutationFn: () => api.delete(`/api/v1/policies/${id}`), onSuccess: () => qc.invalidateQueries({ queryKey: ['policies'] }) }) }
export function useEvaluatePolicy() { return useMutation({ mutationFn: (data: { subject: { userId: string; role: string }; action: string; resource: string; context?: Record<string, string> }) => api.post<EvaluateResult>('/api/v1/policy/evaluate', data).then(r => r.data) }) }
