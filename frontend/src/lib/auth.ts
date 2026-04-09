const GATEWAY_URL = 'http://localhost:8090'
const CLIENT_ID = 'demo-app'
const REDIRECT_URI = 'http://localhost:3000/callback'

function base64UrlEncode(buffer: ArrayBuffer | Uint8Array): string {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer)
  let binary = ''
  bytes.forEach(b => binary += String.fromCharCode(b))
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export async function generatePKCE() {
  const verifier = base64UrlEncode(crypto.getRandomValues(new Uint8Array(32)))
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  const challenge = base64UrlEncode(digest)
  return { verifier, challenge }
}

export function getLoginUrl(codeChallenge: string, state: string): string {
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    scope: 'openid profile email',
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    state,
  })
  return `${GATEWAY_URL}/oauth2/authorize?${params}`
}

export async function exchangeCodeForToken(code: string, codeVerifier: string) {
  const res = await fetch(`${GATEWAY_URL}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: REDIRECT_URI,
      code_verifier: codeVerifier,
      client_id: CLIENT_ID,
      client_secret: 'demo-secret',
    }),
  })
  if (!res.ok) throw new Error('Token exchange failed')
  return res.json() as Promise<{
    access_token: string
    refresh_token?: string
    id_token?: string
    expires_in: number
    scope: string
  }>
}

export function parseJwt(token: string): Record<string, unknown> {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(atob(base64))
}

export function getStoredToken(): string | null {
  return localStorage.getItem('access_token')
}

export function storeTokens(accessToken: string, refreshToken?: string) {
  localStorage.setItem('access_token', accessToken)
  if (refreshToken) localStorage.setItem('refresh_token', refreshToken)
}

export function clearTokens() {
  localStorage.removeItem('access_token')
  localStorage.removeItem('refresh_token')
  sessionStorage.removeItem('pkce_verifier')
  sessionStorage.removeItem('pkce_state')
}
