import { Activity, Zap, Gauge, TrendingDown } from 'lucide-react'
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts'

const eletricasData = [
  { hora: '00h', tensao: 131, frequencia: 60.02 },
  { hora: '04h', tensao: 129, frequencia: 59.97 },
  { hora: '08h', tensao: 134, frequencia: 60.05 },
  { hora: '12h', tensao: 136, frequencia: 59.95 },
  { hora: '16h', tensao: 133, frequencia: 60.01 },
  { hora: '20h', tensao: 135, frequencia: 59.98 },
]

const potenciaData = [
  { hora: '00h', ativa: 620, reativa: 140 },
  { hora: '04h', ativa: 540, reativa: 120 },
  { hora: '08h', ativa: 780, reativa: 180 },
  { hora: '12h', ativa: 910, reativa: 210 },
  { hora: '16h', ativa: 860, reativa: 195 },
  { hora: '20h', ativa: 940, reativa: 220 },
]

const subestacoes = [
  { id: 'SE-01', nome: 'Subestação Norte', carga: 72, status: 'operacional' },
  { id: 'SE-02', nome: 'Subestação Central', carga: 91, status: 'atencao' },
  { id: 'SE-03', nome: 'Subestação Sul', carga: 58, status: 'operacional' },
  { id: 'SE-04', nome: 'Subestação Leste', carga: 0, status: 'manutencao' },
  { id: 'SE-05', nome: 'Subestação Oeste', carga: 0, status: 'critico' },
]

const alimentadores = [
  { id: 'AL-101', nome: 'Alimentador Norte-1', fluxo: 34.2, perdas: 3.1, status: 'operacional' },
  { id: 'AL-102', nome: 'Alimentador Central-1', fluxo: 48.7, perdas: 5.8, status: 'atencao' },
  { id: 'AL-103', nome: 'Alimentador Sul-1', fluxo: 29.5, perdas: 2.4, status: 'operacional' },
  { id: 'AL-104', nome: 'Alimentador Leste-1', fluxo: 12.1, perdas: 4.0, status: 'operacional' },
]

const statusStyles = {
  operacional: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  atencao: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Atenção' },
  critico: { dot: 'bg-red-500', text: 'text-red-400', label: 'Crítico' },
  manutencao: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

function Monitoramento() {
  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Monitoramento
        </h2>

        <p className="mt-2 text-slate-400">
          Variáveis elétricas e status operacional da rede em tempo real.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-400">Potência Ativa</p>
            <Zap className="h-4 w-4 text-blue-400" />
          </div>

          <p className="mt-2 text-3xl font-bold text-white">
            842.6 <span className="text-lg font-normal text-slate-400">MW</span>
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-400">Frequência da Rede</p>
            <Activity className="h-4 w-4 text-green-400" />
          </div>

          <p className="mt-2 text-3xl font-bold text-white">
            59.98 <span className="text-lg font-normal text-slate-400">Hz</span>
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-400">Fator de Potência</p>
            <Gauge className="h-4 w-4 text-blue-400" />
          </div>

          <p className="mt-2 text-3xl font-bold text-white">
            0.96
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-400">Perdas na Distribuição</p>
            <TrendingDown className="h-4 w-4 text-yellow-400" />
          </div>

          <p className="mt-2 text-3xl font-bold text-yellow-400">
            4.3%
          </p>
        </div>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <h3 className="text-lg font-semibold text-white">
            Tensão e Frequência (24h)
          </h3>

          <div className="mt-6 h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={eletricasData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="hora" stroke="#64748b" fontSize={12} />
                <YAxis yAxisId="left" stroke="#64748b" fontSize={12} />
                <YAxis yAxisId="right" orientation="right" domain={[59.8, 60.2]} stroke="#64748b" fontSize={12} />
                <Tooltip
                  contentStyle={{
                    backgroundColor: '#0f172a',
                    border: '1px solid #1e293b',
                    borderRadius: '8px',
                    color: '#fff',
                  }}
                />
                <Legend wrapperStyle={{ fontSize: '12px', color: '#94a3b8' }} />
                <Line yAxisId="left" type="monotone" dataKey="tensao" name="Tensão (kV)" stroke="#3b82f6" strokeWidth={2} dot={false} />
                <Line yAxisId="right" type="monotone" dataKey="frequencia" name="Frequência (Hz)" stroke="#22c55e" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <h3 className="text-lg font-semibold text-white">
            Potência Ativa e Reativa (24h)
          </h3>

          <div className="mt-6 h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={potenciaData}>
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
                <Legend wrapperStyle={{ fontSize: '12px', color: '#94a3b8' }} />
                <Line type="monotone" dataKey="ativa" name="P. Ativa (MW)" stroke="#3b82f6" strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="reativa" name="P. Reativa (MVAr)" stroke="#a855f7" strokeWidth={2} dot={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <h3 className="text-lg font-semibold text-white">
          Status das Subestações
        </h3>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Status</th>
                <th className="pb-3 font-normal">Carga</th>
              </tr>
            </thead>
            <tbody>
              {subestacoes.map((s) => {
                const style = statusStyles[s.status as keyof typeof statusStyles]

                return (
                  <tr key={s.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 text-slate-200">{s.nome}</td>
                    <td className="py-3">
                      <span className="flex items-center gap-2">
                        <span className={`h-2 w-2 rounded-full ${style.dot}`} />
                        <span className={style.text}>{style.label}</span>
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{s.carga}%</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <h3 className="text-lg font-semibold text-white">
          Fluxo de Potência - Alimentadores
        </h3>

        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          {alimentadores.map((f) => {
            const style = statusStyles[f.status as keyof typeof statusStyles]

            return (
              <div key={f.id} className="flex items-center justify-between rounded-lg border border-slate-800 p-4">
                <div>
                  <p className="text-sm text-slate-200">{f.nome}</p>
                  <p className="mt-1 text-xs text-slate-500">Perdas: {f.perdas}%</p>
                </div>

                <div className="text-right">
                  <p className="font-semibold text-white">{f.fluxo} MW</p>
                  <span className={`mt-1 flex items-center justify-end gap-1.5 text-xs ${style.text}`}>
                    <span className={`h-1.5 w-1.5 rounded-full ${style.dot}`} />
                    {style.label}
                  </span>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

export default Monitoramento