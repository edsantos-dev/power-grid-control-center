import { AlertTriangle, ArrowUpRight } from 'lucide-react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'

const consumoData = [
  { hora: '00h', consumo: 320, geracao: 280 },
  { hora: '04h', consumo: 280, geracao: 250 },
  { hora: '08h', consumo: 420, geracao: 390 },
  { hora: '12h', consumo: 510, geracao: 480 },
  { hora: '16h', consumo: 470, geracao: 460 },
  { hora: '20h', consumo: 540, geracao: 500 },
]

const alertasRecentes = [
  { id: 1, titulo: 'Transformador T-14 acima da capacidade', nivel: 'critico', tempo: '2 min atrás' },
  { id: 2, titulo: 'Subestação SE-07 com oscilação de tensão', nivel: 'atencao', tempo: '18 min atrás' },
  { id: 3, titulo: 'Falha de comunicação no medidor M-231', nivel: 'atencao', tempo: '41 min atrás' },
]

const nivelStyles = {
  critico: { dot: 'bg-red-500', text: 'text-red-400' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400' },
}

function Dashboard() {
  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Dashboard
        </h2>

        <p className="mt-2 text-slate-400">
          Visão geral da operação da rede elétrica.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">
            Subestações
          </p>

          <p className="mt-2 text-3xl font-bold text-white">
            24
          </p>

          <p className="mt-2 text-sm text-green-400">
            Operacionais
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">
            Transformadores
          </p>

          <p className="mt-2 text-3xl font-bold text-white">
            48
          </p>

          <p className="mt-2 text-sm text-green-400">
            Funcionamento normal
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">
            Alertas ativos
          </p>

          <p className="mt-2 text-3xl font-bold text-yellow-400">
            12
          </p>

          <p className="mt-2 text-sm text-yellow-400">
            Requer atenção
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">
            Falhas críticas
          </p>

          <p className="mt-2 text-3xl font-bold text-red-500">
            2
          </p>

          <p className="mt-2 text-sm text-red-400">
            Ação necessária
          </p>
        </div>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-3">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 xl:col-span-2">
          <h3 className="text-lg font-semibold text-white">
            Consumo x Geração (24h)
          </h3>

          <div className="mt-6 h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={consumoData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="hora" stroke="#64748b" fontSize={12} />
                <YAxis stroke="#64748b" fontSize={12} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0f172a',
                    border: '1px solid #1e293b',
                    borderRadius: '8px',
                    color: '#fff',
                  }}
                />
                <Line type="monotone" dataKey="consumo" stroke="#3b82f6" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="geracao" stroke="#22c55e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-semibold text-white">
              Alertas recentes
            </h3>

            <AlertTriangle className="h-5 w-5 text-yellow-400" />
          </div>

          <div className="mt-4 space-y-4">
            {alertasRecentes.map((alerta) => {
              const style = nivelStyles[alerta.nivel as keyof typeof nivelStyles]

              return (
                <div key={alerta.id} className="flex items-start gap-3">
                  <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${style.dot}`} />

                  <div>
                    <p className="text-sm text-slate-200">
                      {alerta.titulo}
                    </p>

                    <p className={`mt-1 text-xs ${style.text}`}>
                      {alerta.tempo}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>

          <button className="mt-5 flex items-center gap-1 text-sm text-blue-400 transition hover:text-blue-300">
            Ver todos os alertas
            <ArrowUpRight className="h-4 w-4" />
          </button>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <h3 className="text-lg font-semibold text-white">
          Estado geral da rede
        </h3>

        <div className="mt-6 flex items-center gap-3">
          <div className="h-3 w-3 rounded-full bg-green-500" />

          <span className="text-slate-300">
            Sistema operacional
          </span>
        </div>
      </div>
    </div>
  )
}

export default Dashboard