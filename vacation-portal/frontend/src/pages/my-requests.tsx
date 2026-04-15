import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, XCircle } from 'lucide-react'
import { useMyRequests, useCancelRequest, type VacationRequest } from '@/api/requests'
import { typeLabel, statusLabel, statusColors, formatDate } from '@/lib/format'

const PAGE_SIZE = 20

export default function MyRequestsPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMyRequests(page, PAGE_SIZE)
  const cancelMutation = useCancelRequest()

  const requests = data?.content ?? []
  const totalPages = data?.totalPages ?? 0

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-navy">Мои заявки</h1>
          <p className="text-sm text-body mt-1">{data?.totalElements ?? 0} всего</p>
        </div>
        <Link
          to="/new-request"
          className="inline-flex items-center gap-2 px-4 py-2 bg-purple text-white text-sm font-medium rounded-md hover:bg-purple-hover transition-colors"
        >
          <Plus size={16} />
          Создать заявку
        </Link>
      </div>

      <div className="bg-white border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-3 font-medium">Тип</th>
              <th className="text-left px-4 py-3 font-medium">С</th>
              <th className="text-left px-4 py-3 font-medium">По</th>
              <th className="text-left px-4 py-3 font-medium">Причина</th>
              <th className="text-left px-4 py-3 font-medium">Статус</th>
              <th className="text-left px-4 py-3 font-medium">Создано</th>
              <th className="w-20" />
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-body">Загрузка…</td>
              </tr>
            ) : requests.length === 0 ? (
              <tr>
                <td colSpan={7} className="px-4 py-12 text-center text-body">Пока нет заявок</td>
              </tr>
            ) : (
              requests.map((req: VacationRequest) => (
                <tr key={req.id} className="border-b border-border last:border-0 hover:bg-surface">
                  <td className="px-4 py-3 text-navy">{typeLabel[req.type]}</td>
                  <td className="px-4 py-3 text-body">{formatDate(req.startDate)}</td>
                  <td className="px-4 py-3 text-body">{formatDate(req.endDate)}</td>
                  <td className="px-4 py-3 text-body max-w-xs truncate" title={req.reason}>{req.reason}</td>
                  <td className="px-4 py-3">
                    <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${statusColors[req.status]}`}>
                      {statusLabel[req.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-body">{formatDate(req.createdAt)}</td>
                  <td className="px-4 py-3">
                    {req.status === 'PENDING' && (
                      <button
                        onClick={() => {
                          if (confirm('Отменить заявку?')) cancelMutation.mutate(req.id)
                        }}
                        disabled={cancelMutation.isPending}
                        className="text-ruby hover:underline text-xs inline-flex items-center gap-1"
                      >
                        <XCircle size={14} /> Отменить
                      </button>
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
