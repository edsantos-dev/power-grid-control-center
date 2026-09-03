import { useState } from 'react'
import { Search, Zap, MapPin } from 'lucide-react'

type TipoUsina = 'hidreletrica' | 'solar' | 'eolica' | 'termica'
type StatusUsina = 'operacional' | 'atencao' | 'parada' | 'manutencao'

interface Usina {
  id: string
  nome: string
  tipo: TipoUsina
  regiao: string
  capacidadeInstalada: number
  geracaoAtual: number
  status: StatusUsina
}

const usinas: Usina[] = [
  { id: 'US-01', nome: 'UHE Rio Verde', tipo: 'hidreletrica', regiao: 'Região Norte', capacidadeInstalada: 420, geracaoAtual: 385, status: 'operacional' },
  { id: 'US-02', nome: 'Parque Solar Cerrado', tipo: 'solar', regiao: 'Região Central', capacidadeInstalada: 180, geracaoAtual: 142, status: 'operacional' },
  { id: 'US-03', nome: 'Complexo Eólico Serra Alta', tipo: 'eolica', regiao: 'Região Sul', capacidadeInstalada: 260, geracaoAtual: 198, status: 'atencao' },
  { id: 'US-04', nome: 'UTE Porto Novo', tipo: 'termica', regiao: 'Região Leste', capacidadeInstalada: 150, geracaoAtual: 0, status: 'manutencao' },
  { id: 'US-05', nome: 'UHE Cachoeira Grande', tipo: 'hidreletrica', regiao: 'Região Oeste', capacidadeInstalada: 310, geracaoAtual: 0, status: 'parada' },
  { id: 'US-06', nome: 'Parque Solar Vale Dourado', tipo: 'solar', regiao: 'Região Norte', capacidadeInstalada: 95, geracaoAtual: 78, status: 'operacional' },
]

const tipoLabel: Record<TipoUsina, string> = {
  hidreletrica: 'Hidrelétrica',
  solar: 'Solar',
  eolica: 'Eólica',
  termica: 'Térmica',
}

const statusStyles: Record<StatusUsina, { dot: string; text: string; label: string }> = {
  operacional: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Atenção' },
  parada: { dot: 'bg-red-500', text: 'text-red-400', label: 'Parada' },
  manutencao: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

function Usinas() {
  const [filtroTipo, setFiltroTipo] = useState<TipoUsina | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const usinasFiltradas = usinas.filter((u) => {
    const passaTipo = filtroTipo === 'todos' || u.tipo === filtroTipo
    const passaBusca = u.nome.toLowerCase().includes(busca.toLowerCase()) || u.regiao.toLowerCase().includes(busca.toLowerCase())
    return passaTipo && passaBusca
  })

  const capacidadeTotal = usinas.reduce((acc, u) => acc + u.capacidadeInstalada, 0)
  const geracaoTotal = usinas.reduce((acc, u) => acc + u.geracaoAtual, 0)
  const totalOperacionais = usinas.filter((u) => u.status === 'operacional').length
  const totalParadas = usinas.filter((u) => u.status === 'parada' || u.status === 'manutencao').length

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Usinas
        </h2>

        <p className="mt-2 text-slate-400">
          Cadastro e acompanhamento das usinas geradoras da rede.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Capacidade Instalada</p>
          <p className="mt-2 text-3xl font-bold text-white">
            {capacidadeTotal} <span className="text-lg font-normal text-slate-400">MW</span>
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Geração Atual</p>
          <p className="mt-2 text-3xl font-bold text-blue-400">
            {geracaoTotal} <span className="text-lg font-normal text-slate-400">MW</span>
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {usinas.length} usinas</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Paradas / Manutenção</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalParadas}</p>
          <p className="mt-2 text-sm text-red-400">Fora de operação</p>
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
              placeholder="Buscar usina ou região..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'hidreletrica', 'solar', 'eolica', 'termica'] as const).map((tipo) => (
              <button
                key={tipo}
                onClick={() => setFiltroTipo(tipo)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroTipo === tipo
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {tipo === 'todos' ? 'Todos' : tipoLabel[tipo]}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Usina</th>
                <th className="pb-3 font-normal">Tipo</th>
                <th className="pb-3 font-normal">Região</th>
                <th className="pb-3 font-normal">Capacidade</th>
                <th className="pb-3 font-normal">Geração Atual</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {usinasFiltradas.map((u) => {
                const status = statusStyles[u.status]

                return (
                  <tr key={u.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{u.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">{u.id}</p>
                    </td>
                    <td className="py-3 text-slate-300">{tipoLabel[u.tipo]}</td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {u.regiao}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{u.capacidadeInstalada} MW</td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Zap className="h-3.5 w-3.5 text-blue-400" />
                        {u.geracaoAtual} MW
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

          {usinasFiltradas.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhuma usina encontrada.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Usinas