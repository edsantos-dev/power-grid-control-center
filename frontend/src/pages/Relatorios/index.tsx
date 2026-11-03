import { Lock } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import HistoricoEnergia from './HistoricoEnergia'
import PerdasTecnicas from './PerdasTecnicas'

function Relatorios() {
  const { usuario } = useAuth()

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Relatórios
        </h2>

        <p className="mt-2 text-slate-400">
          Consultas calculadas a partir das medições registradas.
        </p>
      </div>

      <PerdasTecnicas />

      {/* O backend restringe os históricos ao gerente */}
      {usuario?.perfil === 'GERENTE_OPERACOES' ? (
        <HistoricoEnergia />
      ) : (
        <p className="mt-6 flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400">
          <Lock className="h-4 w-4" />
          O histórico de energia, perdas e consumo está disponível para o perfil Gerente de Operações.
        </p>
      )}
    </div>
  )
}

export default Relatorios
