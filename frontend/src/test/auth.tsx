import { render as renderizarBase } from '@testing-library/react'
import type { ReactElement } from 'react'
import { vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../hooks/useAuth'
import type { PerfilAcesso } from '../types'

// Renderiza a tela com um usuário autenticado; o perfil padrão é o do operador, que cadastra a rede
export function render(ui: ReactElement, { perfil = 'OPERADOR' }: { perfil?: PerfilAcesso } = {}) {
  const auth: AuthContextValue = {
    usuario: { id: 7, nome: 'Usuário de teste', email: 'teste@powergrid.local', perfil },
    carregando: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  return renderizarBase(<AuthContext.Provider value={auth}>{ui}</AuthContext.Provider>)
}
