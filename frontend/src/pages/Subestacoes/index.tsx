import { useState } from 'react'
import { Search, MapPin, Gauge } from 'lucide-react'

type StatusSubestacao = 'operacional' | 'atencao' | 'falha' | 'manutencao'

interface Subestacao {
  id: string
  nome: string
  regiao: string
  tensaoPrimaria: number
  tensaoSecundaria: number
  cargaAtual: number
  disponibilidade: number
  status: StatusSubestacao
}

const subestacoes: Subestacao[] = [
  { id: 'SE-01', nome: 'Subestação Norte', regiao: 'Região Norte', tensaoPrimaria: 138, tensaoSecundaria: 13.8, cargaAtual: 72, disponibilidade: 99.8, status: 'operacional' },
  { id: 'SE-02', nome: 'Subestação Central', regiao: 'Região Central', tensaoPrimaria: 138, tensaoSecundaria: 13.8, cargaAtual: 91, disponibilidade: 98.2, status: 'atencao' },
  { id: 'SE-03', nome: 'Subestação Sul', regiao: 'Região Sul', tensaoPrimaria: 69, tensaoSecundaria: 13.8, cargaAtual: 58, disponibilidade: 99.9, status: 'operacional' },
  { id: 'SE-04', nome: 'Subestação Leste', regiao: 'Região Leste', tensaoPrimaria: 69, tensaoSecundaria: 13.8, cargaAtual: 0, disponibilidade: 95.4, status: 'manutencao' },
  { id: 'SE-05', nome: 'Subestação Oeste', regiao: 'Região Oeste', tensaoPrimaria: 138, tensaoSecundaria: 13.8, cargaAtual: 0, disponibilidade: 88.1, status: 'falha' },
  { id: 'SE-06', nome: 'Subestação Vale Verde', regiao: 'Região Norte', tensaoPrimaria: 69, tensaoSecundaria: 13.8, cargaAtual: 64, disponibilidade: 99.6, status: 'operacional' },
  { id: 'SE-07', nome: 'Subestação Porto Alto', regiao: 'Região Leste', tensaoPrimaria: 138, tensaoSecundaria: 13.8, cargaAtual: 88, disponibilidade: 97.5, status: 'atencao' },
]

const statusStyles: Record<StatusSubestacao, { dot: string; text: string; label: string }> = {
  operacional: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Atenção' },
  falha: { dot: 'bg-red-500', text: 'text-red-400', label: 'Falha' },
  manutencao: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

function Subestacoes() {
  const [filtroStatus, setFiltroStatus] = useState<StatusSubestacao | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const subestacoesFiltradas = subestacoes.filter((s) => {
    const passaStatus = filtroStatus === 'todos' || s.status === filtroStatus
    const passaBusca = s.nome.toLowerCase().includes(busca.toLowerCase()) || s.regiao.toLowerCase().includes(busca.toLowerCase())
    return passaStatus && passaBusca
  })

  const totalOperacionais = subestacoes.filter((s) => s.status === 'operacional').length
  const totalAtencao = subestacoes.filter((s) => s.status === 'atencao').length
  const totalFalha = subestacoes.filter((s) => s.status === 'falha').length
  const disponibilidadeMedia = (subestacoes.reduce((acc, s) => acc + s.disponibilidade, 0) / subestacoes.length).toFixed(1)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Subestações
        </h2>

        <p className="mt-2 text-slate-400">
          Cadastro e monitoramento das subestações da rede elétrica.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {subestacoes.length} subestações</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em atenção</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAtencao}</p>
          <p className="mt-2 text-sm text-yellow-400">Carga elevada</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em falha</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalFalha}</p>
          <p className="mt-2 text-sm text-red-400">Ação necessária</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Disponibilidade Média</p>
          <p className="mt-2 text-3xl font-bold text-white">{disponibilidadeMedia}%</p>
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
              placeholder="Buscar subestação ou região..."
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
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Região</th>
                <th className="pb-3 font-normal">Tensão (kV)</th>
                <th className="pb-3 font-normal">Carga</th>
                <th className="pb-3 font-normal">Disponibilidade</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {subestacoesFiltradas.map((s) => {
                const status = statusStyles[s.status]

                return (
                  <tr key={s.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{s.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">{s.id}</p>
                    </td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {s.regiao}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{s.tensaoPrimaria} / {s.tensaoSecundaria}</td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Gauge className="h-3.5 w-3.5 text-blue-400" />
                        {s.cargaAtual}%
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{s.disponibilidade}%</td>
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

          {subestacoesFiltradas.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhuma subestação encontrada.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Subestacoes