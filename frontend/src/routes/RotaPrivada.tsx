import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

function RotaPrivada() {
  const { usuario, carregando } = useAuth()

  if (carregando) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-950 text-sm text-slate-400">
        Verificando sessão...
      </main>
    )
  }

  if (!usuario) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}

export default RotaPrivada
