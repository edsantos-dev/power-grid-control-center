import { useCallback, useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { extrairErroApi } from '../../services/api'
import { listarMedicoesLinha } from '../../services/linhas'
import { consultarSnapshot } from '../../services/monitoramento'
import { listarSubestacoes } from '../../services/subestacoes'
import type { MedicaoLinha, SnapshotMonitoramento, Subestacao } from '../../types'
import CargaTransformadores from './CargaTransformadores'
import CelulaLeitura from './CelulaLeitura'
import GraficoSerie from './GraficoSerie'
import {
  CADENCIA_MS,
  descreverIdade,
  FREQUENCIA_NOMINAL_HZ,
  fatorPotenciaDoSnapshot,
  GRANDEZAS_LINHA,
  leituraDaAmostra,
  linhaComLeitura,
  type Leitura,
} from './monitoramento'

function Monitoramento() {
  const [snapshot, setSnapshot] = useState<SnapshotMonitoramento | null>(null)
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

  // Uma única consulta traz a última leitura de cada grandeza de todas as linhas e transformadores.
  // Se uma atualização falhar, as leituras anteriores continuam na tela junto com o aviso.
  const atualizar = useCallback(async () => {
    try {
      setSnapshot(await consultarSnapshot())
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

  const linhas = snapshot?.linhas ?? []
  // A idade das leituras é medida contra o relógio do servidor, que também carimba o snapshot
  const referencia = snapshot ? new Date(snapshot.consultadoEm).getTime() : atualizadoEm
  const linhaGrafico = linhaSelecionada ?? (linhas.find(linhaComLeitura) ?? linhas[0])?.ativoId ?? null

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

  const linhaDoGrafico = linhas.find((l) => l.ativoId === linhaGrafico)
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
              {linhas.map((linha) => (
                  <tr
                    key={linha.ativoId}
                    aria-selected={linha.ativoId === linhaGrafico}
                    className={`border-b border-slate-800/60 align-top last:border-0 ${linha.ativoId === linhaGrafico ? 'bg-slate-800/40' : ''}`}
                  >
                    <td className="py-3 pr-4">
                      <button type="button" onClick={() => setLinhaSelecionada(linha.ativoId)} className="text-left text-slate-200 hover:underline">
                        {linha.nome}
                      </button>
                      <p className="mt-1 text-xs text-slate-500">Operação {linha.tensaoOperacao} kV</p>
                    </td>

                    {GRANDEZAS_LINHA.map((g) => {
                      const fator = g.campo === 'fatorPotencia' ? fatorPotenciaDoSnapshot(linha) : null
                      const leitura = g.campo === 'fatorPotencia' ? (fator?.tipo === 'calculado' ? fator.leitura : null) : leituraDaAmostra(linha[g.campo])

                      return (
                        <td key={g.campo} className="py-3 pr-4">
                          {fator?.tipo === 'indefinido' ? (
                            <div className="text-xs">
                              <p className="text-slate-300">Indefinido</p>
                              <p className="mt-0.5 text-slate-500">P e Q iguais a zero</p>
                              <p className="mt-0.5 text-slate-500">{descreverIdade(fator.medidoEm, referencia)}</p>
                            </div>
                          ) : (
                            <CelulaLeitura
                              leitura={leitura}
                              unidade={g.unidade}
                              agora={referencia}
                              referenciaKv={g.campo === 'tensaoKv' ? linha.tensaoOperacao : undefined}
                              nota={g.comTerminal ? terminal(leitura) : g.campo === 'frequenciaHz' ? `Nominal ${FREQUENCIA_NOMINAL_HZ} Hz` : g.campo === 'fatorPotencia' ? 'Calculado de P e Q' : undefined}
                            />
                          )}
                        </td>
                      )
                    })}
                  </tr>
              ))}
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

      <CargaTransformadores
        transformadores={snapshot?.transformadores ?? []}
        agora={referencia}
        atualizadoEm={atualizadoEm}
        carregando={carregando}
        erro={snapshot ? '' : erro}
      />
    </div>
  )
}

export default Monitoramento
