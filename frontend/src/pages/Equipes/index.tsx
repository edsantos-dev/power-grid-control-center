import { useState } from 'react'
import { Search, MapPin, Users, Phone } from 'lucide-react'

type Especialidade = 'manutencao_linhas' | 'subestacoes' | 'smartmeters' | 'emergencia'
type StatusEquipe = 'disponivel' | 'em_atendimento' | 'fora_de_servico'

interface Equipe {
  id: string
  nome: string
  especialidade: Especialidade
  regiao: string
  membros: number
  responsavel: string
  contato: string
  status: StatusEquipe
}

const equipes: Equipe[] = [
  { id: 'EQ-01', nome: 'Equipe Norte', especialidade: 'manutencao_linhas', regiao: 'Região Norte', membros: 4, responsavel: 'Carlos Mendes', contato: '(41) 99123-4501', status: 'disponivel' },
  { id: 'EQ-02', nome: 'Equipe Central', especialidade: 'smartmeters', regiao: 'Região Central', membros: 3, responsavel: 'Renata Alves', contato: '(41) 99123-4502', status: 'em_atendimento' },
  { id: 'EQ-03', nome: 'Equipe Sul', especialidade: 'subestacoes', regiao: 'Região Sul', membros: 5, responsavel: 'Paulo Ricardo', contato: '(41) 99123-4503', status: 'disponivel' },
  { id: 'EQ-04', nome: 'Equipe Leste', especialidade: 'emergencia', regiao: 'Região Leste', membros: 6, responsavel: 'Fernanda Lima', contato: '(41) 99123-4504', status: 'em_atendimento' },
  { id: 'EQ-05', nome: 'Equipe Oeste', especialidade: 'manutencao_linhas', regiao: 'Região Oeste', membros: 4, responsavel: 'André Souza', contato: '(41) 99123-4505', status: 'fora_de_servico' },
  { id: 'EQ-06', nome: 'Equipe Vale Verde', especialidade: 'subestacoes', regiao: 'Região Norte', membros: 3, responsavel: 'Juliana Ramos', contato: '(41) 99123-4506', status: 'disponivel' },
]

const especialidadeLabel: Record<Especialidade, string> = {
  manutencao_linhas: 'Manutenção de Linhas',
  subestacoes: 'Subestações',
  smartmeters: 'Smartmeters',
  emergencia: 'Emergência',
}

const statusStyles: Record<StatusEquipe, { dot: string; text: string; label: string }> = {
  disponivel: { dot: 'bg-green-500', text: 'text-green-400', label: 'Disponível' },
  em_atendimento: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Em atendimento' },
  fora_de_servico: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Fora de serviço' },
}

function Equipes() {
  const [filtroEspecialidade, setFiltroEspecialidade] = useState<Especialidade | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const equipesFiltradas = equipes.filter((e) => {
    const passaEspecialidade = filtroEspecialidade === 'todos' || e.especialidade === filtroEspecialidade
    const passaBusca =
      e.nome.toLowerCase().includes(busca.toLowerCase()) ||
      e.responsavel.toLowerCase().includes(busca.toLowerCase()) ||
      e.regiao.toLowerCase().includes(busca.toLowerCase())
    return passaEspecialidade && passaBusca
  })

  const totalDisponiveis = equipes.filter((e) => e.status === 'disponivel').length
  const totalEmAtendimento = equipes.filter((e) => e.status === 'em_atendimento').length
  const totalForaDeServico = equipes.filter((e) => e.status === 'fora_de_servico').length
  const membrosTotal = equipes.reduce((acc, e) => acc + e.membros, 0)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Equipes
        </h2>

        <p className="mt-2 text-slate-400">
          Equipes de manutenção e atendimento operacional da rede.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Disponíveis</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalDisponiveis}</p>
          <p className="mt-2 text-sm text-green-400">de {equipes.length} equipes</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em atendimento</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalEmAtendimento}</p>
          <p className="mt-2 text-sm text-yellow-400">Em campo</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Fora de serviço</p>
          <p className="mt-2 text-3xl font-bold text-slate-400">{totalForaDeServico}</p>
          <p className="mt-2 text-sm text-slate-400">Indisponíveis</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Total de Membros</p>
          <p className="mt-2 text-3xl font-bold text-white">{membrosTotal}</p>
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
              placeholder="Buscar equipe, responsável ou região..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'manutencao_linhas', 'subestacoes', 'smartmeters', 'emergencia'] as const).map((esp) => (
              <button
                key={esp}
                onClick={() => setFiltroEspecialidade(esp)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroEspecialidade === esp
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {esp === 'todos' ? 'Todos' : especialidadeLabel[esp]}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Equipe</th>
                <th className="pb-3 font-normal">Especialidade</th>
                <th className="pb-3 font-normal">Região</th>
                <th className="pb-3 font-normal">Membros</th>
                <th className="pb-3 font-normal">Responsável</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {equipesFiltradas.map((e) => {
                const status = statusStyles[e.status]

                return (
                  <tr key={e.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4 text-slate-200">{e.nome}</td>
                    <td className="py-3 text-slate-300">{especialidadeLabel[e.especialidade]}</td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {e.regiao}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Users className="h-3.5 w-3.5 text-blue-400" />
                        {e.membros}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">
                      <p>{e.responsavel}</p>
                      <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                        <Phone className="h-3 w-3" />
                        {e.contato}
                      </p>
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

          {equipesFiltradas.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhuma equipe encontrada.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Equipes