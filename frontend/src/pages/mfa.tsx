import { useState } from 'react'
import { ShieldCheck, Plus, Trash2, CheckCircle, XCircle } from 'lucide-react'
import { useAuth } from '@/lib/auth-context'
import StatusBadge from '@/components/status-badge'
import {
  useMfaFactors,
  useMfaStatus,
  useEnrollMfa,
  useConfirmMfa,
  useRemoveMfa,
  type MfaEnrollment,
} from '@/api/mfa'

function fmt(iso: string) {
  return new Date(iso).toLocaleString()
}

function FactorRow({ factor, userId }: { factor: { id: string; factorType: string; status: string; displayName: string | null; createdAt: string }; userId: string }) {
  const [code, setCode] = useState('')
  const confirm = useConfirmMfa(userId, factor.id)
  const remove = useRemoveMfa(userId, factor.id)
  const [confirmDelete, setConfirmDelete] = useState(false)

  return (
    <tr className="border-b border-border last:border-0">
      <td className="px-4 py-3 text-sm text-navy">{factor.factorType}</td>
      <td className="px-4 py-3"><StatusBadge status={factor.status} /></td>
      <td className="px-4 py-3 text-sm text-body">{factor.displayName || '—'}</td>
      <td className="px-4 py-3 text-sm text-body">{fmt(factor.createdAt)}</td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          {factor.status === 'PENDING' && (
            <>
              <input
                type="text"
                value={code}
                onChange={e => setCode(e.target.value)}
                placeholder="Код"
                className="w-24 rounded-md border border-border px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-purple/50"
              />
              <button
                onClick={() => confirm.mutate(code)}
                disabled={!code || confirm.isPending}
                className="rounded-md bg-purple px-3 py-1 text-sm font-medium text-white hover:bg-purple-hover transition-colors disabled:opacity-50"
              >
                {confirm.isPending ? '...' : 'Подтвердить'}
              </button>
            </>
          )}
          {!confirmDelete ? (
            <button
              onClick={() => setConfirmDelete(true)}
              className="p-1.5 rounded-md text-body hover:text-ruby hover:bg-red-50 transition-colors"
              title="Удалить"
            >
              <Trash2 size={16} />
            </button>
          ) : (
            <div className="flex items-center gap-1">
              <button
                onClick={() => remove.mutate()}
                disabled={remove.isPending}
                className="rounded-md bg-red-600 px-2 py-1 text-xs font-medium text-white hover:bg-red-700 transition-colors disabled:opacity-50"
              >
                {remove.isPending ? '...' : 'Удалить'}
              </button>
              <button
                onClick={() => setConfirmDelete(false)}
                className="rounded-md border border-border bg-white px-2 py-1 text-xs font-medium text-navy hover:bg-surface transition-colors"
              >
                Отмена
              </button>
            </div>
          )}
        </div>
      </td>
    </tr>
  )
}

export default function MfaPage() {
  const { user } = useAuth()
  const userId = user?.sub || ''
  const { data: factors, isLoading: factorsLoading } = useMfaFactors(userId)
  const { data: status } = useMfaStatus(userId)
  const enroll = useEnrollMfa(userId)
  const [enrollment, setEnrollment] = useState<MfaEnrollment | null>(null)

  const handleEnroll = (factorType: 'TOTP' | 'EMAIL_OTP') => {
    enroll.mutate({ factorType }, {
      onSuccess: (data) => setEnrollment(data),
    })
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-navy">Многофакторная аутентификация</h1>
        <p className="text-body mt-1">Управление факторами MFA для вашей учётной записи.</p>
      </div>

      {/* MFA status banner */}
      <div className="bg-white border border-border rounded-md p-6">
        <div className="flex items-center gap-3">
          {status?.mfaEnabled ? (
            <CheckCircle size={24} className="text-green-text" />
          ) : (
            <XCircle size={24} className="text-body" />
          )}
          <div>
            <h2 className="text-lg font-semibold text-navy">
              MFA {status?.mfaEnabled ? 'включено' : 'выключено'}
            </h2>
            {status?.activeFactors && status.activeFactors.length > 0 && (
              <p className="text-sm text-body mt-0.5">
                Активные факторы: {status.activeFactors.join(', ')}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Enrollment section */}
      <div className="bg-white border border-border rounded-md p-6">
        <div className="flex items-center gap-2 mb-5">
          <ShieldCheck size={20} className="text-purple" />
          <h2 className="text-lg font-semibold text-navy">Подключить фактор</h2>
        </div>
        <div className="flex flex-wrap gap-3">
          <button
            onClick={() => handleEnroll('TOTP')}
            disabled={enroll.isPending}
            className="inline-flex items-center gap-2 rounded-md bg-purple px-4 py-2 text-sm font-medium text-white hover:bg-purple-hover transition-colors disabled:opacity-50"
          >
            <Plus size={16} />
            {enroll.isPending ? 'Подключение...' : 'Подключить TOTP'}
          </button>
          <button
            onClick={() => handleEnroll('EMAIL_OTP')}
            disabled={enroll.isPending}
            className="inline-flex items-center gap-2 rounded-md border border-border bg-white px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors disabled:opacity-50"
          >
            <Plus size={16} />
            {enroll.isPending ? 'Подключение...' : 'Подключить Email OTP'}
          </button>
        </div>

        {enrollment && (
          <div className="mt-5 rounded-md border border-border bg-surface p-4 space-y-3">
            <h3 className="text-sm font-semibold text-navy">Настройка {enrollment.factorType}</h3>
            <p className="text-sm text-body">
              Статус: <StatusBadge status={enrollment.status} />
            </p>
            {enrollment.secret && (
              <div>
                <p className="text-sm text-body mb-1">Секрет:</p>
                <code className="block rounded-md bg-white border border-border px-3 py-2 text-sm font-mono text-navy break-all select-all">
                  {enrollment.secret}
                </code>
              </div>
            )}
            {enrollment.provisioningUri && (
              <div>
                <p className="text-sm text-body mb-1">URI для приложения:</p>
                <code className="block rounded-md bg-white border border-border px-3 py-2 text-xs font-mono text-navy break-all select-all">
                  {enrollment.provisioningUri}
                </code>
              </div>
            )}
            <p className="text-xs text-body">
              Введите код из приложения-аутентификатора в таблице ниже для подтверждения фактора.
            </p>
            <button
              onClick={() => setEnrollment(null)}
              className="rounded-md border border-border bg-white px-3 py-1 text-sm font-medium text-navy hover:bg-surface transition-colors"
            >
              Закрыть
            </button>
          </div>
        )}
      </div>

      {/* Factors table */}
      <div className="bg-white border border-border rounded-md overflow-hidden">
        <div className="px-6 py-4 border-b border-border">
          <h2 className="text-lg font-semibold text-navy">Факторы</h2>
        </div>
        {factorsLoading ? (
          <p className="px-6 py-4 text-sm text-body">Загрузка...</p>
        ) : !factors || factors.length === 0 ? (
          <p className="px-6 py-4 text-sm text-body">Факторы MFA не подключены.</p>
        ) : (
          <table className="w-full">
            <thead className="bg-surface">
              <tr>
                <th className="text-left px-4 py-2 text-xs font-medium text-body uppercase">Тип</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-body uppercase">Статус</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-body uppercase">Название</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-body uppercase">Создан</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-body uppercase">Действия</th>
              </tr>
            </thead>
            <tbody>
              {factors.map(factor => (
                <FactorRow key={factor.id} factor={factor} userId={userId} />
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
