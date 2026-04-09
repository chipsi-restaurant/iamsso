import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export interface MfaFactor {
  id: string
  factorType: 'TOTP' | 'EMAIL_OTP'
  status: 'PENDING' | 'ACTIVE'
  displayName: string | null
  createdAt: string
}

export interface MfaEnrollment {
  factorId: string
  factorType: 'TOTP' | 'EMAIL_OTP'
  status: string
  secret: string | null
  provisioningUri: string | null
}

export interface MfaStatus {
  userId: string
  mfaEnabled: boolean
  activeFactors: string[]
}

export function useMfaFactors(userId: string) {
  return useQuery({
    queryKey: ['mfa', userId, 'factors'],
    queryFn: () => api.get<MfaFactor[]>(`/api/v1/mfa/${userId}/factors`).then(r => r.data),
    enabled: !!userId,
  })
}

export function useMfaStatus(userId: string) {
  return useQuery({
    queryKey: ['mfa', userId, 'status'],
    queryFn: () => api.get<MfaStatus>(`/api/v1/mfa/${userId}/status`).then(r => r.data),
    enabled: !!userId,
  })
}

export function useEnrollMfa(userId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { factorType: 'TOTP' | 'EMAIL_OTP'; displayName?: string }) =>
      api.post<MfaEnrollment>(`/api/v1/mfa/${userId}/factors`, data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mfa', userId] }),
  })
}

export function useConfirmMfa(userId: string, factorId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (code: string) =>
      api.post<MfaFactor>(`/api/v1/mfa/${userId}/factors/${factorId}/confirm`, { code }).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mfa', userId] }),
  })
}

export function useRemoveMfa(userId: string, factorId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.delete(`/api/v1/mfa/${userId}/factors/${factorId}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['mfa', userId] }),
  })
}
