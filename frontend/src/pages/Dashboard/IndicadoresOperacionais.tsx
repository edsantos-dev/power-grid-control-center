import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, Info } from 'lucide-react'
import { extrairErroApi } from '../../services/api'
import { consultarIndicadores } from '../../services/indicadores'
import { listarSubestacoes } from '../../services/subestacoes'
import type { IndicadoresOperacionais as Indicadores, Subestacao, TipoAtivoIndicador } from '../../types'
import { ESTADO_STYLES, ESTADOS } from '../../utils/constants'
import { formatarDataHora, formatarDuracao, formatarPercentual, inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'

const TIPOS: { tipo: TipoAtivoIndicador; rotulo: string }[] = [
  { tipo: 'SUBESTACAO', rotulo: 'Subestações' },
  { tipo: 'ALIMENTADOR', rotulo: 'Alimentadores' },
  { tipo: 'TRANSFORMADOR', rotulo: 'Transformadores' },
  { tipo: 'LINHA', rotulo: 'Linhas' },
  { tipo: 'USINA', rotulo: 'Usinas' },
]

const diaLocal = (data: Date) =>
  `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`

// Disponibilidade só tem valor com população ativa e registros consistentes; ainda assim é uma estimativa
const SITUACAO = {
  COBERTURA_NAO_AFERIDA: { texto: 'Estimativa: a cobertura do monitoramento não é aferida.', classe: 'text-slate-400', icone: Info },
  SEM_POPULACAO_ATIVA: { texto: 'Sem ativos no escopo: não há base para calcular a disponibilidade.', classe: 'text-slate-400', icone: Info },
  DADOS_INCONSISTENTES: { texto: 'Há registros de interrupção inconsistentes: a disponibilidade não é calculada.', classe: 'text-yellow-400', icone: AlertTriangle },
}

function Cartao({ rotulo, valor, detalhe }: { rotulo: string; valor: string; detalhe?: string }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950 p-5">
      <p className="text-sm text-slate-400">{rotulo}</p>
      <p className="mt-2 text-2xl font-bold text-white">{valor}</p>
      {detalhe && <p className="mt-1 text-xs text-slate-500">{detalhe}</p>}
    </div>
  )
}

