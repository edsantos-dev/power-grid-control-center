import { useState } from 'react'
import { AlertTriangle, AlertCircle, Info, CheckCircle2, Search } from 'lucide-react'

type Nivel = 'critico' | 'atencao' | 'info'
type StatusAlerta = 'aberto' | 'em_andamento' | 'resolvido'

interface Alerta {
  id: number
  titulo: string
  ativo: string
  nivel: Nivel
  status: StatusAlerta
  categoria: string
  tempo: string
}

const alertas: Alerta[] = [
  { id: 1, titulo: 'Transformador T-14 acima da capacidade', ativo: 'Transformador T-14', nivel: 'critico', status: 'aberto', categoria: 'Sobrecarga', tempo: '2 min atrás' },
  { id: 2, titulo: 'Subestação SE-07 com oscilação de tensão', ativo: 'Subestação SE-07', nivel: 'atencao', status: 'em_andamento', categoria: 'Tensão', tempo: '18 min atrás' },
  { id: 3, titulo: 'Falha de comunicação no medidor M-231', ativo: 'Smartmeter M-231', nivel: 'atencao', status: 'aberto', categoria: 'Comunicação', tempo: '41 min atrás' },
  { id: 4, titulo: 'Sobrecorrente na linha LT-05', ativo: 'Linha LT-05', nivel: 'critico', status: 'em_andamento', categoria: 'Sobrecorrente', tempo: '1h atrás' },
  { id: 5, titulo: 'Oscilação de frequência detectada', ativo: 'Rede Geral', nivel: 'info', status: 'resolvido', categoria: 'Frequência', tempo: '3h atrás' },
  { id: 6, titulo: 'Manutenção programada concluída', ativo: 'Subestação SE-02', nivel: 'info', status: 'resolvido', categoria: 'Manutenção', tempo: '5h atrás' },
]

const nivelStyles: Record<Nivel, { dot: string; text: string; bg: string; label: string; icon: typeof AlertTriangle }> = {
  critico: { dot: 'bg-red-500', text: 'text-red-400', bg: 'bg-red-500/10', label: 'Crítico', icon: AlertTriangle },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', bg: 'bg-yellow-500/10', label: 'Atenção', icon: AlertCircle },
  info: { dot: 'bg-blue-500', text: 'text-blue-400', bg: 'bg-blue-500/10', label: 'Info', icon: Info },
}

const statusStyles: Record<StatusAlerta, { text: string; label: string }> = {
  aberto: { text: 'text-red-400', label: 'Aberto' },
  em_andamento: { text: 'text-yellow-400', label: 'Em andamento' },
  resolvido: { text: 'text-green-400', label: 'Resolvido' },
}

function Alertas() {
  const [filtroNivel, setFiltroNivel] = useState<Nivel | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const alertasFiltrados = alertas.filter((a) => {
    const passaNivel = filtroNivel === 'todos' || a.nivel === filtroNivel
    const passaBusca = a.titulo.toLowerCase().includes(busca.toLowerCase()) || a.ativo.toLowerCase().includes(busca.toLowerCase())
    return passaNivel && passaBusca
  })

  const totalCritico = alertas.filter((a) => a.nivel === 'critico' && a.status !== 'resolvido').length
  const totalAtencao = alertas.filter((a) => a.nivel === 'atencao' && a.status !== 'resolvido').length
  const totalAbertos = alertas.filter((a) => a.status === 'aberto').length
  const totalResolvidos = alertas.filter((a) => a.status === 'resolvido').length

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Alertas
        </h2>

        <p className="mt-2 text-slate-400">
          Ocorrências e situações que requerem atenção dos operadores.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Críticos</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalCritico}</p>
          <p className="mt-2 text-sm text-red-400">Ação imediata</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Atenção</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAtencao}</p>
          <p className="mt-2 text-sm text-yellow-400">Monitorar</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em aberto</p>
          <p className="mt-2 text-3xl font-bold text-white">{totalAbertos}</p>
          <p className="mt-2 text-sm text-slate-400">Aguardando resposta</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Resolvidos</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalResolvidos}</p>
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
              placeholder="Buscar alerta ou ativo..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'critico', 'atencao', 'info'] as const).map((nivel) => (
              <button
                key={nivel}
                onClick={() => setFiltroNivel(nivel)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroNivel === nivel
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {nivel === 'todos' ? 'Todos' : nivelStyles[nivel].label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 space-y-3">
          {alertasFiltrados.map((alerta) => {
            const nivel = nivelStyles[alerta.nivel]
            const status = statusStyles[alerta.status]
            const Icon = nivel.icon

            return (
              <div
                key={alerta.id}
                className="flex items-start justify-between gap-4 rounded-lg border border-slate-800 p-4 transition hover:border-slate-700"
              >
                <div className="flex items-start gap-3">
                  <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${nivel.bg}`}>
                    <Icon className={`h-4 w-4 ${nivel.text}`} />
                  </span>

                  <div>
                    <p className="text-sm text-slate-200">{alerta.titulo}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {alerta.ativo} • {alerta.categoria} • {alerta.tempo}
                    </p>
                  </div>
                </div>

                <div className="flex shrink-0 flex-col items-end gap-1.5">
                  <span className={`flex items-center gap-1.5 text-xs font-medium ${nivel.text}`}>
                    <span className={`h-1.5 w-1.5 rounded-full ${nivel.dot}`} />
                    {nivel.label}
                  </span>
                  <span className={`text-xs ${status.text}`}>{status.label}</span>
                </div>
              </div>
            )
          })}

          {alertasFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <CheckCircle2 className="h-8 w-8" />
              <p className="text-sm">Nenhum alerta encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Alertas