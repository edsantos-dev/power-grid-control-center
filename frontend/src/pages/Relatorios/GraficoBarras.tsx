import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatarNumero } from '../../utils/formatacao'

export type Barra = { rotulo: string; valor: number | null }

type Props = {
  titulo: string
  unidade: string
  barras: Barra[]
}

// Mesma cor de série única validada para a superfície escura do monitoramento
const COR_SERIE = '#3987e5'

// Uma medida por gráfico; períodos sem dado ficam sem barra em vez de virarem zero
function GraficoBarras({ titulo, unidade, barras }: Props) {
  const semDado = barras.filter((b) => b.valor === null).length

  return (
    <figure className="rounded-xl border border-slate-800 bg-slate-950 p-4">
      <figcaption className="mb-3 text-sm font-medium text-slate-200">
        {titulo} <span className="text-slate-500">({unidade})</span>
      </figcaption>

      <div className="h-52">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={barras} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap={2}>
            <CartesianGrid stroke="#1e293b" vertical={false} />
            <XAxis dataKey="rotulo" stroke="#64748b" fontSize={11} tickLine={false} minTickGap={12} />
            <YAxis stroke="#64748b" fontSize={11} tickLine={false} axisLine={false} width={56} tickFormatter={(v: number) => formatarNumero(v)} />
            <Tooltip
              cursor={{ fill: '#1e293b' }}
              contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: 8, fontSize: 12 }}
              labelStyle={{ color: '#cbd5e1' }}
              itemStyle={{ color: '#e2e8f0' }}
              formatter={(valor) => [valor === null || valor === undefined ? 'Dados insuficientes' : `${formatarNumero(Number(valor))} ${unidade}`, titulo]}
            />
            <Bar dataKey="valor" fill={COR_SERIE} radius={[4, 4, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {semDado > 0 && <p className="mt-2 text-xs text-yellow-400">{semDado} período(s) com dados insuficientes, sem barra.</p>}
    </figure>
  )
}

export default GraficoBarras
