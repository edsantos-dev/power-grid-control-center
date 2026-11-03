import { useCallback, useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { extrairErroApi } from '../../services/api'
import { listarLinhas, listarMedicoesLinha } from '../../services/linhas'
import { listarSubestacoes } from '../../services/subestacoes'
import type { LinhaTransmissao, MedicaoLinha, Subestacao } from '../../types'
import CargaTransformadores from './CargaTransformadores'
import CelulaLeitura from './CelulaLeitura'
import GraficoSerie from './GraficoSerie'
import {
  CADENCIA_MS,
  descreverIdade,
  FREQUENCIA_NOMINAL_HZ,
  GRANDEZAS_LINHA,
  ultimoFatorPotencia,
  ultimaLeitura,
  type Leitura,
} from './monitoramento'

type Medicoes = { estado: 'pronta'; lista: MedicaoLinha[] } | { estado: 'erro' }

function Monitoramento() {
  const [linhas, setLinhas] = useState<LinhaTransmissao[]>([])
  const [medicoes, setMedicoes] = useState<Record<number, Medicoes>>({})
  const [serie, setSerie] = useState<MedicaoLinha[]>([])
  const [linhaSelecionada, setLinhaSelecionada] = useState<number | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [atualizadoEm, setAtualizadoEm] = useState<number>(() => Date.now())
  const [automatico, setAutomatico] = useState(true)
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])

  // Os nomes dos terminais não mudam com a cadência das leituras
  useEffect(() => {
    listarSubestacoes().then(setSubestacoes).catch(() => setSubestacoes([]))
  }, [])

  function terminal(leitura: Leitura | null) {
    if (!leitura) return undefined
    if (leitura.subestacaoReferenciaId === null) return 'Sem terminal de referência'
    const nome = subestacoes.find((s) => s.id === leitura.subestacaoReferenciaId)?.nome ?? `Subestação #${leitura.subestacaoReferenciaId}`
    return `Referência: ${nome}`
  }

  // Cada linha consulta suas leituras recentes; a falha de uma não esconde as demais
  const atualizar = useCallback(async () => {
    try {
      const lista = await listarLinhas()
      const resultados = await Promise.allSettled(lista.map((l) => listarMedicoesLinha(l.id)))
      setLinhas([...lista].sort((a, b) => a.id - b.id))
      setMedicoes(
        Object.fromEntries(
          lista.map((l, i) => {
            const resultado = resultados[i]
            return [l.id, resultado.status === 'fulfilled' ? { estado: 'pronta', lista: resultado.value } : { estado: 'erro' }]
          })
        )
      )
      setErro('')
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setAtualizadoEm(Date.now())
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    atualizar()
  }, [atualizar])

  useEffect(() => {
    if (!automatico) return
    const intervalo = setInterval(atualizar, CADENCIA_MS)
    return () => clearInterval(intervalo)
  }, [automatico, atualizar])

  const linhaGrafico = linhaSelecionada ?? linhas[0]?.id ?? null

  // A série do gráfico acompanha a mesma cadência das leituras
  useEffect(() => {
    if (linhaGrafico === null) return
    let ativo = true

    listarMedicoesLinha(linhaGrafico, 100)
      .then((lista) => ativo && setSerie([...lista].reverse()))
      .catch(() => ativo && setSerie([]))

    return () => {
      ativo = false
    }
  }, [linhaGrafico, atualizadoEm])

  const linhaDoGrafico = linhas.find((l) => l.id === linhaGrafico)
  // Só grandezas medidas no período ganham gráfico; as demais são listadas numa nota
  const grandezasComDados = GRANDEZAS_LINHA.filter((g) => serie.some((m) => m[g.campo] !== null))
  const grandezasSemDados = GRANDEZAS_LINHA.filter((g) => !grandezasComDados.includes(g))

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Monitoramento
          </h2>

          <p className="mt-2 text-slate-400">
            Leituras mais recentes da rede, com origem, instante e indicação de atraso.
          </p>
        </div>

        <div className="flex flex-col items-end gap-2 text-xs text-slate-400">
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-2">
              <input type="checkbox" checked={automatico} onChange={(e) => setAutomatico(e.target.checked)} />
              Atualizar a cada {CADENCIA_MS / 1000} s
            </label>

            <button
              type="button"
              onClick={atualizar}
              className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-slate-300 transition hover:bg-slate-800"
            >
              <RefreshCw className="h-3.5 w-3.5" />
              Atualizar agora
            </button>
          </div>

          <p role="status">
            Atualizado às {new Date(atualizadoEm).toLocaleTimeString('pt-BR')} · leituras com mais de 5 min são marcadas como atrasadas
          </p>
        </div>
      </div>

      <section className="rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-linhas">
        <h3 id="titulo-linhas" className="text-lg font-semibold text-white">
          Linhas de transmissão
        </h3>
        <p className="mt-1 text-xs text-slate-500">
          Potências positivas indicam fluxo entrando na linha pelo terminal de referência da fonte; negativas, saindo.
        </p>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Linha</th>
                {GRANDEZAS_LINHA.map((g) => (
                  <th key={g.campo} className="pb-3 font-normal">{g.rotulo}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {linhas.map((linha) => {
                const estado = medicoes[linha.id]

                return (
                  <tr
                    key={linha.id}
                    aria-selected={linha.id === linhaGrafico}
                    className={`border-b border-slate-800/60 align-top last:border-0 ${linha.id === linhaGrafico ? 'bg-slate-800/40' : ''}`}
                  >
                    <td className="py-3 pr-4">
                      <button type="button" onClick={() => setLinhaSelecionada(linha.id)} className="text-left text-slate-200 hover:underline">
                        {linha.nome}
                      </button>
                      <p className="mt-1 text-xs text-slate-500">Operação {linha.tensaoOperacaoKv} kV</p>
                    </td>

                    {GRANDEZAS_LINHA.map((g) => {
                      const fator = g.campo === 'fatorPotencia' && estado?.estado === 'pronta' ? ultimoFatorPotencia(estado.lista) : null
                      const leitura =
                        estado?.estado !== 'pronta'
                          ? null
                          : g.campo === 'fatorPotencia'
                            ? fator?.tipo === 'calculado' ? fator.leitura : null
                            : ultimaLeitura(estado.lista, g.campo)

                      return (
                        <td key={g.campo} className="py-3 pr-4">
                          {!estado ? (
                            <span className="text-xs text-slate-500">...</span>
                          ) : estado.estado === 'erro' ? (
                            <span className="text-xs text-slate-500">Indisponível</span>
                          ) : fator?.tipo === 'indefinido' ? (
                            <div className="text-xs">
                              <p className="text-slate-300">Indefinido</p>
                              <p className="mt-0.5 text-slate-500">P e Q iguais a zero</p>
                              <p className="mt-0.5 text-slate-500">{descreverIdade(fator.medidoEm, atualizadoEm)}</p>
                            </div>
                          ) : (
                            <CelulaLeitura
                              leitura={leitura}
                              unidade={g.unidade}
                              agora={atualizadoEm}
                              referenciaKv={g.campo === 'tensaoKv' ? linha.tensaoOperacaoKv : undefined}
                              nota={g.comTerminal ? terminal(leitura) : g.campo === 'frequenciaHz' ? `Nominal ${FREQUENCIA_NOMINAL_HZ} Hz` : g.campo === 'fatorPotencia' ? 'Calculado de P e Q' : undefined}
                            />
                          )}
                        </td>
                      )
                    })}
                  </tr>
                )
              })}
            </tbody>
          </table>

          {carregando && <p className="py-10 text-center text-sm text-slate-500">Carregando leituras...</p>}

          {!carregando && erro && (
            <div className="flex flex-col items-center gap-3 py-10">
              <p className="text-sm text-red-400">{erro}</p>
              <button
                type="button"
                onClick={atualizar}
                className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
              >
                Tentar novamente
              </button>
            </div>
          )}

          {!carregando && !erro && linhas.length === 0 && (
            <p className="py-10 text-center text-sm text-slate-500">Nenhuma linha de transmissão cadastrada.</p>
          )}
        </div>
      </section>

      {linhaDoGrafico && (
        <section className="mt-6" aria-label={`Série de ${linhaDoGrafico.nome}`}>
          <h3 className="mb-3 text-lg font-semibold text-white">
            {linhaDoGrafico.nome} <span className="text-sm font-normal text-slate-500">· últimas {serie.length} leituras</span>
          </h3>

          {grandezasComDados.length === 0 && (
            <p className="rounded-xl border border-slate-800 bg-slate-900 py-10 text-center text-sm text-slate-500">
              Nenhuma leitura registrada para esta linha.
            </p>
          )}

          <div className="grid gap-4 lg:grid-cols-2">
            {grandezasComDados.map((g) => (
              <GraficoSerie
                key={g.campo}
                titulo={g.rotulo}
                unidade={g.unidade}
                pontos={serie.map((m) => ({ instante: m.medidoEm, valor: m[g.campo] }))}
              />
            ))}
          </div>

          {grandezasComDados.length > 0 && grandezasSemDados.length > 0 && (
            <p className="mt-3 text-xs text-slate-500">
              Sem leituras no período: {grandezasSemDados.map((g) => g.rotulo.toLowerCase()).join(', ')}.
            </p>
          )}
        </section>
      )}

      <CargaTransformadores atualizadoEm={atualizadoEm} />
    </div>
  )
}

export default Monitoramento
