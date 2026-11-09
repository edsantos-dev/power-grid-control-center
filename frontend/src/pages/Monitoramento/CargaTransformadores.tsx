import { useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, Clock } from 'lucide-react'
import { listarMedicoesTransformador } from '../../services/transformadores'
import type { MedicaoTransformador, TransformadorSnapshot } from '../../types'
import { formatarNumero, formatarPercentual } from '../../utils/formatacao'
import GraficoSerie from './GraficoSerie'
import { descreverIdade, estaAtrasada } from './monitoramento'

type Props = {
  transformadores: TransformadorSnapshot[]
  // Relógio do servidor no snapshot, contra o qual a idade das leituras é medida
  agora: number
  // Instante da última atualização da tela: cada mudança consulta de novo a série do gráfico
  atualizadoEm: number
  carregando: boolean
  // Só informado quando não há leitura anterior para mostrar
  erro: string
}

// Mesmo limiar estrito da geração de alertas (US21)
const LIMIAR_SOBRECARGA = 100

function CelulaCarga({ transformador, agora }: { transformador: TransformadorSnapshot; agora: number }) {
  const { cargaPercentual: carga, potenciaAparenteMva: potencia, capacidadeReferenciaMva: capacidade } = transformador
  if (!carga) return <span className="text-xs text-slate-500">Sem medição</span>

  const sobrecarga = carga.valor > LIMIAR_SOBRECARGA
  const atrasada = estaAtrasada(carga.medidoEm, agora)

  return (
    <div>
      <p className={atrasada ? 'text-slate-400' : 'text-slate-100'}>{formatarPercentual(carga.valor)}</p>
      <p className={`mt-0.5 flex items-center gap-1 text-xs ${sobrecarga ? 'text-orange-400' : 'text-green-400'}`}>
        {sobrecarga ? <AlertTriangle className="h-3 w-3" /> : <CheckCircle2 className="h-3 w-3" />}
        {sobrecarga ? 'Sobrecarga' : 'Dentro da capacidade'}
      </p>
      <p className="mt-0.5 text-xs text-slate-400">
        {potencia && capacidade ? `${formatarNumero(potencia.valor)} MVA de ${formatarNumero(capacidade.valor)} MVA` : ''}
      </p>
      <p className="mt-0.5 text-xs text-slate-500">
        {descreverIdade(carga.medidoEm, agora)} · {carga.origemLeitura === 'SIMULADA' ? 'Simulada' : 'Real'}
      </p>
      {atrasada && (
        <p className="mt-0.5 flex items-center gap-1 text-xs text-yellow-400">
          <Clock className="h-3 w-3" />
          Atrasada
        </p>
      )}
    </div>
  )
}

function CargaTransformadores({ transformadores, agora, atualizadoEm, carregando, erro }: Props) {
  const [selecionado, setSelecionado] = useState<number | null>(null)
  const [serie, setSerie] = useState<MedicaoTransformador[]>([])

  // Abre no primeiro transformador com medição, para os sem leitura não esconderem os demais
  const idGrafico = selecionado ?? (transformadores.find((t) => t.cargaPercentual) ?? transformadores[0])?.ativoId ?? null

  useEffect(() => {
    if (idGrafico === null) return
    let ativo = true

    listarMedicoesTransformador(idGrafico, 100)
      .then((lista) => ativo && setSerie([...lista].reverse()))
      .catch(() => ativo && setSerie([]))

    return () => {
      ativo = false
    }
  }, [idGrafico, atualizadoEm])

  const transformadorGrafico = transformadores.find((t) => t.ativoId === idGrafico)

  return (
    <>
      <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-transformadores">
        <h3 id="titulo-transformadores" className="text-lg font-semibold text-white">
          Carga dos transformadores
        </h3>
        <p className="mt-1 text-xs text-slate-500">
          Carga = potência aparente medida ÷ capacidade de referência registrada na própria medição, calculada pelo servidor e sem limite de 100%.
        </p>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Transformador</th>
                <th className="pb-3 font-normal">Capacidade de referência</th>
                <th className="pb-3 font-normal">Carga mais recente</th>
              </tr>
            </thead>
            <tbody>
              {transformadores.map((t) => (
                <tr
                  key={t.ativoId}
                  aria-selected={t.ativoId === idGrafico}
                  className={`border-b border-slate-800/60 align-top last:border-0 ${t.ativoId === idGrafico ? 'bg-slate-800/40' : ''}`}
                >
                  <td className="py-3 pr-4">
                    <button type="button" onClick={() => setSelecionado(t.ativoId)} className="text-left text-slate-200 hover:underline">
                      {t.nome}
                    </button>
                  </td>
                  <td className="py-3 pr-4 text-slate-300">
                    {t.capacidadeReferenciaMva ? `${formatarNumero(t.capacidadeReferenciaMva.valor)} MVA` : <span className="text-slate-500">—</span>}
                  </td>
                  <td className="py-3 pr-4">
                    <CelulaCarga transformador={t} agora={agora} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {erro && <p className="py-6 text-center text-sm text-red-400">{erro}</p>}
          {!erro && !carregando && transformadores.length === 0 && (
            <p className="py-6 text-center text-sm text-slate-500">Nenhum transformador cadastrado.</p>
          )}
        </div>
      </section>

      {transformadorGrafico && (
        <section className="mt-6" aria-label={`Carga de ${transformadorGrafico.nome}`}>
          <GraficoSerie
            titulo={`Carga de ${transformadorGrafico.nome}`}
            unidade="%"
            limiar={{ valor: LIMIAR_SOBRECARGA, rotulo: 'Limiar 100%' }}
            pontos={serie.map((m) => ({ instante: m.medidoEm, valor: m.cargaPercentual }))}
          />
        </section>
      )}
    </>
  )
}

export default CargaTransformadores
