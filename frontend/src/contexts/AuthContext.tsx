import { useEffect, useState, type ReactNode } from 'react'
import { AuthContext } from '../hooks/useAuth'
import * as authApi from '../services/api'
import type { Usuario } from '../types'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [usuario, setUsuario] = useState<Usuario | null>(null)
  const [carregando, setCarregando] = useState(true)

  useEffect(() => {
    authApi.definirAoExpirarSessao(() => setUsuario(null))

    authApi
      .consultarUsuarioAutenticado()
      .then(setUsuario)
      .catch(() => setUsuario(null))
      .finally(() => setCarregando(false))
  }, [])

  async function login(email: string, senha: string) {
    setUsuario(await authApi.login(email, senha))
  }

  async function logout() {
    await authApi.logout()
    setUsuario(null)
  }

  return (
    <AuthContext.Provider value={{ usuario, carregando, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}
