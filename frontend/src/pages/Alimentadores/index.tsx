import { useState } from 'react'
import { Search, MapPin, Zap, TrendingDown } from 'lucide-react'

type StatusAlimentador = 'operacional' | 'atencao' | 'falha' | 'manutencao'

interface Alimentador {
  id: string
  nome: string
  subestacao: string
  tensao: number
  potenciaAtual: number
  perdas: number
  consumidoresAtendidos: number
  status: StatusAlimentador
}

const alimentadores: Alimentador[] = [
  { id: 'AL-101', nome: 'Alimentador Norte-1', subestacao: 'Subestação Norte', tensao: 13.8, potenciaAtual: 34.2, perdas: 3.1, consumidoresAtendidos: 4820, status: 'operacional' },
  { id: 'AL-102', nome: 'Alimentador Central-1', subestacao: 'Subestação Central', tensao: 13.8, potenciaAtual: 48.7, perdas: 5.8, consumidoresAtendidos: 6210, status: 'atencao' },
  { id: 'AL-103', nome: 'Alimentador Sul-1', subestacao: 'Subestação Sul', tensao: 13.8, potenciaAtual: 29.5, perdas: 2.4, consumidoresAtendidos: 3940, status: 'operacional' },
  { id: 'AL-104', nome: 'Alimentador Leste-1', subestacao: 'Subestação Leste', tensao: 13.8, potenciaAtual: 12.1, perdas: 4.0, consumidoresAtendidos: 1780, status: 'operacional' },
  { id: 'AL-105', nome: 'Alimentador Oeste-1', subestacao: 'Subestação Oeste', tensao: 13.8, potenciaAtual: 0, perdas: 0, consumidoresAtendidos: 2650, status: 'falha' },
  { id: 'AL-106', nome: 'Alimentador Vale Verde-1', subestacao: 'Subestação Vale Verde', tensao: 13.8, potenciaAtual: 21.4, perdas: 3.6, consumidoresAtendidos: 3120, status: 'operacional' },
  { id: 'AL-107', nome: 'Alimentador Porto Alto-1', subestacao: 'Subestação Porto Alto', tensao: 13.8, potenciaAtual: 0, perdas: 0, consumidoresAtendidos: 2890, status: 'manutencao' },
]

const statusStyles: Record<StatusAlimentador, { dot: string; text: string; label: string }> = {
  operacional: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Atenção' },
  falha: { dot: 'bg-red-500', text: 'text-red-400', label: 'Falha' },
  manutencao: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

function Alimentadores() {
  const [filtroStatus, setFiltroStatus] = useState<StatusAlimentador | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const alimentadoresFiltrados = alimentadores.filter((a) => {
    const passaStatus = filtroStatus === 'todos' || a.status === filtroStatus
    const passaBusca = a.nome.toLowerCase().includes(busca.toLowerCase()) || a.subestacao.toLowerCase().includes(busca.toLowerCase())
    return passaStatus && passaBusca
  })

  const totalOperacionais = alimentadores.filter((a) => a.status === 'operacional').length
  const totalAtencao = alimentadores.filter((a) => a.status === 'atencao').length
  const totalFalha = alimentadores.filter((a) => a.status === 'falha').length
  const consumidoresTotal = alimentadores.reduce((acc, a) => acc + a.consumidoresAtendidos, 0)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Alimentadores
        </h2>

        <p className="mt-2 text-slate-400">
          Cadastro e monitoramento dos alimentadores de distribuição.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {alimentadores.length} alimentadores</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em atenção</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAtencao}</p>
          <p className="mt-2 text-sm text-yellow-400">Perdas elevadas</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em falha</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalFalha}</p>
          <p className="mt-2 text-sm text-red-400">Ação necessária</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Consumidores Atendidos</p>
          <p className="mt-2 text-3xl font-bold text-white">{consumidoresTotal.toLocaleString('pt-BR')}</p>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Buscar alimentador ou subestação..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'operacional', 'atencao', 'falha', 'manutencao'] as const).map((status) => (
              <button
                key={status}
                onClick={() => setFiltroStatus(status)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroStatus === status
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {status === 'todos' ? 'Todos' : statusStyles[status].label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Alimentador</th>
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Potência</th>
                <th className="pb-3 font-normal">Perdas</th>
                <th className="pb-3 font-normal">Consumidores</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {alimentadoresFiltrados.map((a) => {
                const status = statusStyles[a.status]

                return (
                  <tr key={a.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{a.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">{a.id}</p>
                    </td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {a.subestacao}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Zap className="h-3.5 w-3.5 text-blue-400" />
                        {a.potenciaAtual} MW
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <TrendingDown className="h-3.5 w-3.5 text-yellow-400" />
                        {a.perdas}%
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{a.consumidoresAtendidos.toLocaleString('pt-BR')}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {alimentadoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum alimentador encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Alimentadores