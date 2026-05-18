import { useState } from 'react'
import { LogOut, Monitor, Trash2 } from 'lucide-react'
import { useAuth } from '@/lib/auth-context'
import {
  useCurrentSession,
  useMySessions,
  useLogout,
  useLogoutAll,
  useRevokeSession,
} from '@/api/sessions'

function fmt(iso: string) {
  return new Date(iso).toLocaleString()
}

export default function SessionsPage() {
  const { logout: localLogout } = useAuth()
  const current = useCurrentSession()
  const sessions = useMySessions()
  const logoutMut = useLogout()
  const logoutAllMut = useLogoutAll()
  const revokeMut = useRevokeSession()
  const [confirmAll, setConfirmAll] = useState(false)

  const handleLogout = () => {
    logoutMut.mutate(undefined, { onSettled: () => localLogout() })
  }

  const handleLogoutAll = () => {
    logoutAllMut.mutate(undefined, { onSettled: () => localLogout() })
  }

  const currentSessionId = current.data?.sessionId

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-navy">Сессии</h1>
        <p className="text-body mt-1">
          Активные сессии вашей учётной записи на всех устройствах. Можно завершить
          отдельную сессию или выйти со всех устройств.
        </p>
      </div>

      <div className="bg-white border border-border rounded-md">
        <div className="flex items-center gap-2 px-6 py-4 border-b border-border">
          <Monitor size={18} className="text-purple" />
          <h2 className="text-base font-semibold text-navy">Активные сессии</h2>
        </div>

        {sessions.isLoading ? (
          <p className="text-body text-sm px-6 py-4">Загрузка списка сессий...</p>
        ) : sessions.isError ? (
          <p className="text-body text-sm px-6 py-4">Не удалось получить список сессий.</p>
        ) : !sessions.data || sessions.data.length === 0 ? (
          <p className="text-body text-sm px-6 py-4">Активных сессий нет.</p>
        ) : (
          <ul className="divide-y divide-border">
            {sessions.data.map((s) => {
              const isCurrent = s.sessionId === currentSessionId
              return (
                <li key={s.sessionId} className="px-6 py-4 flex items-start justify-between gap-4">
                  <div className="space-y-1 text-sm min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="font-mono text-xs text-navy break-all">{s.sessionId}</span>
                      {isCurrent && (
                        <span className="inline-block bg-purple/10 text-purple text-xs px-2 py-0.5 rounded">
                          текущая
                        </span>
                      )}
                    </div>
                    <div className="text-body">
                      <span className="font-medium">Клиенты:</span>{' '}
                      {s.clientIds.length > 0 ? s.clientIds.join(', ') : '—'}
                    </div>
                    <div className="text-body text-xs">
                      Создана {fmt(s.createdAt)} · Последняя активность {fmt(s.lastActivityAt)} ·
                      Истекает {fmt(s.expiresAt)}
                    </div>
                  </div>
                  {!isCurrent && (
                    <button
                      onClick={() => revokeMut.mutate(s.sessionId)}
                      disabled={revokeMut.isPending}
                      className="inline-flex items-center gap-2 rounded-md border border-border bg-white px-3 py-1.5 text-sm font-medium text-navy hover:bg-surface transition-colors disabled:opacity-50 shrink-0"
                    >
                      <Trash2 size={14} />
                      Завершить
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <div className="flex flex-wrap gap-3">
        <button
          onClick={handleLogout}
          disabled={logoutMut.isPending}
          className="inline-flex items-center gap-2 rounded-md border border-border bg-white px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors disabled:opacity-50"
        >
          <LogOut size={16} />
          {logoutMut.isPending ? 'Выход...' : 'Выйти с этого устройства'}
        </button>

        {!confirmAll ? (
          <button
            onClick={() => setConfirmAll(true)}
            className="inline-flex items-center gap-2 rounded-md bg-red-50 border border-red-200 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-100 transition-colors"
          >
            <LogOut size={16} />
            Выйти со всех устройств
          </button>
        ) : (
          <div className="inline-flex items-center gap-2">
            <span className="text-sm text-red-700">Выйти со всех устройств?</span>
            <button
              onClick={handleLogoutAll}
              disabled={logoutAllMut.isPending}
              className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 transition-colors disabled:opacity-50"
            >
              {logoutAllMut.isPending ? 'Выход...' : 'Да, выйти везде'}
            </button>
            <button
              onClick={() => setConfirmAll(false)}
              className="rounded-md border border-border bg-white px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors"
            >
              Отмена
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
