import { useState } from 'react'
import { Search, MapPin, Zap } from 'lucide-react'

type TipoConsumidor = 'residencial' | 'comercial' | 'industrial' | 'rural'
type StatusConsumidor = 'ativo' | 'inadimplente' | 'suspenso'

interface Consumidor {
  id: string
  nome: string
  tipo: TipoConsumidor
  endereco: string
  alimentador: string
  consumoMedio: number
  status: StatusConsumidor
}

const consumidores: Consumidor[] = [
  { id: 'C-1001', nome: 'Maria Fernandes', tipo: 'residencial', endereco: 'Rua das Palmeiras, 120', alimentador: 'Alimentador Norte-1', consumoMedio: 210, status: 'ativo' },
  { id: 'C-1002', nome: 'Mercado Central Ltda', tipo: 'comercial', endereco: 'Av. Central, 850', alimentador: 'Alimentador Central-1', consumoMedio: 3200, status: 'ativo' },
  { id: 'C-1003', nome: 'João Batista', tipo: 'residencial', endereco: 'Rua Bela Vista, 45', alimentador: 'Alimentador Central-1', consumoMedio: 180, status: 'inadimplente' },
  { id: 'C-1004', nome: 'Metalúrgica Sul Indústria', tipo: 'industrial', endereco: 'Distrito Sul, 12', alimentador: 'Alimentador Sul-1', consumoMedio: 24500, status: 'ativo' },
  { id: 'C-1005', nome: 'Ana Paula Souza', tipo: 'residencial', endereco: 'Rua Girassol, 78', alimentador: 'Alimentador Leste-1', consumoMedio: 150, status: 'ativo' },
  { id: 'C-1006', nome: 'Fazenda Vale Verde', tipo: 'rural', endereco: 'Estrada Vale Verde, km 8', alimentador: 'Alimentador Vale Verde-1', consumoMedio: 890, status: 'ativo' },
  { id: 'C-1007', nome: 'Padaria Porto Alto', tipo: 'comercial', endereco: 'Praça Porto Alto, 33', alimentador: 'Alimentador Porto Alto-1', consumoMedio: 640, status: 'suspenso' },
]

const tipoLabel: Record<TipoConsumidor, string> = {
  residencial: 'Residencial',
  comercial: 'Comercial',
  industrial: 'Industrial',
  rural: 'Rural',
}

const statusStyles: Record<StatusConsumidor, { dot: string; text: string; label: string }> = {
  ativo: { dot: 'bg-green-500', text: 'text-green-400', label: 'Ativo' },
  inadimplente: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Inadimplente' },
  suspenso: { dot: 'bg-red-500', text: 'text-red-400', label: 'Suspenso' },
}

function Consumidores() {
  const [filtroTipo, setFiltroTipo] = useState<TipoConsumidor | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const consumidoresFiltrados = consumidores.filter((c) => {
    const passaTipo = filtroTipo === 'todos' || c.tipo === filtroTipo
    const passaBusca =
      c.nome.toLowerCase().includes(busca.toLowerCase()) ||
      c.endereco.toLowerCase().includes(busca.toLowerCase())
    return passaTipo && passaBusca
  })

  const totalAtivos = consumidores.filter((c) => c.status === 'ativo').length
  const totalInadimplentes = consumidores.filter((c) => c.status === 'inadimplente').length
  const totalSuspensos = consumidores.filter((c) => c.status === 'suspenso').length
  const consumoTotal = consumidores.reduce((acc, c) => acc + c.consumoMedio, 0)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Consumidores
        </h2>

        <p className="mt-2 text-slate-400">
          Cadastro dos consumidores atendidos pela rede de distribuição.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Ativos</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalAtivos}</p>
          <p className="mt-2 text-sm text-green-400">de {consumidores.length} consumidores</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Inadimplentes</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalInadimplentes}</p>
          <p className="mt-2 text-sm text-yellow-400">Pendência financeira</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Suspensos</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalSuspensos}</p>
          <p className="mt-2 text-sm text-red-400">Fornecimento interrompido</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Consumo Médio Total</p>
          <p className="mt-2 text-3xl font-bold text-white">
            {consumoTotal.toLocaleString('pt-BR')} <span className="text-lg font-normal text-slate-400">kWh</span>
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
              placeholder="Buscar consumidor ou endereço..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'residencial', 'comercial', 'industrial', 'rural'] as const).map((tipo) => (
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
                <th className="pb-3 font-normal">Consumidor</th>
                <th className="pb-3 font-normal">Tipo</th>
                <th className="pb-3 font-normal">Endereço</th>
                <th className="pb-3 font-normal">Alimentador</th>
                <th className="pb-3 font-normal">Consumo Médio</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {consumidoresFiltrados.map((c) => {
                const status = statusStyles[c.status]

                return (
                  <tr key={c.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{c.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">{c.id}</p>
                    </td>
                    <td className="py-3 text-slate-300">{tipoLabel[c.tipo]}</td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {c.endereco}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{c.alimentador}</td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Zap className="h-3.5 w-3.5 text-blue-400" />
                        {c.consumoMedio.toLocaleString('pt-BR')} kWh
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

          {consumidoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum consumidor encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Consumidores