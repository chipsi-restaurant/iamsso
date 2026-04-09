import { useState } from 'react'
import { Plus, Trash2, Play } from 'lucide-react'
import StatusBadge from '@/components/status-badge'
import {
  useRoles,
  useCreateRole,
  useDeleteRole,
  usePolicies,
  useCreatePolicy,
  useDeletePolicy,
  useEvaluatePolicy,
  type EvaluateResult,
  type Policy,
} from '@/api/policies'

type Tab = 'roles' | 'policies' | 'evaluate'

const tabs: { key: Tab; label: string }[] = [
  { key: 'roles', label: 'Роли' },
  { key: 'policies', label: 'Политики' },
  { key: 'evaluate', label: 'Проверка' },
]

/* ---------- Delete wrappers (hooks need static id) ---------- */

function RoleDeleteButton({ id }: { id: string }) {
  const del = useDeleteRole(id)
  return (
    <button
      onClick={() => del.mutate()}
      disabled={del.isPending}
      className="text-body hover:text-ruby transition-colors"
    >
      <Trash2 size={16} />
    </button>
  )
}

function PolicyDeleteButton({ id }: { id: string }) {
  const del = useDeletePolicy(id)
  return (
    <button
      onClick={() => del.mutate()}
      disabled={del.isPending}
      className="text-body hover:text-ruby transition-colors"
    >
      <Trash2 size={16} />
    </button>
  )
}

/* ---------- Tab: Roles ---------- */

