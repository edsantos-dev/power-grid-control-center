import { useState } from 'react'
import { LogOut } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { PERFIL_LABEL } from '../../utils/constants'

function Header() {
  const { usuario, logout } = useAuth()
  const [erro, setErro] = useState('')

  async function handleLogout() {
    setErro('')

    try {
      await logout()
    } catch {
      setErro('Não foi possível sair. Tente novamente.')
    }
  }

  return (
    <header className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 bg-slate-900 px-6 py-4">
      <h1 className="text-xl font-bold">
        Power Grid Control Center
      </h1>

      {usuario && (
        <div className="flex items-center gap-4">
          {erro && (
            <p className="text-sm text-red-400">
              {erro}
            </p>
          )}

          <div className="text-right">
            <p className="text-sm font-medium text-white">
              {usuario.nome}
            </p>

            <p className="text-xs text-slate-400">
              {PERFIL_LABEL[usuario.perfil]}
            </p>
          </div>

          <button
            type="button"
            onClick={handleLogout}
            className="flex items-center gap-2 rounded-lg border border-slate-700 px-3 py-2 text-sm text-slate-300 transition hover:bg-slate-800 hover:text-white"
          >
            <LogOut className="h-4 w-4" />
            Sair
          </button>
        </div>
      )}
    </header>
  )
}

export default Header
