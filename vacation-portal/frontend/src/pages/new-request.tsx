import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { useCreateRequest, type VacationRequestType } from '@/api/requests'
import { typeLabel } from '@/lib/format'

export default function NewRequestPage() {
  const navigate = useNavigate()
  const create = useCreateRequest()
  const [form, setForm] = useState<{
    type: VacationRequestType
    startDate: string
    endDate: string
    reason: string
  }>({
    type: 'VACATION',
    startDate: '',
    endDate: '',
    reason: '',
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    create.mutate(form, { onSuccess: () => navigate('/my-requests') })
  }

  return (
    <div className="max-w-2xl">
      <button
        onClick={() => navigate(-1)}
        className="inline-flex items-center gap-2 text-sm text-body hover:text-navy mb-4"
      >
        <ArrowLeft size={16} /> Назад
      </button>
      <h1 className="text-2xl font-semibold text-navy mb-6">Новая заявка</h1>

      <form onSubmit={handleSubmit} className="bg-white border border-border rounded-lg p-6 space-y-4">
        <Field label="Тип" required>
          <select
            value={form.type}
            onChange={e => setForm({ ...form, type: e.target.value as VacationRequestType })}
            className="w-full px-3 py-2 border border-border rounded-md text-sm bg-white"
          >
            {(['VACATION', 'SICK_LEAVE', 'BUSINESS_TRIP', 'OTHER'] as const).map(t => (
              <option key={t} value={t}>{typeLabel[t]}</option>
            ))}
          </select>
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label="Дата начала" required>
            <input
              type="date"
              required
              value={form.startDate}
              onChange={e => setForm({ ...form, startDate: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md text-sm"
            />
          </Field>
          <Field label="Дата окончания" required>
            <input
              type="date"
              required
              value={form.endDate}
              onChange={e => setForm({ ...form, endDate: e.target.value })}
              className="w-full px-3 py-2 border border-border rounded-md text-sm"
            />
          </Field>
        </div>
        <Field label="Причина / комментарий" required>
          <textarea
            required
            rows={4}
            value={form.reason}
            onChange={e => setForm({ ...form, reason: e.target.value })}
            className="w-full px-3 py-2 border border-border rounded-md text-sm resize-none"
          />
        </Field>

        {create.isError && (
          <p className="text-sm text-ruby">Не удалось создать заявку. Проверьте даты и попробуйте ещё раз.</p>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="px-4 py-2 text-sm border border-border rounded-md"
          >
            Отмена
          </button>
          <button
            type="submit"
            disabled={create.isPending}
            className="px-4 py-2 text-sm bg-purple text-white rounded-md font-medium hover:bg-purple-hover disabled:opacity-60"
          >
            {create.isPending ? 'Отправка…' : 'Отправить'}
          </button>
        </div>
      </form>
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