function RolesTab() {
  const { data: roles, isLoading } = useRoles()
  const createRole = useCreateRole()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')

  const handleCreate = () => {
    if (!name.trim()) return
    createRole.mutate(
      { name: name.trim(), description: description.trim() || undefined },
      { onSuccess: () => { setName(''); setDescription('') } },
    )
  }

  return (
    <div className="space-y-4">
      {/* Inline create form */}
      <div className="flex items-end gap-3">
        <div className="flex-1">
          <label className="block text-xs font-medium text-body mb-1">Название</label>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. admin"
            className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
          />
        </div>
        <div className="flex-1">
          <label className="block text-xs font-medium text-body mb-1">Описание</label>
          <input
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Необязательное описание"
            className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
          />
        </div>
        <button
          onClick={handleCreate}
          disabled={createRole.isPending || !name.trim()}
          className="inline-flex items-center gap-1.5 bg-purple text-white px-4 py-1.5 rounded-md text-sm font-medium hover:bg-purple/90 disabled:opacity-50 transition-colors"
        >
          <Plus size={16} />
          Создать
        </button>
      </div>

      {/* Table */}
      <div className="bg-white border border-border rounded-md overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-2.5 font-medium text-body">Название</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Описание</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Создан</th>
              <th className="w-10" />
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={4} className="px-4 py-4 text-center text-body">Loading...</td>
              </tr>
            ) : !roles?.length ? (
              <tr>
                <td colSpan={4} className="px-4 py-4 text-center text-body">No roles yet.</td>
              </tr>
            ) : (
              roles.map((r) => (
                <tr key={r.id} className="border-b border-border last:border-b-0">
                  <td className="px-4 py-3 text-navy font-medium">{r.name}</td>
                  <td className="px-4 py-3 text-body">{r.description ?? '—'}</td>
                  <td className="px-4 py-3 text-body">{new Date(r.createdAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3 text-right">
                    <RoleDeleteButton id={r.id} />
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

/* ---------- Tab: Policies ---------- */

function PoliciesTab() {
  const { data, isLoading } = usePolicies()
  const createPolicy = useCreatePolicy()
  const { data: roles } = useRoles()

  const [modalOpen, setModalOpen] = useState(false)
  const [form, setForm] = useState({
    name: '',
    description: null as string | null,
    role: '',
    effect: 'ALLOW' as 'ALLOW' | 'DENY',
    action: '',
    resourcePattern: '',
    conditions: null as Policy['conditions'],
    priority: 0,
    enabled: true,
  })

  const resetForm = () =>
    setForm({ name: '', description: null, role: '', effect: 'ALLOW', action: '', resourcePattern: '', conditions: null, priority: 0, enabled: true })

  const handleCreate = () => {
    if (!form.name.trim() || !form.role.trim() || !form.action.trim() || !form.resourcePattern.trim()) return
    createPolicy.mutate(form, {
      onSuccess: () => { resetForm(); setModalOpen(false) },
    })
  }

  const policies = data?.content

  return (
    <div className="space-y-4">
      <div className="flex justify-end">
        <button
          onClick={() => setModalOpen(true)}
          className="inline-flex items-center gap-1.5 bg-purple text-white px-4 py-1.5 rounded-md text-sm font-medium hover:bg-purple/90 transition-colors"
        >
          <Plus size={16} />
          Создать политику
        </button>
      </div>

      {/* Table */}
      <div className="bg-white border border-border rounded-md overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-2.5 font-medium text-body">Название</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Роль</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Эффект</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Действие</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Паттерн ресурса</th>
              <th className="text-left px-4 py-2.5 font-medium text-body">Приоритет</th>
              <th className="w-10" />
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={7} className="px-4 py-4 text-center text-body">Loading...</td>
              </tr>
            ) : !policies?.length ? (
              <tr>
                <td colSpan={7} className="px-4 py-4 text-center text-body">No policies yet.</td>
              </tr>
            ) : (
              policies.map((p) => (
                <tr key={p.id} className="border-b border-border last:border-b-0">
                  <td className="px-4 py-3 text-navy font-medium">{p.name}</td>
                  <td className="px-4 py-3 text-body">{p.role}</td>
                  <td className="px-4 py-3"><StatusBadge status={p.effect} /></td>
                  <td className="px-4 py-3 text-body">{p.action}</td>
                  <td className="px-4 py-3 text-body font-mono text-xs">{p.resourcePattern}</td>
                  <td className="px-4 py-3 text-body">{p.priority}</td>
                  <td className="px-4 py-3 text-right">
                    <PolicyDeleteButton id={p.id} />
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-lg shadow-lg w-full max-w-lg p-6 space-y-4">
            <h3 className="text-lg font-semibold text-navy">Создать политику</h3>

            <div className="grid grid-cols-2 gap-3">
              <div className="col-span-2">
                <label className="block text-xs font-medium text-body mb-1">Название</label>
                <input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-body mb-1">Роль</label>
                <select
                  value={form.role}
                  onChange={(e) => setForm({ ...form, role: e.target.value })}
                  className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
                >
                  <option value="">Выберите роль</option>
                  {roles?.map((r) => (
                    <option key={r.id} value={r.name}>{r.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-body mb-1">Эффект</label>
                <select
                  value={form.effect}
                  onChange={(e) => setForm({ ...form, effect: e.target.value as 'ALLOW' | 'DENY' })}
                  className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
                >
                  <option value="ALLOW">ALLOW</option>
                  <option value="DENY">DENY</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-body mb-1">Действие</label>
                <select
                  value={form.action}
                  onChange={(e) => setForm({ ...form, action: e.target.value })}
                  className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
                >
                  <option value="">Выберите действие</option>
                  <option value="READ">READ</option>
                  <option value="CREATE">CREATE</option>
                  <option value="UPDATE">UPDATE</option>
                  <option value="DELETE">DELETE</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium text-body mb-1">Паттерн ресурса</label>
                <input
                  value={form.resourcePattern}
                  onChange={(e) => setForm({ ...form, resourcePattern: e.target.value })}
                  placeholder="e.g. /users/*"
                  className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-body mb-1">Приоритет</label>
                <input
                  type="number"
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: Number(e.target.value) })}
                  className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <button
                onClick={() => { resetForm(); setModalOpen(false) }}
                className="px-4 py-1.5 rounded-md text-sm font-medium text-body border border-border hover:bg-surface transition-colors"
              >
                Отмена
              </button>
              <button
                onClick={handleCreate}
                disabled={createPolicy.isPending || !form.name.trim() || !form.role || !form.action || !form.resourcePattern.trim()}
                className="inline-flex items-center gap-1.5 bg-purple text-white px-4 py-1.5 rounded-md text-sm font-medium hover:bg-purple/90 disabled:opacity-50 transition-colors"
              >
                <Plus size={16} />
                Создать
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/* ---------- Tab: Evaluate ---------- */

function EvaluateTab() {
  const evaluate = useEvaluatePolicy()
  const { data: roles } = useRoles()

  const [userId, setUserId] = useState('')
  const [role, setRole] = useState('')
  const [action, setAction] = useState('')
  const [resource, setResource] = useState('')
  const [result, setResult] = useState<EvaluateResult | null>(null)

  const handleEvaluate = () => {
    if (!userId.trim() || !role || !action || !resource.trim()) return
    evaluate.mutate(
      { subject: { userId: userId.trim(), role }, action, resource: resource.trim() },
      { onSuccess: (data) => setResult(data) },
    )
  }

  return (
    <div className="space-y-6 max-w-xl">
      <div className="space-y-3">
        <div>
          <label className="block text-xs font-medium text-body mb-1">User ID</label>
          <input
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="UUID of the user"
            className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-body mb-1">Роль</label>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value)}
            className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
          >
            <option value="">Выберите роль</option>
            {roles?.map((r) => (
              <option key={r.id} value={r.name}>{r.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-body mb-1">Действие</label>
          <select
            value={action}
            onChange={(e) => setAction(e.target.value)}
            className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
          >
            <option value="">Выберите действие</option>
            <option value="READ">READ</option>
            <option value="CREATE">CREATE</option>
            <option value="UPDATE">UPDATE</option>
            <option value="DELETE">DELETE</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-body mb-1">Ресурс</label>
          <input
            value={resource}
            onChange={(e) => setResource(e.target.value)}
            placeholder="e.g. /users/123"
            className="w-full border border-border rounded-md px-3 py-1.5 text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/40"
          />
        </div>
        <button
          onClick={handleEvaluate}
          disabled={evaluate.isPending || !userId.trim() || !role || !action || !resource.trim()}
          className="inline-flex items-center gap-1.5 bg-purple text-white px-4 py-1.5 rounded-md text-sm font-medium hover:bg-purple/90 disabled:opacity-50 transition-colors"
        >
          <Play size={16} />
          Проверить
        </button>
      </div>

      {result && (
        <div className="bg-white border border-border rounded-md p-5 space-y-3">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium text-body">Результат:</span>
            <StatusBadge status={result.allowed ? 'ALLOW' : 'DENY'} />
          </div>
          <div className="text-sm text-body">
            <span className="font-medium text-navy">Причина:</span> {result.reason}
          </div>
          {result.policyId && (
            <div className="text-sm text-body">
              <span className="font-medium text-navy">ID политики:</span>{' '}
              <span className="font-mono text-xs">{result.policyId}</span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

/* ---------- Main Page ---------- */

export default function PoliciesPage() {
  const [activeTab, setActiveTab] = useState<Tab>('roles')

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-navy">Политики</h1>
        <p className="text-body mt-1">Управление ролями, политиками авторизации и проверка доступа.</p>
      </div>

      {/* Tab bar */}
      <div className="flex gap-6 border-b border-border">
        {tabs.map((t) => (
          <button
            key={t.key}
            onClick={() => setActiveTab(t.key)}
            className={`pb-2 text-sm font-medium transition-colors ${
              activeTab === t.key
                ? 'text-purple border-b-2 border-purple'
                : 'text-body hover:text-navy'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Tab content */}
      {activeTab === 'roles' && <RolesTab />}
      {activeTab === 'policies' && <PoliciesTab />}
      {activeTab === 'evaluate' && <EvaluateTab />}
    </div>
  )
}
