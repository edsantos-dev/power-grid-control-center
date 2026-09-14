import { useState } from 'react'
import { Search, FileText, Download, Calendar } from 'lucide-react'

type TipoRelatorio = 'consumo' | 'perdas' | 'disponibilidade' | 'eventos' | 'manutencao'
type StatusRelatorio = 'disponivel' | 'gerando'

interface Relatorio {
  id: string
  titulo: string
  tipo: TipoRelatorio
  periodo: string
  geradoEm: string
  responsavel: string
  status: StatusRelatorio
}

const relatorios: Relatorio[] = [
  { id: 'REL-001', titulo: 'Relatório de Consumo Mensal - Agosto 2026', tipo: 'consumo', periodo: '01/08/2026 - 31/08/2026', geradoEm: '01/09/2026', responsavel: 'Centro de Controle', status: 'disponivel' },
  { id: 'REL-002', titulo: 'Perdas Técnicas na Distribuição - Agosto 2026', tipo: 'perdas', periodo: '01/08/2026 - 31/08/2026', geradoEm: '01/09/2026', responsavel: 'Equipe Central', status: 'disponivel' },
  { id: 'REL-003', titulo: 'Disponibilidade de Subestações - 3º Trimestre', tipo: 'disponibilidade', periodo: '01/07/2026 - 30/09/2026', geradoEm: '14/09/2026', responsavel: 'Centro de Controle', status: 'gerando' },
  { id: 'REL-004', titulo: 'Eventos Operacionais - Agosto 2026', tipo: 'eventos', periodo: '01/08/2026 - 31/08/2026', geradoEm: '01/09/2026', responsavel: 'Centro de Controle', status: 'disponivel' },
  { id: 'REL-005', titulo: 'Manutenções Realizadas - Agosto 2026', tipo: 'manutencao', periodo: '01/08/2026 - 31/08/2026', geradoEm: '02/09/2026', responsavel: 'Equipe Norte', status: 'disponivel' },
  { id: 'REL-006', titulo: 'Relatório de Consumo Semanal', tipo: 'consumo', periodo: '07/09/2026 - 13/09/2026', geradoEm: '14/09/2026', responsavel: 'Centro de Controle', status: 'disponivel' },
]

const tipoLabel: Record<TipoRelatorio, string> = {
  consumo: 'Consumo',
  perdas: 'Perdas',
  disponibilidade: 'Disponibilidade',
  eventos: 'Eventos',
  manutencao: 'Manutenção',
}

const statusStyles: Record<StatusRelatorio, { dot: string; text: string; label: string }> = {
  disponivel: { dot: 'bg-green-500', text: 'text-green-400', label: 'Disponível' },
  gerando: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Gerando...' },
}

function Relatorios() {
  const [filtroTipo, setFiltroTipo] = useState<TipoRelatorio | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const relatoriosFiltrados = relatorios.filter((r) => {
    const passaTipo = filtroTipo === 'todos' || r.tipo === filtroTipo
    const passaBusca = r.titulo.toLowerCase().includes(busca.toLowerCase())
    return passaTipo && passaBusca
  })

  const totalDisponiveis = relatorios.filter((r) => r.status === 'disponivel').length
  const totalGerando = relatorios.filter((r) => r.status === 'gerando').length

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Relatórios
        </h2>

        <p className="mt-2 text-slate-400">
          Relatórios gerenciais e históricos operacionais da rede.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Disponíveis</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalDisponiveis}</p>
          <p className="mt-2 text-sm text-green-400">Prontos para download</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Sendo gerados</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalGerando}</p>
          <p className="mt-2 text-sm text-yellow-400">Processando</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Total de Relatórios</p>
          <p className="mt-2 text-3xl font-bold text-white">{relatorios.length}</p>
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
              placeholder="Buscar relatório..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'consumo', 'perdas', 'disponibilidade', 'eventos', 'manutencao'] as const).map((tipo) => (
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

        <div className="mt-6 space-y-3">
          {relatoriosFiltrados.map((r) => {
            const status = statusStyles[r.status]

            return (
              <div
                key={r.id}
                className="flex items-center justify-between gap-4 rounded-lg border border-slate-800 p-4 transition hover:border-slate-700"
              >
                <div className="flex items-start gap-3">
                  <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-500/10">
                    <FileText className="h-4 w-4 text-blue-400" />
                  </span>

                  <div>
                    <p className="text-sm text-slate-200">{r.titulo}</p>
                    <p className="mt-1 flex items-center gap-1.5 text-xs text-slate-500">
                      <Calendar className="h-3 w-3" />
                      {r.periodo} • Gerado em {r.geradoEm} • {r.responsavel}
                    </p>
                  </div>
                </div>

                <div className="flex shrink-0 items-center gap-4">
                  <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                    <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                    {status.label}
                  </span>

                  <button
                    disabled={r.status !== 'disponivel'}
                    className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 transition hover:border-slate-600 hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
                  >
                    <Download className="h-3.5 w-3.5" />
                    Baixar
                  </button>
                </div>
              </div>
            )
          })}

          {relatoriosFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum relatório encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Relatorios