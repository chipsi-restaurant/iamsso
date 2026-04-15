import type { VacationRequestStatus, VacationRequestType } from '@/api/requests'

export const typeLabel: Record<VacationRequestType, string> = {
  VACATION: 'Отпуск',
  SICK_LEAVE: 'Больничный',
  BUSINESS_TRIP: 'Командировка',
  OTHER: 'Иное',
}

export const statusLabel: Record<VacationRequestStatus, string> = {
  PENDING: 'На рассмотрении',
  APPROVED: 'Одобрено',
  REJECTED: 'Отклонено',
  CANCELLED: 'Отменено',
}

export const statusColors: Record<VacationRequestStatus, string> = {
  PENDING: 'bg-yellow-100 text-yellow-800',
  APPROVED: 'bg-green-100 text-green-800',
  REJECTED: 'bg-red-100 text-red-800',
  CANCELLED: 'bg-gray-100 text-gray-700',
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU')
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU')
}
