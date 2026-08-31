import { useState } from 'react'
import { Search, Calendar, User } from 'lucide-react'

type Categoria = 'manobra' | 'desligamento' | 'interrupcao' | 'sobrecarga' | 'falha' | 'manutencao'
type Criticidade = 'alta' | 'media' | 'baixa'
type Situacao = 'aberto' | 'em_andamento' | 'concluido'

interface Evento {
  id: number
  titulo: string
  ativo: string
  categoria: Categoria
  criticidade: Criticidade
  situacao: Situacao
  responsavel: string
  data: string
  hora: string
}

const eventos: Evento[] = [
  { id: 1, titulo: 'Desligamento programado para manutenção preventiva', ativo: 'Subestação SE-04', categoria: 'desligamento', criticidade: 'media', situacao: 'concluido', responsavel: 'Equipe Norte', data: '31/08/2026', hora: '06:15' },
  { id: 2, titulo: 'Manobra de transferência de carga', ativo: 'Alimentador AL-102', categoria: 'manobra', criticidade: 'baixa', situacao: 'concluido', responsavel: 'Centro de Controle', data: '31/08/2026', hora: '08:40' },
  { id: 3, titulo: 'Interrupção no fornecimento por falha em equipamento', ativo: 'Subestação SE-05', categoria: 'interrupcao', criticidade: 'alta', situacao: 'em_andamento', responsavel: 'Equipe Oeste', data: '31/08/2026', hora: '09:52' },
  { id: 4, titulo: 'Sobrecarga detectada em horário de pico', ativo: 'Transformador T-14', categoria: 'sobrecarga', criticidade: 'alta', situacao: 'aberto', responsavel: 'Não atribuído', data: '31/08/2026', hora: '11:20' },
  { id: 5, titulo: 'Falha em dispositivo de proteção', ativo: 'Linha LT-05', categoria: 'falha', criticidade: 'alta', situacao: 'em_andamento', responsavel: 'Equipe Sul', data: '31/08/2026', hora: '12:05' },
  { id: 6, titulo: 'Manutenção corretiva em medidor inteligente', ativo: 'Smartmeter M-231', categoria: 'manutencao', criticidade: 'baixa', situacao: 'concluido', responsavel: 'Equipe Central', data: '30/08/2026', hora: '17:30' },
  { id: 7, titulo: 'Manobra de religamento automático', ativo: 'Alimentador AL-101', categoria: 'manobra', criticidade: 'media', situacao: 'concluido', responsavel: 'Centro de Controle', data: '30/08/2026', hora: '15:10' },
]

const categoriaLabel: Record<Categoria, string> = {
  manobra: 'Manobra',
  desligamento: 'Desligamento',
  interrupcao: 'Interrupção',
  sobrecarga: 'Sobrecarga',
  falha: 'Falha',
  manutencao: 'Manutenção',
}

const criticidadeStyles: Record<Criticidade, { dot: string; text: string; label: string }> = {
  alta: { dot: 'bg-red-500', text: 'text-red-400', label: 'Alta' },
  media: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Média' },
  baixa: { dot: 'bg-blue-500', text: 'text-blue-400', label: 'Baixa' },
}

const situacaoStyles: Record<Situacao, { text: string; label: string }> = {
  aberto: { text: 'text-red-400', label: 'Aberto' },
  em_andamento: { text: 'text-yellow-400', label: 'Em andamento' },
  concluido: { text: 'text-green-400', label: 'Concluído' },
}

function Eventos() {
  const [filtroCategoria, setFiltroCategoria] = useState<Categoria | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const eventosFiltrados = eventos.filter((e) => {
    const passaCategoria = filtroCategoria === 'todos' || e.categoria === filtroCategoria
    const passaBusca =
      e.titulo.toLowerCase().includes(busca.toLowerCase()) ||
      e.ativo.toLowerCase().includes(busca.toLowerCase())
    return passaCategoria && passaBusca
  })

  const totalAbertos = eventos.filter((e) => e.situacao === 'aberto').length
  const totalAndamento = eventos.filter((e) => e.situacao === 'em_andamento').length
  const totalConcluidos = eventos.filter((e) => e.situacao === 'concluido').length
  const totalAltaCriticidade = eventos.filter((e) => e.criticidade === 'alta' && e.situacao !== 'concluido').length

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Eventos
        </h2>

        <p className="mt-2 text-slate-400">
          Registro de ocorrências operacionais na infraestrutura elétrica.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Alta criticidade</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalAltaCriticidade}</p>
          <p className="mt-2 text-sm text-red-400">Não concluídos</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em aberto</p>
          <p className="mt-2 text-3xl font-bold text-white">{totalAbertos}</p>
          <p className="mt-2 text-sm text-slate-400">Sem responsável</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em andamento</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAndamento}</p>
          <p className="mt-2 text-sm text-yellow-400">Sendo atendidos</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Concluídos</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalConcluidos}</p>
          <p className="mt-2 text-sm text-green-400">Últimas 24h</p>
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
              placeholder="Buscar evento ou ativo..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'manobra', 'desligamento', 'interrupcao', 'sobrecarga', 'falha', 'manutencao'] as const).map((cat) => (
              <button
                key={cat}
                onClick={() => setFiltroCategoria(cat)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroCategoria === cat
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {cat === 'todos' ? 'Todos' : categoriaLabel[cat]}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Evento</th>
                <th className="pb-3 font-normal">Categoria</th>
                <th className="pb-3 font-normal">Criticidade</th>
                <th className="pb-3 font-normal">Situação</th>
                <th className="pb-3 font-normal">Responsável</th>
                <th className="pb-3 font-normal">Data / Hora</th>
              </tr>
            </thead>
            <tbody>
              {eventosFiltrados.map((evento) => {
                const criticidade = criticidadeStyles[evento.criticidade]
                const situacao = situacaoStyles[evento.situacao]

                return (
                  <tr key={evento.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{evento.titulo}</p>
                      <p className="mt-1 text-xs text-slate-500">{evento.ativo}</p>
                    </td>
                    <td className="py-3 text-slate-300">{categoriaLabel[evento.categoria]}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${criticidade.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${criticidade.dot}`} />
                        {criticidade.label}
                      </span>
                    </td>
                    <td className={`py-3 text-xs ${situacao.text}`}>{situacao.label}</td>
                    <td className="py-3 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <User className="h-3.5 w-3.5 text-slate-500" />
                        {evento.responsavel}
                      </span>
                    </td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="h-3.5 w-3.5 text-slate-500" />
                        {evento.data} • {evento.hora}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {eventosFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum evento encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Eventos