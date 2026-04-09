import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { exchangeCodeForToken, storeTokens } from '@/lib/auth'
import { useAuth } from '@/lib/auth-context'

export default function CallbackPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { setToken } = useAuth()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = searchParams.get('code')
    const state = searchParams.get('state')
    const storedState = sessionStorage.getItem('pkce_state')
    const codeVerifier = sessionStorage.getItem('pkce_verifier')

    if (!code || !codeVerifier) {
      setError('Отсутствует код авторизации или PKCE verifier')
      return
    }
    if (state !== storedState) {
      setError('Несоответствие state — возможная CSRF-атака')
      return
    }

    exchangeCodeForToken(code, codeVerifier)
      .then((tokens) => {
        storeTokens(tokens.access_token, tokens.refresh_token)
        setToken(tokens.access_token)
        sessionStorage.removeItem('pkce_verifier')
        sessionStorage.removeItem('pkce_state')
        navigate('/', { replace: true })
      })
      .catch((err) => {
        setError(`Ошибка обмена токена: ${err.message}`)
      })
  }, [])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center">
          <p className="text-ruby text-sm mb-4">{error}</p>
          <a href="/login" className="text-purple text-sm hover:underline">Вернуться к входу</a>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <p className="text-body text-sm">Авторизация...</p>
    </div>
  )
}
