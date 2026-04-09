import { useState, useCallback } from 'react'
import { Plus, RefreshCw, Trash2, Copy } from 'lucide-react'
import {
  useClients,
  useCreateClient,
  useRotateSecret,
  useDeleteClient,
  type OAuthClient,
} from '@/api/clients'

/* ------------------------------------------------------------------ */
/*  Secret banner – shown once after create or rotate                 */
/* ------------------------------------------------------------------ */

function SecretBanner({
  secret,
  onDismiss,
}: {
  secret: string
  onDismiss: () => void
}) {
  const [copied, setCopied] = useState(false)

  const copy = useCallback(() => {
    navigator.clipboard.writeText(secret).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }, [secret])

  return (
    <div className="bg-purple/10 border border-purple rounded-md px-4 py-3 flex items-center justify-between gap-4">
      <div className="min-w-0">
        <p className="text-sm font-medium text-navy">
          Секрет клиента (скопируйте сейчас — он больше не будет показан):
        </p>
        <code className="text-sm text-purple break-all select-all">{secret}</code>
      </div>
      <div className="flex items-center gap-2 shrink-0">
        <button
          type="button"
          onClick={copy}
          className="inline-flex items-center gap-1.5 rounded-md border border-border bg-white px-3 py-1.5 text-sm font-medium text-navy hover:bg-surface transition-colors"
        >
          <Copy size={14} />
          {copied ? 'Скопировано!' : 'Копировать'}
        </button>
        <button
          type="button"
          onClick={onDismiss}
          className="text-body hover:text-navy text-sm underline"
        >
          Скрыть
        </button>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  Create client modal                                               */
/* ------------------------------------------------------------------ */

function CreateClientModal({
  onClose,
  onCreated,
}: {
  onClose: () => void
  onCreated: (c: OAuthClient) => void
}) {
  const [clientName, setClientName] = useState('')
  const [grantTypes, setGrantTypes] = useState('')
  const [redirectUris, setRedirectUris] = useState('')
  const [scopes, setScopes] = useState('')
  const create = useCreateClient()

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    create.mutate(
      {
        clientName: clientName.trim(),
        grantTypes: grantTypes
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
        redirectUris: redirectUris
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
        scopes: scopes
          .split(',')
          .map((s) => s.trim())
          .filter(Boolean),
      },
      {
        onSuccess: (data) => {
          onCreated(data)
          onClose()
        },
      },
    )
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-md shadow-lg border border-border w-full max-w-md mx-4">
        <div className="px-5 py-4 border-b border-border">
          <h2 className="text-lg font-semibold text-navy">Создать OAuth-клиент</h2>
        </div>

        <form onSubmit={submit} className="px-5 py-4 space-y-4">
          <label className="block">
            <span className="text-sm font-medium text-navy">Название клиента</span>
            <input
              required
              value={clientName}
              onChange={(e) => setClientName(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-navy">
              Типы грантов <span className="text-body font-normal">(через запятую)</span>
            </span>
            <input
              required
              placeholder="authorization_code, client_credentials"
              value={grantTypes}
              onChange={(e) => setGrantTypes(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-navy">
              URI перенаправления <span className="text-body font-normal">(через запятую)</span>
            </span>
            <input
              placeholder="https://app.example.com/callback"
              value={redirectUris}
              onChange={(e) => setRedirectUris(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
            />
          </label>

          <label className="block">
            <span className="text-sm font-medium text-navy">
              Скоупы <span className="text-body font-normal">(через запятую)</span>
            </span>
            <input
              placeholder="openid, profile, email"
              value={scopes}
              onChange={(e) => setScopes(e.target.value)}
              className="mt-1 block w-full rounded-md border border-border px-3 py-2 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
            />
          </label>

          {create.isError && (
            <p className="text-sm text-red-600">
              {(create.error as Error).message ?? 'Не удалось создать клиента.'}
            </p>
          )}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md border border-border px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors"
            >
              Отмена
            </button>
            <button
              type="submit"
              disabled={create.isPending}
              className="rounded-md bg-purple px-4 py-2 text-sm font-medium text-white hover:bg-purple/90 transition-colors disabled:opacity-50"
            >
              {create.isPending ? 'Создание...' : 'Создать'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  Confirm dialog (rotate / delete)                                  */
/* ------------------------------------------------------------------ */

function ConfirmDialog({
  title,
  description,
  confirmLabel,
  destructive,
  pending,
  onConfirm,
  onCancel,
}: {
  title: string
  description: string
  confirmLabel: string
  destructive?: boolean
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-md shadow-lg border border-border w-full max-w-sm mx-4 p-5 space-y-4">
        <h2 className="text-lg font-semibold text-navy">{title}</h2>
        <p className="text-sm text-body">{description}</p>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="rounded-md border border-border px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors"
          >
            Отмена
          </button>
          <button
            type="button"
            disabled={pending}
            onClick={onConfirm}
            className={`rounded-md px-4 py-2 text-sm font-medium text-white transition-colors disabled:opacity-50 ${
              destructive
                ? 'bg-red-600 hover:bg-red-700'
                : 'bg-purple hover:bg-purple/90'
            }`}
          >
            {pending ? 'Подождите...' : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  Row-level action wrappers (hooks require static clientId)         */
/* ------------------------------------------------------------------ */

function RotateButton({
  clientId,
  onSecret,
}: {
  clientId: string
  onSecret: (s: string) => void
}) {
  const [open, setOpen] = useState(false)
  const rotate = useRotateSecret(clientId)

  return (
    <>
      <button
        type="button"
        title="Ротировать секрет"
        onClick={() => setOpen(true)}
        className="p-1 rounded hover:bg-surface text-body hover:text-purple transition-colors"
      >
        <RefreshCw size={15} />
      </button>
      {open && (
        <ConfirmDialog
          title="Ротировать секрет клиента"
          description="Текущий секрет будет немедленно аннулирован. Новый секрет будет показан один раз."
          confirmLabel="Ротировать"
          pending={rotate.isPending}
          onCancel={() => setOpen(false)}
          onConfirm={() =>
            rotate.mutate(undefined, {
              onSuccess: (data) => {
                if (data.clientSecret) onSecret(data.clientSecret)
                setOpen(false)
              },
            })
          }
        />
      )}
    </>
  )
}

function DeleteButton({
  clientId,
  clientName,
}: {
  clientId: string
  clientName: string
}) {
  const [open, setOpen] = useState(false)
  const del = useDeleteClient(clientId)

  return (
    <>
      <button
        type="button"
        title="Удалить клиент"
        onClick={() => setOpen(true)}
        className="p-1 rounded hover:bg-surface text-body hover:text-red-600 transition-colors"
      >
        <Trash2 size={15} />
      </button>
      {open && (
        <ConfirmDialog
          title="Удалить клиент"
          description={`Вы уверены, что хотите удалить "${clientName}"? Это действие нельзя отменить.`}
          confirmLabel="Удалить"
          destructive
          pending={del.isPending}
          onCancel={() => setOpen(false)}
          onConfirm={() =>
            del.mutate(undefined, { onSuccess: () => setOpen(false) })
          }
        />
      )}
    </>
  )
}

/* ------------------------------------------------------------------ */
/*  Main page                                                         */
/* ------------------------------------------------------------------ */

export default function ClientsPage() {
  const { data: clients, isLoading } = useClients()
  const [showCreate, setShowCreate] = useState(false)
  const [revealedSecret, setRevealedSecret] = useState<string | null>(null)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-navy">OAuth-клиенты</h1>
          <p className="text-body mt-1">Управление клиентскими приложениями OAuth 2.0.</p>
        </div>
        <button
          type="button"
          onClick={() => setShowCreate(true)}
          className="inline-flex items-center gap-1.5 rounded-md bg-purple px-4 py-2 text-sm font-medium text-white hover:bg-purple/90 transition-colors"
        >
          <Plus size={16} />
          Создать клиент
        </button>
      </div>

      {/* Secret banner */}
      {revealedSecret && (
        <SecretBanner
          secret={revealedSecret}
          onDismiss={() => setRevealedSecret(null)}
        />
      )}

      {/* Table */}
      <div className="bg-white border border-border rounded-md overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-2.5 font-medium text-body">Client ID</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Название</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Типы грантов</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Скоупы</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Создан</th>
              <th className="text-right px-4 py-2.5 font-medium text-body">Действия</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-body">
                  Загрузка клиентов...
                </td>
              </tr>
            ) : !clients?.length ? (
              <tr>
                <td colSpan={6} className="px-4 py-6 text-center text-body">
                  Клиенты не найдены. Создайте первого клиента.
                </td>
              </tr>
            ) : (
              clients.map((c) => (
                <tr
                  key={c.clientId}
                  className="border-b border-border last:border-b-0"
                >
                  <td className="px-4 py-3 text-navy font-mono text-xs">
                    {c.clientId}
                  </td>
                  <td className="px-4 py-3 text-navy font-medium">
                    {c.clientName}
                  </td>
                  <td className="px-4 py-3 text-body">
                    {c.grantTypes.join(', ')}
                  </td>
                  <td className="px-4 py-3 text-body">
                    {c.scopes.join(', ') || '—'}
                  </td>
                  <td className="px-4 py-3 text-body whitespace-nowrap">
                    {new Date(c.createdAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      <RotateButton
                        clientId={c.clientId}
                        onSecret={setRevealedSecret}
                      />
                      <DeleteButton
                        clientId={c.clientId}
                        clientName={c.clientName}
                      />
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Create modal */}
      {showCreate && (
        <CreateClientModal
          onClose={() => setShowCreate(false)}
          onCreated={(c) => {
            if (c.clientSecret) setRevealedSecret(c.clientSecret)
          }}
        />
      )}
    </div>
  )
}
