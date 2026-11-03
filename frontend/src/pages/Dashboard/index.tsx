import { Lock } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import IndicadoresOperacionais from './IndicadoresOperacionais'
import MapaAtivos from './MapaAtivos'

function Dashboard() {
  const { usuario } = useAuth()

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Dashboard
        </h2>

        <p className="mt-2 text-slate-400">
          Visão geral da operação da rede elétrica.
        </p>
      </div>

      <MapaAtivos />

      {/* O backend restringe os indicadores ao gerente */}
      {usuario?.perfil === 'GERENTE_OPERACOES' ? (
        <IndicadoresOperacionais />
      ) : (
        <p className="mt-6 flex items-center gap-2 rounded-xl border border-slate-800 bg-slate-900 p-6 text-sm text-slate-400">
          <Lock className="h-4 w-4" />
          Os indicadores de disponibilidade e desempenho estão disponíveis para o perfil Gerente de Operações.
        </p>
      )}
    </div>
  )
}

export default Dashboard
