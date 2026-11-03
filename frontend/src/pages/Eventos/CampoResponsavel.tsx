import { useAuth } from '../../hooks/useAuth'
import type { Responsavel } from './responsavel'

type Props = {
  valor: Responsavel
  aoAlterar: (valor: Responsavel) => void
  erro?: string
}

function CampoResponsavel({ valor, aoAlterar, erro }: Props) {
  const { usuario } = useAuth()

  return (
    <div>
      <label htmlFor="responsavel" className="mb-2 block text-sm font-medium text-slate-300">
        Responsável
      </label>

      <select
        id="responsavel"
        value={valor.modo}
        onChange={(e) => aoAlterar({ ...valor, modo: e.target.value as Responsavel['modo'] })}
        className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
      >
        <option value="eu">Eu ({usuario?.nome})</option>
        <option value="outro">Outro usuário (pelo ID)</option>
      </select>

      {valor.modo === 'outro' && (
        <input
          aria-label="ID do responsável"
          inputMode="numeric"
          placeholder="ID do usuário"
          value={valor.outroId}
          onChange={(e) => aoAlterar({ ...valor, outroId: e.target.value })}
          className="mt-2 w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none placeholder:text-slate-500 focus:border-blue-500"
        />
      )}

      {erro && <p className="mt-1 text-xs text-red-400">{erro}</p>}
    </div>
  )
}

export default CampoResponsavel
