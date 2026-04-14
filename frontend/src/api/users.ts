import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export interface User {
  id: string
  email: string | null
  username: string | null
  displayName: string | null
  role: string
  status: string
  emailVerified: boolean
  mfaEnabled: boolean
  locale: string
  createdAt: string
  updatedAt: string
}

export interface UserProfile {
  userId: string
  displayName: string | null
  firstName: string | null
  lastName: string | null
  avatarUrl: string | null
  timezone: string | null
  locale: string | null
  updatedAt: string
}

export function useUsers(page = 0, size = 20) {
  return useQuery({
    queryKey: ['users', page, size],
    queryFn: () => api.get<{ content: User[]; totalElements: number }>(`/api/v1/users?page=${page}&size=${size}`).then(r => r.data),
  })
}

export function useUser(id: string) {
  return useQuery({
    queryKey: ['users', id],
    queryFn: () => api.get<User>(`/api/v1/users/${id}`).then(r => r.data),
    enabled: !!id,
  })
}

export function useUserProfile(id: string) {
  return useQuery({
    queryKey: ['users', id, 'profile'],
    queryFn: () => api.get<UserProfile>(`/api/v1/users/${id}/profile`).then(r => r.data),
    enabled: !!id,
  })
}

export function useCreateUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: {
      email: string
      password: string
      username?: string
      displayName?: string
      role?: 'user' | 'admin' | 'moderator'
      activate?: boolean
    }) =>
      api.post<User>('/api/v1/users', data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}

export function useUpdateUser(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.patch<User>(`/api/v1/users/${id}`, data).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      qc.invalidateQueries({ queryKey: ['users', id] })
    },
  })
}

export function useChangeStatus(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: { status: string; reason: string }) =>
      api.put<User>(`/api/v1/users/${id}/status`, data).then(r => r.data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] })
      qc.invalidateQueries({ queryKey: ['users', id] })
    },
  })
}

export function useDeleteUser(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api.delete(`/api/v1/users/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
  })
}
