import { useState } from 'react'
import { Check, X } from 'lucide-react'
import {
  useAllRequests,
  useApproveRequest,
  useRejectRequest,
  type VacationRequest,
  type VacationRequestStatus,
} from '@/api/requests'
import { typeLabel, statusLabel, statusColors, formatDate } from '@/lib/format'

const PAGE_SIZE = 20

export default function AllRequestsPage() {
  const [status, setStatus] = useState<VacationRequestStatus | ''>('PENDING')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useAllRequests(status || undefined, page, PAGE_SIZE)
  const approveMutation = useApproveRequest()
  const rejectMutation = useRejectRequest()

  const requests = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-navy">Все заявки</h1>
          <p className="text-sm text-body mt-1">{data?.totalElements ?? 0} записей</p>
        </div>
        <select
          value={status}
          onChange={e => { setStatus(e.target.value as VacationRequestStatus | ''); setPage(0) }}
          className="px-3 py-2 border border-border rounded-md text-sm bg-white"
        >
          <option value="">Все статусы</option>
          <option value="PENDING">На рассмотрении</option>
          <option value="APPROVED">Одобрено</option>
          <option value="REJECTED">Отклонено</option>
          <option value="CANCELLED">Отменено</option>
        </select>
      </div>

      <div className="bg-white border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-3 font-medium">Сотрудник</th>
              <th className="text-left px-4 py-3 font-medium">Тип</th>
              <th className="text-left px-4 py-3 font-medium">С</th>
              <th className="text-left px-4 py-3 font-medium">По</th>
              <th className="text-left px-4 py-3 font-medium">Причина</th>
              <th className="text-left px-4 py-3 font-medium">Статус</th>
              <th className="w-36" />
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-body">Загрузка…</td>
              </tr>
            ) : requests.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-body">Ничего не найдено</td>
              </tr>
            ) : (
              requests.map((req: VacationRequest) => (
                <tr key={req.id} className="border-b border-border last:border-0 hover:bg-surface">
                  <td className="px-4 py-3 text-body font-mono text-xs">{req.userId.slice(0, 8)}…</td>
                  <td className="px-4 py-3 text-navy">{typeLabel[req.type]}</td>
                  <td className="px-4 py-3 text-body">{formatDate(req.startDate)}</td>
                  <td className="px-4 py-3 text-body">{formatDate(req.endDate)}</td>
                  <td className="px-4 py-3 text-body max-w-xs truncate" title={req.reason}>{req.reason}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${statusColors[req.status]}`}>
                      {statusLabel[req.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {req.status === 'PENDING' && (
                      <div className="flex gap-2">
                        <button
                          onClick={() => approveMutation.mutate({ id: req.id })}
                          className="px-2 py-1 bg-green-100 text-green-800 rounded text-xs inline-flex items-center gap-1"
                          disabled={approveMutation.isPending}
                        >
                          <Check size={12} /> Одобрить
                        </button>
                        <button
                          onClick={() => {
                            const comment = prompt('Причина отказа?') || undefined
                            rejectMutation.mutate({ id: req.id, comment })
                          }}
                          className="px-2 py-1 bg-red-100 text-red-800 rounded text-xs inline-flex items-center gap-1"
                          disabled={rejectMutation.isPending}
                        >
                          <X size={12} /> Отклонить
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-body">Страница {page + 1} из {totalPages}</p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm border border-border rounded-md disabled:opacity-40"
            >
              Назад
            </button>
            <button
              onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm border border-border rounded-md disabled:opacity-40"
            >
              Далее
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
