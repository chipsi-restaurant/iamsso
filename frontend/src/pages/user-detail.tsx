import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Trash2 } from 'lucide-react'
import { useUser, useUserProfile, useChangeStatus, useDeleteUser } from '@/api/users'
import StatusBadge from '@/components/status-badge'

const STATUSES = ['ACTIVE', 'PENDING_VERIFICATION', 'LOCKED', 'SUSPENDED', 'DELETED']

export default function UserDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { data: user, isLoading: userLoading } = useUser(id!)
  const { data: profile, isLoading: profileLoading } = useUserProfile(id!)
  const changeStatus = useChangeStatus(id!)
  const deleteUser = useDeleteUser(id!)

  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)

  const handleStatusChange = (newStatus: string) => {
    if (newStatus === user?.status) return
    changeStatus.mutate({ status: newStatus, reason: 'Changed by admin' })
  }

  const handleDelete = () => {
    deleteUser.mutate(undefined, {
      onSuccess: () => navigate('/users'),
    })
  }

  if (userLoading || profileLoading) {
    return <p className="text-body text-sm">Loading...</p>
  }

  if (!user) {
    return <p className="text-body text-sm">User not found.</p>
  }

  return (
    <div>
      <button
        onClick={() => navigate('/users')}
        className="inline-flex items-center gap-1.5 text-sm text-body hover:text-navy transition-colors mb-6"
      >
        <ArrowLeft size={16} />
        Back to users
      </button>

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold text-navy">{user.email ?? user.username ?? 'User'}</h1>
          <p className="text-sm text-body mt-1">ID: {user.id}</p>
        </div>
        <button
          onClick={() => setShowDeleteConfirm(true)}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm text-ruby border border-red-200 rounded-md hover:bg-red-50 transition-colors"
        >
          <Trash2 size={16} />
          Delete
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Account info card */}
        <div className="bg-white border border-border rounded-lg">
          <div className="px-6 py-4 border-b border-border">
            <h2 className="text-sm font-semibold text-navy">Account</h2>
          </div>
          <div className="px-6 py-4 space-y-3">
            <InfoRow label="Email" value={user.email ?? '—'} />
            <InfoRow label="Username" value={user.username ?? '—'} />
            <InfoRow label="Role" value={user.role} />
            <div className="flex justify-between items-center">
              <span className="text-sm text-body">Status</span>
              <div className="flex items-center gap-2">
                <StatusBadge status={user.status} />
                <select
                  value={user.status}
                  onChange={(e) => handleStatusChange(e.target.value)}
                  disabled={changeStatus.isPending}
                  className="text-xs border border-border rounded px-2 py-1 text-navy focus:outline-none focus:ring-2 focus:ring-purple/30 focus:border-purple"
                >
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </div>
            </div>
            <InfoRow label="Email verified" value={user.emailVerified ? 'Yes' : 'No'} />
            <InfoRow label="MFA enabled" value={user.mfaEnabled ? 'Yes' : 'No'} />
            <InfoRow label="Locale" value={user.locale} />
            <InfoRow label="Created" value={new Date(user.createdAt).toLocaleString()} />
          </div>
        </div>

        {/* Profile card */}
        <div className="bg-white border border-border rounded-lg">
          <div className="px-6 py-4 border-b border-border">
            <h2 className="text-sm font-semibold text-navy">Profile</h2>
          </div>
          <div className="px-6 py-4 space-y-3">
            <InfoRow label="Display name" value={profile?.displayName ?? '—'} />
            <InfoRow label="First name" value={profile?.firstName ?? '—'} />
            <InfoRow label="Last name" value={profile?.lastName ?? '—'} />
            <InfoRow label="Timezone" value={profile?.timezone ?? '—'} />
            <InfoRow label="Avatar URL" value={profile?.avatarUrl ?? '—'} />
          </div>
        </div>
      </div>

      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white rounded-lg shadow-lg w-full max-w-sm mx-4">
            <div className="px-6 py-4 border-b border-border">
              <h2 className="text-lg font-semibold text-navy">Delete user</h2>
            </div>
            <div className="px-6 py-4">
              <p className="text-sm text-body">
                Are you sure you want to delete <strong className="text-navy">{user.email ?? user.username}</strong>? This action cannot be undone.
              </p>
              {deleteUser.isError && (
                <p className="text-sm text-ruby mt-2">Failed to delete user. Please try again.</p>
              )}
            </div>
            <div className="px-6 py-4 border-t border-border flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(false)}
                className="px-4 py-2 text-sm border border-border rounded-md text-navy hover:bg-surface transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                disabled={deleteUser.isPending}
                className="px-4 py-2 text-sm bg-ruby text-white rounded-md font-medium hover:bg-ruby/90 disabled:opacity-60 transition-colors"
              >
                {deleteUser.isPending ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between items-center">
      <span className="text-sm text-body">{label}</span>
      <span className="text-sm text-navy">{value}</span>
    </div>
  )
}
