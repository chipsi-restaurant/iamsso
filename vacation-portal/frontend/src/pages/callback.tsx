import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { exchangeCodeForToken, storeTokens } from '@/lib/auth'
import { useAuth } from '@/lib/auth-context'

export default function CallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { setToken } = useAuth()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = params.get('code')
    const state = params.get('state')
    const storedState = sessionStorage.getItem('pkce_state')
    const verifier = sessionStorage.getItem('pkce_verifier')

    if (!code || !verifier || state !== storedState) {
      setError('Некорректные параметры авторизации')
      return
    }

    exchangeCodeForToken(code, verifier)
      .then(data => {
        storeTokens(data.access_token, data.refresh_token)
        setToken(data.access_token)
        sessionStorage.removeItem('pkce_verifier')
        sessionStorage.removeItem('pkce_state')
        navigate('/my-requests', { replace: true })
      })
      .catch(() => setError('Не удалось получить токен'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="text-center">
        {error ? (
          <>
            <p className="text-ruby font-medium">{error}</p>
            <button
              onClick={() => navigate('/login')}
              className="mt-4 px-4 py-2 bg-purple text-white rounded text-sm"
            >
              Вернуться к входу
            </button>
          </>
        ) : (
          <p className="text-body">Завершение входа…</p>
        )}
      </div>
    </div>
  )
}
