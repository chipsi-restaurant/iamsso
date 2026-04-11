import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from '@/lib/auth-context'
import ProtectedRoute from '@/components/layout/protected-route'
import SidebarLayout from '@/components/layout/sidebar'
import LoginPage from '@/pages/login'
import CallbackPage from '@/pages/callback'
import DashboardPage from '@/pages/dashboard'
import UsersPage from '@/pages/users'
import UserDetailPage from '@/pages/user-detail'
import PoliciesPage from '@/pages/policies'
import ClientsPage from '@/pages/clients'
import SessionsPage from '@/pages/sessions'
import MfaPage from '@/pages/mfa'
import AuditPage from '@/pages/audit'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/callback" element={<CallbackPage />} />
            <Route element={<ProtectedRoute><SidebarLayout /></ProtectedRoute>}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/users" element={<UsersPage />} />
              <Route path="/users/:id" element={<UserDetailPage />} />
              <Route path="/policies" element={<PoliciesPage />} />
              <Route path="/clients" element={<ClientsPage />} />
              <Route path="/sessions" element={<SessionsPage />} />
              <Route path="/mfa" element={<MfaPage />} />
              <Route path="/audit" element={<AuditPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}
