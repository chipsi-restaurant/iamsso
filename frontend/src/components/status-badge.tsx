import { cn } from '@/lib/utils'

const statusStyles: Record<string, string> = {
  ACTIVE: 'bg-green/15 text-green-text border-green/30',
  PENDING_VERIFICATION: 'bg-yellow-50 text-yellow-700 border-yellow-200',
  LOCKED: 'bg-red-50 text-ruby border-red-200',
  SUSPENDED: 'bg-red-50 text-ruby border-red-200',
  DELETED: 'bg-gray-100 text-gray-500 border-gray-200',
  ALLOW: 'bg-green/15 text-green-text border-green/30',
  DENY: 'bg-red-50 text-ruby border-red-200',
}

export default function StatusBadge({ status }: { status: string }) {
  return (
    <span className={cn('inline-flex items-center px-2 py-0.5 rounded text-xs font-medium border', statusStyles[status] || 'bg-gray-100 text-gray-500 border-gray-200')}>
      {status}
    </span>
  )
}
