import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'

export interface AuditEvent {
  id: string
  eventType: string
  eventSource: string
  userId: string | null
  clientId: string | null
  sessionId: string | null
  ipAddress: string | null
  details: Record<string, unknown> | null
  timestamp: string
}

export interface AuditStats {
  total: number
  byType: Record<string, number>
  bySource: Record<string, number>
}

export interface AuditFilters {
  userId?: string
  eventType?: string
  eventSource?: string
  from?: string
  to?: string
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function useAuditEvents(filters: AuditFilters, page = 0, size = 50) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (filters.userId && UUID_RE.test(filters.userId)) params.set('userId', filters.userId)
  if (filters.eventType) params.set('eventType', filters.eventType)
  if (filters.eventSource) params.set('eventSource', filters.eventSource)
  if (filters.from) params.set('from', new Date(filters.from).toISOString())
  if (filters.to) params.set('to', new Date(filters.to).toISOString())

  return useQuery({
    queryKey: ['audit', 'events', filters, page, size],
    queryFn: () => api.get<{ content: AuditEvent[]; totalElements: number }>(`/api/v1/audit/events?${params}`).then(r => r.data),
  })
}

export function useAuditStats(from?: string, to?: string) {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  return useQuery({
    queryKey: ['audit', 'stats', from, to],
    queryFn: () => api.get<AuditStats>(`/api/v1/audit/stats?${params}`).then(r => r.data),
  })
}
