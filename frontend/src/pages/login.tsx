import { generatePKCE, getLoginUrl } from '@/lib/auth'
import { LogIn } from 'lucide-react'

export default function LoginPage() {
  const handleLogin = async () => {
    const { verifier, challenge } = await generatePKCE()
    const state = crypto.randomUUID()
    sessionStorage.setItem('pkce_verifier', verifier)
    sessionStorage.setItem('pkce_state', state)
    window.location.href = getLoginUrl(challenge, state)
  }

  return (
    <div className="min-h-screen bg-white flex items-center justify-center">
      <div className="w-full max-w-sm mx-auto text-center">
        <h1 className="text-2xl font-light text-navy mb-2">iam<span className="text-purple">sso</span></h1>
        <p className="text-body text-sm mb-8">Policy-based access management</p>
        <div className="border border-border rounded-md p-8 bg-white shadow-[rgba(50,50,93,0.25)_0px_30px_45px_-30px,rgba(0,0,0,0.1)_0px_18px_36px_-18px]">
          <h2 className="text-lg font-light text-navy mb-6">Sign in to console</h2>
          <button onClick={handleLogin} className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded bg-purple text-white text-sm font-medium hover:bg-purple-hover transition-colors">
            <LogIn size={16} /> Sign in with OAuth
          </button>
        </div>
        <p className="text-xs text-body mt-6">Secured with OAuth 2.0 + PKCE</p>
      </div>
    </div>
  )
}
