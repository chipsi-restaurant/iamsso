import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Plus, ChevronRight } from 'lucide-react'
import { useUsers, useCreateUser } from '@/api/users'
import StatusBadge from '@/components/status-badge'

const PAGE_SIZE = 20

export default function UsersPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)

  const { data, isLoading } = useUsers(page, PAGE_SIZE)
  const users = data?.content ?? []
  const total = data?.totalElements ?? 0
  const totalPages = Math.ceil(total / PAGE_SIZE)

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-navy">Users</h1>
          <p className="text-sm text-body mt-1">{total} user{total !== 1 ? 's' : ''} total</p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="inline-flex items-center gap-2 px-4 py-2 bg-purple text-white text-sm font-medium rounded-md hover:bg-purple/90 transition-colors"
        >
          <Plus size={16} />
          Create user
        </button>
      </div>

      <div className="bg-white border border-border rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-surface">
              <th className="text-left px-4 py-3 font-medium text-navy">Email</th>
              <th className="text-left px-4 py-3 font-medium text-navy">Username</th>
              <th className="text-left px-4 py-3 font-medium text-navy">Role</th>
              <th className="text-left px-4 py-3 font-medium text-navy">Status</th>
              <th className="text-left px-4 py-3 font-medium text-navy">Created</th>
              <th className="w-8" />
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-body">Loading...</td>
              </tr>
            ) : users.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-12 text-center text-body">No users found</td>
              </tr>
            ) : (
              users.map((user) => (
                <tr
                  key={user.id}
                  onClick={() => navigate(`/users/${user.id}`)}
                  className="border-b border-border last:border-0 hover:bg-surface cursor-pointer transition-colors"
                >
                  <td className="px-4 py-3 text-navy font-medium">{user.email ?? '—'}</td>
                  <td className="px-4 py-3 text-body">{user.username ?? '—'}</td>
                  <td className="px-4 py-3 text-body">{user.role}</td>
                  <td className="px-4 py-3"><StatusBadge status={user.status} /></td>
                  <td className="px-4 py-3 text-body">{new Date(user.createdAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3 text-body"><ChevronRight size={16} /></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-body">
            Page {page + 1} of {totalPages}
          </p>
          <div className="flex gap-2">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm border border-border rounded-md text-navy hover:bg-surface disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Previous
            </button>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm border border-border rounded-md text-navy hover:bg-surface disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
            >
              Next
            </button>
          </div>
        </div>
      )}

      {showCreate && <CreateUserModal onClose={() => setShowCreate(false)} />}
    </div>
  )
}

function CreateUserModal({ onClose }: { onClose: () => void }) {
  const createUser = useCreateUser()
  const [form, setForm] = useState({ email: '', password: '', username: '', displayName: '' })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    createUser.mutate(
      {
        email: form.email,
        password: form.password,
        username: form.username || undefined,
        displayName: form.displayName || undefined,
      },
      { onSuccess: onClose },
    )
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
      <div className="bg-white rounded-lg shadow-lg w-full max-w-md mx-4">
        <div className="px-6 py-4 border-b border-border">
          <h2 className="text-lg font-semibold text-navy">Create user</h2>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="px-6 py-4 space-y-4">
            <Field label="Email" required>
              <input
                type="email"
                required
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className="w-full px-3 py-2 border border-border rounded-md text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/30 focus:border-purple"
              />
            </Field>
            <Field label="Password" required>
              <input
                type="password"
                required
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                className="w-full px-3 py-2 border border-border rounded-md text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/30 focus:border-purple"
              />
            </Field>
            <Field label="Username">
              <input
                type="text"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
                className="w-full px-3 py-2 border border-border rounded-md text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/30 focus:border-purple"
              />
            </Field>
            <Field label="Display name">
              <input
                type="text"
                value={form.displayName}
                onChange={(e) => setForm({ ...form, displayName: e.target.value })}
                className="w-full px-3 py-2 border border-border rounded-md text-sm text-navy focus:outline-none focus:ring-2 focus:ring-purple/30 focus:border-purple"
              />
            </Field>
            {createUser.isError && (
              <p className="text-sm text-ruby">Failed to create user. Please try again.</p>
            )}
          </div>
          <div className="px-6 py-4 border-t border-border flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm border border-border rounded-md text-navy hover:bg-surface transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={createUser.isPending}
              className="px-4 py-2 text-sm bg-purple text-white rounded-md font-medium hover:bg-purple/90 disabled:opacity-60 transition-colors"
            >
              {createUser.isPending ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-navy">
        {label}
        {required && <span className="text-ruby ml-0.5">*</span>}
      </span>
      <div className="mt-1">{children}</div>
    </label>
  )
}