function IndicadoresOperacionais() {
  const hoje = new Date()
  const [de, setDe] = useState(() => diaLocal(new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() - 6)))
  const [ate, setAte] = useState(() => diaLocal(hoje))
  const [tipos, setTipos] = useState<TipoAtivoIndicador[]>(TIPOS.map((t) => t.tipo))
  const [subestacaoId, setSubestacaoId] = useState('')
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])
  const [resultado, setResultado] = useState<Indicadores | null>(null)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  useEffect(() => {
    listarSubestacoes().then(setSubestacoes).catch(() => setSubestacoes([]))
  }, [])

  const invalido = !de || !ate ? 'Informe o período.' : ate < de ? 'A data final deve ser igual ou posterior à inicial.' : tipos.length === 0 ? 'Selecione ao menos um tipo de ativo.' : ''

  // O fim do período em andamento é o instante atual, pois o backend recusa fim futuro
  const periodo = useMemo(
    () => (invalido ? null : { dataInicio: inicioDoDiaLocal(de), dataFim: new Date(Math.min(new Date(inicioDoDiaSeguinteLocal(ate)).getTime(), Date.now())).toISOString() }),
    [de, ate, invalido]
  )

  useEffect(() => {
    if (!periodo) return
    let ativo = true
    setCarregando(true)

    consultarIndicadores({ ...periodo, tipos, subestacaoId: subestacaoId ? Number(subestacaoId) : undefined })
      .then((dados) => {
        if (!ativo) return
        setResultado(dados)
        setErro('')
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [periodo, tipos, subestacaoId])

  function alternar(tipo: TipoAtivoIndicador) {
    setTipos((atuais) => (atuais.includes(tipo) ? atuais.filter((t) => t !== tipo) : [...atuais, tipo]))
  }

  const situacao = resultado ? SITUACAO[resultado.situacaoDisponibilidade] : null
  const tiposPresentes = TIPOS.filter((t) => resultado?.quantidadeAtivos.some((q) => q.tipoAtivo === t.tipo))
  const quantidade = (tipo: TipoAtivoIndicador, estado: string) =>
    resultado?.quantidadeAtivos.find((q) => q.tipoAtivo === tipo && q.estadoOperacional === estado)?.quantidade ?? 0

  return (
    <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-indicadores">
      <h3 id="titulo-indicadores" className="text-lg font-semibold text-white">Indicadores de disponibilidade e desempenho</h3>
      <p className="mt-1 text-xs text-slate-500">
        Calculados a partir das interrupções registradas no período. Disponibilidade = (tempo observado − tempo interrompido) ÷ tempo observado.
      </p>

      <div className="mt-4 flex flex-wrap items-end gap-4">
        <div>
          <label htmlFor="kpi-de" className="mb-1 block text-xs text-slate-400">De</label>
          <input id="kpi-de" type="date" value={de} max={diaLocal(hoje)} onChange={(e) => setDe(e.target.value)} className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white" />
        </div>
        <div>
          <label htmlFor="kpi-ate" className="mb-1 block text-xs text-slate-400">Até</label>
          <input id="kpi-ate" type="date" value={ate} max={diaLocal(hoje)} onChange={(e) => setAte(e.target.value)} className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white" />
        </div>
        <div>
          <label htmlFor="kpi-subestacao" className="mb-1 block text-xs text-slate-400">Subestação</label>
          <select id="kpi-subestacao" value={subestacaoId} onChange={(e) => setSubestacaoId(e.target.value)} className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white">
            <option value="">Toda a rede</option>
            {subestacoes.map((s) => (
              <option key={s.id} value={s.id}>{s.nome}</option>
            ))}
          </select>
        </div>
        <fieldset className="flex flex-wrap gap-3 text-xs text-slate-300">
          <legend className="mb-1 text-xs text-slate-400">Tipos de ativo</legend>
          {TIPOS.map((t) => (
            <label key={t.tipo} className="flex items-center gap-1.5">
              <input type="checkbox" checked={tipos.includes(t.tipo)} onChange={() => alternar(t.tipo)} />
              {t.rotulo}
            </label>
          ))}
        </fieldset>
      </div>

      {invalido && <p className="mt-3 text-sm text-red-400">{invalido}</p>}
      {!invalido && erro && <p className="mt-3 text-sm text-red-400">{erro}</p>}
      {!invalido && carregando && !resultado && <p className="mt-6 text-sm text-slate-500">Calculando indicadores...</p>}

      {!invalido && resultado && situacao && (
        <div className="mt-6" aria-label="Resultado dos indicadores">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <Cartao
              rotulo="Disponibilidade estimada"
              valor={resultado.disponibilidadeEstimadaPercentual === null ? '—' : formatarPercentual(resultado.disponibilidadeEstimadaPercentual)}
              detalhe={`${resultado.totalAtivos} ativo(s) observado(s) por ${formatarDuracao(resultado.tempoObservadoAtivosSegundos)}`}
            />
            <Cartao rotulo="Tempo interrompido" valor={formatarDuracao(resultado.tempoInterrompidoSegundos)} detalhe={`${resultado.quantidadeSobrepostasAoPeriodo} interrupção(ões) no período`} />
            <Cartao
              rotulo="Tempo médio de restabelecimento"
              valor={resultado.tempoMedioRestabelecimentoSegundos === null ? '—' : formatarDuracao(resultado.tempoMedioRestabelecimentoSegundos)}
              detalhe={resultado.tempoMedioRestabelecimentoSegundos === null ? 'Sem restabelecimentos no período' : `${resultado.quantidadeRestabelecimentosNoPeriodo} restabelecimento(s)`}
            />
            <Cartao rotulo="Interrupções em aberto" valor={String(resultado.quantidadeSemTerminoFisico)} detalhe={`${resultado.quantidadeIniciadasNoPeriodo} iniciada(s) no período`} />
          </div>

          <p className={`mt-3 flex items-center gap-1.5 text-xs ${situacao.classe}`}>
            <situacao.icone className="h-3.5 w-3.5" />
            {situacao.texto}
            {resultado.quantidadeRegistrosInconsistentes > 0 && ` (${resultado.quantidadeRegistrosInconsistentes} registro(s))`}
          </p>

          {tiposPresentes.length > 0 && (
            <div className="mt-6 overflow-x-auto">
              <table className="w-full text-sm" aria-label="Inventário por estado">
                <thead>
                  <tr className="border-b border-slate-800 text-left text-slate-400">
                    <th className="pb-2 font-normal">Tipo</th>
                    {ESTADOS.map((e) => (
                      <th key={e} className="pb-2 font-normal">{ESTADO_STYLES[e].label}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {tiposPresentes.map((t) => (
                    <tr key={t.tipo} className="border-b border-slate-800/60 last:border-0">
                      <td className="py-2 pr-4 text-slate-300">{t.rotulo}</td>
                      {ESTADOS.map((e) => (
                        <td key={e} className="py-2 pr-4 text-slate-300">{quantidade(t.tipo, e)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="mt-3 text-xs text-slate-500">
            Período {formatarDataHora(resultado.dataInicio)} a {formatarDataHora(resultado.dataFim)} · calculado em {formatarDataHora(resultado.calculadoEm)}
          </p>
        </div>
      )}
    </section>
  )
}

export default IndicadoresOperacionais
