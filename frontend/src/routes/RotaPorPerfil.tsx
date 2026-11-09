import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { podeAcessarTela } from '../utils/acesso'

// Quem digita o endereço de uma tela fora do seu perfil volta ao dashboard
function RotaPorPerfil() {
  const { usuario } = useAuth()
  const { pathname } = useLocation()

  if (!podeAcessarTela(usuario, pathname)) {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}

export default RotaPorPerfil
