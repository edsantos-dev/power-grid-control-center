import { useState } from 'react'
import { Search, MapPin, Gauge } from 'lucide-react'

type StatusTransformador = 'operacional' | 'atencao' | 'falha' | 'manutencao'

interface Transformador {
  id: string
  nome: string
  subestacao: string
  potenciaNominal: number
  cargaAtual: number
  temperatura: number
  status: StatusTransformador
}

const transformadores: Transformador[] = [
  { id: 'T-01', nome: 'Transformador T-01', subestacao: 'Subestação Norte', potenciaNominal: 100, cargaAtual: 72, temperatura: 58, status: 'operacional' },
  { id: 'T-02', nome: 'Transformador T-02', subestacao: 'Subestação Central', potenciaNominal: 80, cargaAtual: 91, temperatura: 74, status: 'atencao' },
  { id: 'T-03', nome: 'Transformador T-03', subestacao: 'Subestação Sul', potenciaNominal: 60, cargaAtual: 58, temperatura: 52, status: 'operacional' },
  { id: 'T-14', nome: 'Transformador T-14', subestacao: 'Subestação Leste', potenciaNominal: 45, cargaAtual: 108, temperatura: 89, status: 'atencao' },
  { id: 'T-05', nome: 'Transformador T-05', subestacao: 'Subestação Oeste', potenciaNominal: 100, cargaAtual: 0, temperatura: 24, status: 'falha' },
  { id: 'T-06', nome: 'Transformador T-06', subestacao: 'Subestação Vale Verde', potenciaNominal: 60, cargaAtual: 64, temperatura: 55, status: 'operacional' },
  { id: 'T-07', nome: 'Transformador T-07', subestacao: 'Subestação Porto Alto', potenciaNominal: 80, cargaAtual: 0, temperatura: 22, status: 'manutencao' },
]

const statusStyles: Record<StatusTransformador, { dot: string; text: string; label: string }> = {
  operacional: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Atenção' },
  falha: { dot: 'bg-red-500', text: 'text-red-400', label: 'Falha' },
  manutencao: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

function Transformadores() {
  const [filtroStatus, setFiltroStatus] = useState<StatusTransformador | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const transformadoresFiltrados = transformadores.filter((t) => {
    const passaStatus = filtroStatus === 'todos' || t.status === filtroStatus
    const passaBusca = t.nome.toLowerCase().includes(busca.toLowerCase()) || t.subestacao.toLowerCase().includes(busca.toLowerCase())
    return passaStatus && passaBusca
  })

  const totalOperacionais = transformadores.filter((t) => t.status === 'operacional').length
  const totalAtencao = transformadores.filter((t) => t.status === 'atencao').length
  const totalFalha = transformadores.filter((t) => t.status === 'falha').length
  const cargaMedia = (transformadores.reduce((acc, t) => acc + t.cargaAtual, 0) / transformadores.length).toFixed(0)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Transformadores
        </h2>

        <p className="mt-2 text-slate-400">
          Cadastro e monitoramento dos transformadores da rede elétrica.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {transformadores.length} transformadores</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em atenção</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAtencao}</p>
          <p className="mt-2 text-sm text-yellow-400">Sobrecarga / temperatura</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em falha</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalFalha}</p>
          <p className="mt-2 text-sm text-red-400">Ação necessária</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Carga Média</p>
          <p className="mt-2 text-3xl font-bold text-white">{cargaMedia}%</p>
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
              placeholder="Buscar transformador ou subestação..."
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
                <th className="pb-3 font-normal">Transformador</th>
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Potência</th>
                <th className="pb-3 font-normal">Carga</th>
                <th className="pb-3 font-normal">Temperatura</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {transformadoresFiltrados.map((t) => {
                const status = statusStyles[t.status]

                return (
                  <tr key={t.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{t.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">{t.id}</p>
                    </td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {t.subestacao}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{t.potenciaNominal} MVA</td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Gauge className="h-3.5 w-3.5 text-blue-400" />
                        {t.cargaAtual}%
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{t.temperatura}°C</td>
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

          {transformadoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum transformador encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Transformadores