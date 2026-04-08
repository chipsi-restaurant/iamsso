import { useState } from 'react'
import { LogOut, Monitor } from 'lucide-react'
import { useAuth } from '@/lib/auth-context'
import { useCurrentSession, useLogout, useLogoutAll } from '@/api/sessions'

function fmt(iso: string) {
  return new Date(iso).toLocaleString()
}

export default function SessionsPage() {
  const { user, logout: localLogout } = useAuth()
  const session = useCurrentSession()
  const logoutMut = useLogout()
  const logoutAllMut = useLogoutAll()
  const [confirmAll, setConfirmAll] = useState(false)

  const handleLogout = () => {
    logoutMut.mutate(undefined, {
      onSettled: () => localLogout(),
    })
  }

  const handleLogoutAll = () => {
    logoutAllMut.mutate(undefined, {
      onSettled: () => localLogout(),
    })
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-navy">Sessions</h1>
        <p className="text-body mt-1">Manage your active session and sign out.</p>
      </div>

      {/* Current session card */}
      <div className="bg-white border border-border rounded-md p-6">
        <div className="flex items-center gap-2 mb-5">
          <Monitor size={20} className="text-purple" />
          <h2 className="text-lg font-semibold text-navy">Current Session</h2>
        </div>

        {session.isLoading ? (
          <p className="text-body text-sm">Loading session info...</p>
        ) : session.isError ? (
          <p className="text-body text-sm">
            Session details are unavailable. You are authenticated locally but the
            server session could not be retrieved.
          </p>
        ) : session.data ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-3 text-sm">
            <div>
              <span className="text-body font-medium">User ID</span>
              <p className="text-navy mt-0.5 break-all">{user?.sub ?? '—'}</p>
            </div>
            <div>
              <span className="text-body font-medium">Role</span>
              <p className="text-navy mt-0.5">{user?.role ?? '—'}</p>
            </div>
            <div>
              <span className="text-body font-medium">Scope</span>
              <p className="text-navy mt-0.5">{user?.scope ?? '—'}</p>
            </div>
            <div>
              <span className="text-body font-medium">Session ID</span>
              <p className="text-navy mt-0.5 break-all font-mono text-xs">
                {session.data.sessionId}
              </p>
            </div>
            <div>
              <span className="text-body font-medium">Created</span>
              <p className="text-navy mt-0.5">{fmt(session.data.createdAt)}</p>
            </div>
            <div>
              <span className="text-body font-medium">Expires</span>
              <p className="text-navy mt-0.5">{fmt(session.data.expiresAt)}</p>
            </div>
          </div>
        ) : null}
      </div>

      {/* Actions */}
      <div className="flex flex-wrap gap-3">
        <button
          onClick={handleLogout}
          disabled={logoutMut.isPending}
          className="inline-flex items-center gap-2 rounded-md border border-border bg-white px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors disabled:opacity-50"
        >
          <LogOut size={16} />
          {logoutMut.isPending ? 'Signing out...' : 'Logout'}
        </button>

        {!confirmAll ? (
          <button
            onClick={() => setConfirmAll(true)}
            className="inline-flex items-center gap-2 rounded-md bg-red-50 border border-red-200 px-4 py-2 text-sm font-medium text-red-700 hover:bg-red-100 transition-colors"
          >
            <LogOut size={16} />
            Logout all devices
          </button>
        ) : (
          <div className="inline-flex items-center gap-2">
            <span className="text-sm text-red-700">Are you sure?</span>
            <button
              onClick={handleLogoutAll}
              disabled={logoutAllMut.isPending}
              className="rounded-md bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 transition-colors disabled:opacity-50"
            >
              {logoutAllMut.isPending ? 'Signing out...' : 'Yes, logout everywhere'}
            </button>
            <button
              onClick={() => setConfirmAll(false)}
              className="rounded-md border border-border bg-white px-4 py-2 text-sm font-medium text-navy hover:bg-surface transition-colors"
            >
              Cancel
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
