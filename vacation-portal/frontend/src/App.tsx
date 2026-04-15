import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './pages/login'
import CallbackPage from './pages/callback'
import MyRequestsPage from './pages/my-requests'
import NewRequestPage from './pages/new-request'
import AllRequestsPage from './pages/all-requests'
import Layout from './components/layout/layout'
import ProtectedRoute from './components/protected-route'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/callback" element={<CallbackPage />} />
      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Navigate to="/my-requests" replace />} />
        <Route path="/my-requests" element={<MyRequestsPage />} />
        <Route path="/new-request" element={<NewRequestPage />} />
        <Route path="/all-requests" element={<AllRequestsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
