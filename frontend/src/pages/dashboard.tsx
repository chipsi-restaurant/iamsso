import { Users, Shield, Key, FileCheck } from 'lucide-react'
import { useAuth } from '@/lib/auth-context'
import { useUsers } from '@/api/users'
import { useRoles, usePolicies } from '@/api/policies'
import { useClients } from '@/api/clients'
import { useServiceHealth } from '@/api/health'

interface StatCardProps {
  label: string
  value: number | undefined
  icon: React.ReactNode
  loading: boolean
}

function StatCard({ label, value, icon, loading }: StatCardProps) {
  return (
    <div className="bg-white border border-border rounded-md p-5">
      <div className="flex items-center justify-between mb-3">
        <span className="text-sm font-medium text-body">{label}</span>
        <span className="text-purple">{icon}</span>
      </div>
      <p className="text-2xl font-semibold text-navy">
        {loading ? '—' : (value ?? 0)}
      </p>
    </div>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const users = useUsers(0, 1)
  const roles = useRoles()
  const policies = usePolicies(0, 1)
  const clients = useClients()
  const health = useServiceHealth()

  const stats: StatCardProps[] = [
    { label: 'Users', value: users.data?.totalElements, icon: <Users size={20} />, loading: users.isLoading },
    { label: 'Roles', value: roles.data?.length, icon: <Shield size={20} />, loading: roles.isLoading },
    { label: 'Policies', value: policies.data?.totalElements, icon: <FileCheck size={20} />, loading: policies.isLoading },
    { label: 'OAuth Clients', value: clients.data?.length, icon: <Key size={20} />, loading: clients.isLoading },
  ]

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-navy">Dashboard</h1>
        <p className="text-body mt-1">
          Welcome back, {user?.email ?? user?.sub ?? 'Admin'}
        </p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((s) => (
          <StatCard key={s.label} {...s} />
        ))}
      </div>

      <div>
        <h2 className="text-lg font-semibold text-navy mb-3">Service Health</h2>
        <div className="bg-white border border-border rounded-md overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-surface">
                <th className="text-left px-4 py-2.5 font-medium text-body">Service</th>
                <th className="text-left px-4 py-2.5 font-medium text-body">Port</th>
                <th className="text-left px-4 py-2.5 font-medium text-body">Status</th>
              </tr>
            </thead>
            <tbody>
              {health.isLoading ? (
                <tr>
                  <td colSpan={3} className="px-4 py-4 text-center text-body">
                    Checking services...
                  </td>
                </tr>
              ) : (
                health.data?.map((svc) => (
                  <tr key={svc.name} className="border-b border-border last:border-b-0">
                    <td className="px-4 py-3 text-navy font-medium">{svc.name}</td>
                    <td className="px-4 py-3 text-body">{svc.port}</td>
                    <td className="px-4 py-3">
                      <span className="inline-flex items-center gap-1.5">
                        <span
                          className={`inline-block h-2 w-2 rounded-full ${
                            svc.status === 'up' ? 'bg-green-text' : 'bg-gray-300'
                          }`}
                        />
                        <span className={svc.status === 'up' ? 'text-green-text' : 'text-body'}>
                          {svc.status === 'up' ? 'Healthy' : 'Unreachable'}
                        </span>
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
