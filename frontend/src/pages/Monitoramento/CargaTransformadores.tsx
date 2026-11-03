import { useEffect, useState } from 'react'
import { AlertTriangle, CheckCircle2, Clock } from 'lucide-react'
import { listarMedicoesTransformador, listarTransformadores } from '../../services/transformadores'
import type { MedicaoTransformador, Transformador } from '../../types'
import { formatarNumero, formatarPercentual } from '../../utils/formatacao'
import GraficoSerie from './GraficoSerie'
import { descreverIdade, estaAtrasada } from './monitoramento'

type Props = {
  // Instante da última atualização da tela: cada mudança dispara nova consulta
  atualizadoEm: number
}

type Medicoes = { estado: 'pronta'; lista: MedicaoTransformador[] } | { estado: 'erro' }

// Mesmo limiar estrito da geração de alertas (US21)
const LIMIAR_SOBRECARGA = 100

function CelulaCarga({ medicao, agora }: { medicao: MedicaoTransformador | undefined; agora: number }) {
  if (!medicao) return <span className="text-xs text-slate-500">Sem medição</span>

  const sobrecarga = medicao.cargaPercentual > LIMIAR_SOBRECARGA
  const atrasada = estaAtrasada(medicao.medidoEm, agora)

  return (
    <div>
      <p className={atrasada ? 'text-slate-400' : 'text-slate-100'}>{formatarPercentual(medicao.cargaPercentual)}</p>
      <p className={`mt-0.5 flex items-center gap-1 text-xs ${sobrecarga ? 'text-orange-400' : 'text-green-400'}`}>
        {sobrecarga ? <AlertTriangle className="h-3 w-3" /> : <CheckCircle2 className="h-3 w-3" />}
        {sobrecarga ? 'Sobrecarga' : 'Dentro da capacidade'}
      </p>
      <p className="mt-0.5 text-xs text-slate-400">
        {formatarNumero(medicao.potenciaAparenteMva)} MVA de {formatarNumero(medicao.capacidadeReferenciaMva)} MVA
      </p>
      <p className="mt-0.5 text-xs text-slate-500">
        {descreverIdade(medicao.medidoEm, agora)} · {medicao.origem === 'SIMULADA' ? 'Simulada' : 'Real'}
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

function CargaTransformadores({ atualizadoEm }: Props) {
  const [transformadores, setTransformadores] = useState<Transformador[]>([])
  const [medicoes, setMedicoes] = useState<Record<number, Medicoes>>({})
  const [selecionado, setSelecionado] = useState<number | null>(null)
  const [serie, setSerie] = useState<MedicaoTransformador[]>([])
  const [erro, setErro] = useState('')

  useEffect(() => {
    let ativo = true

    listarTransformadores()
      .then(async (lista) => {
        const resultados = await Promise.allSettled(lista.map((t) => listarMedicoesTransformador(t.id)))
        if (!ativo) return
        setTransformadores([...lista].sort((a, b) => a.id - b.id))
        setMedicoes(
          Object.fromEntries(
            lista.map((t, i) => {
              const resultado = resultados[i]
              return [t.id, resultado.status === 'fulfilled' ? { estado: 'pronta', lista: resultado.value } : { estado: 'erro' }]
            })
          )
        )
        setErro('')
      })
      .catch(() => ativo && setErro('Não foi possível consultar os transformadores.'))

    return () => {
      ativo = false
    }
  }, [atualizadoEm])

  const idGrafico = selecionado ?? transformadores[0]?.id ?? null

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

  const transformadorGrafico = transformadores.find((t) => t.id === idGrafico)

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
                <th className="pb-3 font-normal">Capacidade cadastrada</th>
                <th className="pb-3 font-normal">Carga mais recente</th>
              </tr>
            </thead>
            <tbody>
              {transformadores.map((t) => {
                const estado = medicoes[t.id]

                return (
                  <tr
                    key={t.id}
                    aria-selected={t.id === idGrafico}
                    className={`border-b border-slate-800/60 align-top last:border-0 ${t.id === idGrafico ? 'bg-slate-800/40' : ''}`}
                  >
                    <td className="py-3 pr-4">
                      <button type="button" onClick={() => setSelecionado(t.id)} className="text-left text-slate-200 hover:underline">
                        {t.nome}
                      </button>
                    </td>
                    <td className="py-3 pr-4 text-slate-300">{formatarNumero(t.capacidadeMva)} MVA</td>
                    <td className="py-3 pr-4">
                      {!estado ? (
                        <span className="text-xs text-slate-500">...</span>
                      ) : estado.estado === 'erro' ? (
                        <span className="text-xs text-slate-500">Indisponível</span>
                      ) : (
                        <CelulaCarga medicao={estado.lista[0]} agora={atualizadoEm} />
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {erro && <p className="py-6 text-center text-sm text-red-400">{erro}</p>}
          {!erro && transformadores.length === 0 && (
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
