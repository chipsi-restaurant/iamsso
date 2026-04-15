import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'

export type VacationRequestType = 'VACATION' | 'SICK_LEAVE' | 'BUSINESS_TRIP' | 'OTHER'
export type VacationRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export interface VacationRequest {
  id: string
  userId: string
  type: VacationRequestType
  startDate: string
  endDate: string
  reason: string
  status: VacationRequestStatus
  reviewerId: string | null
  reviewComment: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function useMyRequests(page = 0, size = 20) {
  return useQuery({
    queryKey: ['vacation', 'my', page, size],
    queryFn: () =>
      api.get<PagedResponse<VacationRequest>>(`/api/v1/vacation-requests/my?page=${page}&size=${size}`)
        .then(r => r.data),
  })
}

export function useAllRequests(status: VacationRequestStatus | undefined, page = 0, size = 20) {
  return useQuery({
    queryKey: ['vacation', 'all', status, page, size],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (status) params.set('status', status)
      return api.get<PagedResponse<VacationRequest>>(`/api/v1/vacation-requests?${params}`)
        .then(r => r.data)
    },
  })
}

export function useCreateRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: {
      type: VacationRequestType
      startDate: string
      endDate: string
      reason: string
    }) => api.post<VacationRequest>('/api/v1/vacation-requests', data).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['vacation'] }),
  })
}

export function useApproveRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: string; comment?: string }) =>
      api.post<VacationRequest>(`/api/v1/vacation-requests/${id}/approve`, { comment })
        .then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['vacation'] }),
  })
}

export function useRejectRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: string; comment?: string }) =>
      api.post<VacationRequest>(`/api/v1/vacation-requests/${id}/reject`, { comment })
        .then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['vacation'] }),
  })
}

export function useCancelRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      api.post<VacationRequest>(`/api/v1/vacation-requests/${id}/cancel`).then(r => r.data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['vacation'] }),
  })
}
