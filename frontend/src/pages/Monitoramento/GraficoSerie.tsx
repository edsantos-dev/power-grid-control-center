import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatarDataHora, formatarNumero } from '../../utils/formatacao'

type Ponto = { instante: string; valor: number | null }

type Props = {
  titulo: string
  unidade: string
  pontos: Ponto[]
  // Valor de referência exibido como linha tracejada, como o limiar de sobrecarga
  limiar?: { valor: number; rotulo: string }
}

// Cor de série única validada contra a superfície escura (#0f172a)
const COR_SERIE = '#3987e5'

const hora = (tempo: number) => new Date(tempo).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })

// Uma grandeza por gráfico: unidades diferentes nunca dividem o mesmo eixo
function GraficoSerie({ titulo, unidade, pontos, limiar }: Props) {
  const comValor = pontos.filter((p) => p.valor !== null)
  // Eixo em tempo real: intervalos irregulares entre leituras ficam proporcionais
  const dados = pontos.map((p) => ({ tempo: new Date(p.instante).getTime(), valor: p.valor }))
  // Um ponto entre lacunas não forma segmento; recebe marcador para não sumir do gráfico
  const isolado = (i: number) => dados[i]?.valor !== null && (dados[i - 1]?.valor ?? null) === null && (dados[i + 1]?.valor ?? null) === null

  function Marcador({ cx, cy, index }: { cx?: number; cy?: number; index?: number }) {
    if (index === undefined || cx === undefined || cy === undefined || !isolado(index)) return <g key={index} />
    return <circle key={index} cx={cx} cy={cy} r={4} fill={COR_SERIE} stroke="#0f172a" strokeWidth={2} />
  }

  return (
    <figure className="rounded-xl border border-slate-800 bg-slate-900 p-4">
      <figcaption className="mb-3 text-sm font-medium text-slate-200">
        {titulo} {unidade && <span className="text-slate-500">({unidade})</span>}
      </figcaption>

      {comValor.length === 0 ? (
        <p className="py-10 text-center text-sm text-slate-500">Sem leituras desta grandeza no período exibido.</p>
      ) : (
        <div className="h-56">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={dados} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid stroke="#1e293b" vertical={false} />
              <XAxis dataKey="tempo" type="number" scale="time" domain={['dataMin', 'dataMax']} tickFormatter={hora} stroke="#64748b" fontSize={11} tickLine={false} minTickGap={24} />
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} axisLine={false} width={56} domain={['auto', 'auto']} tickFormatter={(v: number) => formatarNumero(v)} />
              <Tooltip
                cursor={{ stroke: '#475569' }}
                contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: 8, fontSize: 12 }}
                labelStyle={{ color: '#cbd5e1' }}
                itemStyle={{ color: '#e2e8f0' }}
                labelFormatter={(tempo) => formatarDataHora(new Date(Number(tempo)).toISOString())}
                formatter={(valor) => [`${formatarNumero(Number(valor))} ${unidade}`.trim(), titulo]}
              />
              {limiar && (
                <ReferenceLine y={limiar.valor} stroke="#94a3b8" strokeDasharray="4 4" ifOverflow="extendDomain" label={{ value: limiar.rotulo, position: 'insideTopRight', fill: '#94a3b8', fontSize: 11 }} />
              )}
              {/* Segmentos retos entre leituras; pontos sem a grandeza ficam como lacuna */}
              <Line type="linear" dataKey="valor" stroke={COR_SERIE} strokeWidth={2} dot={(props) => <Marcador {...props} />} activeDot={{ r: 4 }} connectNulls={false} isAnimationActive={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}

      {comValor.length > 0 && (
        <details className="mt-2 text-xs text-slate-400">
          <summary className="cursor-pointer select-none">Ver dados</summary>
          <table className="mt-2 w-full">
            <thead>
              <tr className="text-left text-slate-500">
                <th className="font-normal">Medido em</th>
                <th className="font-normal">{titulo}{unidade && ` (${unidade})`}</th>
              </tr>
            </thead>
            <tbody>
              {[...comValor].reverse().map((p) => (
                <tr key={p.instante}>
                  <td>{formatarDataHora(p.instante)}</td>
                  <td>{formatarNumero(p.valor as number)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </figure>
  )
}

export default GraficoSerie
