import { useState } from 'react'
import { Eye, X, RotateCcw } from 'lucide-react'
import { useAuditEvents, type AuditFilters, type AuditEvent } from '@/api/audit'

const PAGE_SIZE = 50

const SERVICE_OPTIONS = [
  { value: '', label: 'Все сервисы' },
  { value: 'auth-service', label: 'auth-service' },
  { value: 'user-service', label: 'user-service' },
  { value: 'mfa-service', label: 'mfa-service' },
  { value: 'policy-service', label: 'policy-service' },
  { value: 'audit-service', label: 'audit-service' },
]

export default function AuditPage() {
  const [filters, setFilters] = useState<AuditFilters>({})
  const [page, setPage] = useState(0)
  const [selectedEvent, setSelectedEvent] = useState<AuditEvent | null>(null)

  const { data, isLoading } = useAuditEvents(filters, page, PAGE_SIZE)
  const events = data?.content ?? []
  const total = data?.totalElements ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const updateFilter = <K extends keyof AuditFilters>(key: K, value: AuditFilters[K]) => {
    setFilters((prev) => ({ ...prev, [key]: value || undefined }))
    setPage(0)
  }

  const resetFilters = () => {
    setFilters({})
    setPage(0)
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-navy">Аудит</h1>
        <p className="text-sm text-body mt-1">
          Журнал событий безопасности и действий пользователей. Всего: {total}
        </p>
      </div>

      {/* Filter bar */}
      <div className="bg-white border border-border rounded-lg p-4 mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex-1 min-w-[160px]">
            <label className="block text-xs font-medium text-body mb-1">User ID</label>
            <input
              type="text"
              value={filters.userId ?? ''}
              onChange={(e) => updateFilter('userId', e.target.value)}
              placeholder="UUID пользователя"
              className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40 focus:border-purple"
            />
          </div>
          <div className="flex-1 min-w-[160px]">
            <label className="block text-xs font-medium text-body mb-1">Тип события</label>
            <input
              type="text"
              value={filters.eventType ?? ''}
              onChange={(e) => updateFilter('eventType', e.target.value)}
              placeholder="user.created"
              className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40 focus:border-purple"
            />
          </div>
          <div className="flex-1 min-w-[160px]">
            <label className="block text-xs font-medium text-body mb-1">Сервис</label>
            <select
              value={filters.eventSource ?? ''}
              onChange={(e) => updateFilter('eventSource', e.target.value)}
              className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40 focus:border-purple"
            >
              {SERVICE_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>
          <div className="flex-1 min-w-[180px]">
            <label className="block text-xs font-medium text-body mb-1">С</label>
            <input
              type="datetime-local"
              value={filters.from ?? ''}
              onChange={(e) => updateFilter('from', e.target.value)}
              className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40 focus:border-purple"
            />
          </div>
          <div className="flex-1 min-w-[180px]">
            <label className="block text-xs font-medium text-body mb-1">По</label>
            <input
              type="datetime-local"
              value={filters.to ?? ''}
              onChange={(e) => updateFilter('to', e.target.value)}
              className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40 focus:border-purple"
            />
          </div>
          <button
            onClick={resetFilters}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm border border-border rounded-md text-navy hover:bg-surface transition-colors"
          >
            <RotateCcw size={14} />
            Сбросить
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-3 font-medium text-navy">Время</th>
              <th className="text-left px-4 py-3 font-medium text-navy">Тип</th>
              <th className="text-left px-4 py-3 font-medium text-navy">Сервис</th>
              <th className="text-left px-4 py-3 font-medium text-navy">User ID</th>
              <th className="text-right px-4 py-3 font-medium text-navy">Действия</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-body">Загрузка...</td>
              </tr>
            ) : events.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-body">Событий не найдено</td>
              </tr>
            ) : (
              events.map((event) => (
                <tr
                  key={event.id}
                  onClick={() => setSelectedEvent(event)}
                  className="border-b border-border last:border-0 hover:bg-surface cursor-pointer transition-colors"
                >
                  <td className="px-4 py-3 text-body">{new Date(event.timestamp).toLocaleString()}</td>
                  <td className="px-4 py-3 text-navy font-medium">{event.eventType}</td>
                  <td className="px-4 py-3 text-body">{event.eventSource}</td>
                  <td className="px-4 py-3 text-body font-mono text-xs">{event.userId ?? '—'}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={(e) => { e.stopPropagation(); setSelectedEvent(event) }}
                      className="inline-flex items-center gap-1.5 px-3 py-1 text-xs border border-border rounded-md text-purple hover:bg-purple-bg transition-colors"
                    >
                      <Eye size={14} />
                      Подробнее
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-body">
            Страница {page + 1} из {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm border border-border rounded-md text-navy hover:bg-surface disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Назад
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm border border-border rounded-md text-navy hover:bg-surface disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Далее
            </button>
          </div>
        </div>
      )}

      {/* Detail modal */}
      {selectedEvent && (
        <EventDetailModal event={selectedEvent} onClose={() => setSelectedEvent(null)} />
      )}
    </div>
  )
}

function EventDetailModal({ event, onClose }: { event: AuditEvent; onClose: () => void }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-2xl max-h-[90vh] flex flex-col">
        <div className="px-6 py-4 border-b border-border flex items-center justify-between">
          <h2 className="text-lg font-semibold text-navy">Детали события</h2>
          <button
            onClick={onClose}
            className="text-body hover:text-navy transition-colors"
            aria-label="Закрыть"
          >
            <X size={18} />
          </button>
        </div>
        <div className="px-6 py-4 space-y-3 overflow-auto">
          <DetailRow label="ID" value={event.id} mono />
          <DetailRow label="Время" value={new Date(event.timestamp).toLocaleString()} />
          <DetailRow label="Тип" value={event.eventType} />
          <DetailRow label="Сервис" value={event.eventSource} />
          <DetailRow label="User ID" value={event.userId ?? '—'} mono />
          <DetailRow label="Client ID" value={event.clientId ?? '—'} mono />
          <DetailRow label="Session ID" value={event.sessionId ?? '—'} mono />
          <DetailRow label="IP-адрес" value={event.ipAddress ?? '—'} mono />
          <div>
            <div className="text-xs font-medium text-body mb-1">Детали</div>
            <pre className="bg-surface border border-border rounded-md p-3 text-xs text-navy overflow-auto font-mono whitespace-pre-wrap break-all">
{event.details ? JSON.stringify(event.details, null, 2) : '—'}
            </pre>
          </div>
        </div>
        <div className="px-6 py-4 border-t border-border flex justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm border border-border rounded-md text-navy hover:bg-surface transition-colors"
          >
            Закрыть
          </button>
        </div>
      </div>
    </div>
  )
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="flex gap-3">
      <div className="text-xs font-medium text-body w-28 shrink-0 pt-0.5">{label}</div>
      <div className={`text-sm text-navy break-all ${mono ? 'font-mono text-xs' : ''}`}>{value}</div>
    </div>
  )
}
