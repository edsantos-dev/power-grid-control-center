import { useState } from 'react'
import { Search, MapPin, Zap } from 'lucide-react'

type StatusLinha = 'operacional' | 'atencao' | 'falha' | 'manutencao'

interface Linha {
  id: string
  nome: string
  origem: string
  destino: string
  tensao: number
  extensao: number
  correnteAtual: number
  status: StatusLinha
}

const linhas: Linha[] = [
  { id: 'LT-01', nome: 'Linha de Transmissão LT-01', origem: 'UHE Rio Verde', destino: 'Subestação Norte', tensao: 138, extensao: 84, correnteAtual: 412, status: 'operacional' },
  { id: 'LT-02', nome: 'Linha de Transmissão LT-02', origem: 'Subestação Norte', destino: 'Subestação Central', tensao: 138, extensao: 56, correnteAtual: 480, status: 'atencao' },
  { id: 'LT-03', nome: 'Linha de Transmissão LT-03', origem: 'Parque Solar Cerrado', destino: 'Subestação Central', tensao: 69, extensao: 32, correnteAtual: 210, status: 'operacional' },
  { id: 'LT-05', nome: 'Linha de Transmissão LT-05', origem: 'Subestação Sul', destino: 'Subestação Leste', tensao: 138, extensao: 71, correnteAtual: 615, status: 'atencao' },
  { id: 'LT-06', nome: 'Linha de Transmissão LT-06', origem: 'UTE Porto Novo', destino: 'Subestação Leste', tensao: 69, extensao: 18, correnteAtual: 0, status: 'falha' },
  { id: 'LT-07', nome: 'Linha de Transmissão LT-07', origem: 'Subestação Oeste', destino: 'Subestação Vale Verde', tensao: 138, extensao: 93, correnteAtual: 0, status: 'manutencao' },
  { id: 'LT-08', nome: 'Linha de Transmissão LT-08', origem: 'Complexo Eólico Serra Alta', destino: 'Subestação Sul', tensao: 69, extensao: 44, correnteAtual: 305, status: 'operacional' },
]

const statusStyles: Record<StatusLinha, { dot: string; text: string; label: string }> = {
  operacional: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Atenção' },
  falha: { dot: 'bg-red-500', text: 'text-red-400', label: 'Falha' },
  manutencao: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

function Linhas() {
  const [filtroStatus, setFiltroStatus] = useState<StatusLinha | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const linhasFiltradas = linhas.filter((l) => {
    const passaStatus = filtroStatus === 'todos' || l.status === filtroStatus
    const passaBusca =
      l.nome.toLowerCase().includes(busca.toLowerCase()) ||
      l.origem.toLowerCase().includes(busca.toLowerCase()) ||
      l.destino.toLowerCase().includes(busca.toLowerCase())
    return passaStatus && passaBusca
  })

  const totalOperacionais = linhas.filter((l) => l.status === 'operacional').length
  const totalAtencao = linhas.filter((l) => l.status === 'atencao').length
  const totalFalha = linhas.filter((l) => l.status === 'falha').length
  const extensaoTotal = linhas.reduce((acc, l) => acc + l.extensao, 0)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Linhas
        </h2>

        <p className="mt-2 text-slate-400">
          Cadastro e monitoramento das linhas de transmissão da rede.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {linhas.length} linhas</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em atenção</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAtencao}</p>
          <p className="mt-2 text-sm text-yellow-400">Sobrecorrente</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em falha</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalFalha}</p>
          <p className="mt-2 text-sm text-red-400">Ação necessária</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Extensão Total</p>
          <p className="mt-2 text-3xl font-bold text-white">
            {extensaoTotal} <span className="text-lg font-normal text-slate-400">km</span>
          </p>
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
              placeholder="Buscar linha, origem ou destino..."
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
                <th className="pb-3 font-normal">Linha</th>
                <th className="pb-3 font-normal">Trajeto</th>
                <th className="pb-3 font-normal">Tensão</th>
                <th className="pb-3 font-normal">Extensão</th>
                <th className="pb-3 font-normal">Corrente</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {linhasFiltradas.map((l) => {
                const status = statusStyles[l.status]

                return (
                  <tr key={l.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{l.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">{l.id}</p>
                    </td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {l.origem} → {l.destino}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{l.tensao} kV</td>
                    <td className="py-3 text-slate-300">{l.extensao} km</td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Zap className="h-3.5 w-3.5 text-blue-400" />
                        {l.correnteAtual} A
                      </span>
                    </td>
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

          {linhasFiltradas.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhuma linha encontrada.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Linhas