import { useEffect, useState, type FormEvent } from 'react'
import { CampoSelect } from '../../components/formulario'
import { consultarHistoricoConsumo, consultarHistoricoEnergia, listarAlimentadores } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import type { Alimentador, GranularidadeHistorico, HistoricoConsumoMedido, HistoricoEnergia as Historico } from '../../types'
import { formatarNumero, formatarPercentual } from '../../utils/formatacao'
import GraficoBarras from './GraficoBarras'

type Resultado<T> = { estado: 'pronto'; dados: T } | { estado: 'erro'; mensagem: string }

// O calendário do backend é UTC: os limites e rótulos seguem esse fuso
function periodoUtc(de: string, ate: string, granularidade: GranularidadeHistorico, agora = Date.now()) {
  const [anoDe, mesDe, diaDe = 1] = de.split('-').map(Number)
  const [anoAte, mesAte, diaAte = 1] = ate.split('-').map(Number)
  const inicio = Date.UTC(anoDe, mesDe - 1, granularidade === 'DIA' ? diaDe : 1)
  const fim = granularidade === 'DIA' ? Date.UTC(anoAte, mesAte - 1, diaAte + 1) : Date.UTC(anoAte, mesAte, 1)
  // O fim não pode ser futuro: o período em andamento termina agora
  return { dataInicio: new Date(inicio).toISOString(), dataFim: new Date(Math.min(fim, agora)).toISOString() }
}

function rotulo(inicio: string, granularidade: GranularidadeHistorico) {
  const [ano, mes, dia] = inicio.slice(0, 10).split('-')
  return granularidade === 'DIA' ? `${dia}/${mes}` : `${mes}/${ano}`
}

const SITUACAO = {
  OK: 'Completo',
  DADOS_INSUFICIENTES: 'Dados insuficientes',
  SEM_ENERGIA_INJETADA: 'Sem energia injetada',
  SEM_CONSUMO: 'Sem consumo',
}

function HistoricoEnergia() {
  const [alimentadores, setAlimentadores] = useState<Alimentador[]>([])
  const [alimentadorId, setAlimentadorId] = useState('')
  const [granularidade, setGranularidade] = useState<GranularidadeHistorico>('DIA')
  const [de, setDe] = useState('')
  const [ate, setAte] = useState('')
  const [energia, setEnergia] = useState<Resultado<Historico> | null>(null)
  const [consumo, setConsumo] = useState<Resultado<HistoricoConsumoMedido> | null>(null)
  const [erro, setErro] = useState('')
  const [consultando, setConsultando] = useState(false)

  useEffect(() => {
    listarAlimentadores()
      .then((lista) => setAlimentadores([...lista].sort((a, b) => a.nome.localeCompare(b.nome))))
      .catch((error) => setErro(extrairErroApi(error).mensagem))
  }, [])

  async function handleConsultar(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setEnergia(null)
    setConsumo(null)

    if (!alimentadorId || !de || !ate) {
      setErro('Selecione o alimentador e o período.')
      return
    }
    if (ate < de) {
      setErro('O fim do período deve ser igual ou posterior ao início.')
      return
    }

    setErro('')
    setConsultando(true)
    const { dataInicio, dataFim } = periodoUtc(de, ate, granularidade)
    const id = Number(alimentadorId)

    // Energia/perdas e consumo medido são contratos distintos: a falha de um não esconde o outro
    const [resEnergia, resConsumo] = await Promise.allSettled([
      consultarHistoricoEnergia(id, dataInicio, dataFim, granularidade),
      consultarHistoricoConsumo(id, dataInicio, dataFim, granularidade),
    ])
    setEnergia(resEnergia.status === 'fulfilled' ? { estado: 'pronto', dados: resEnergia.value } : { estado: 'erro', mensagem: extrairErroApi(resEnergia.reason).mensagem })
    setConsumo(resConsumo.status === 'fulfilled' ? { estado: 'pronto', dados: resConsumo.value } : { estado: 'erro', mensagem: extrairErroApi(resConsumo.reason).mensagem })
    setConsultando(false)
  }

  const tipoData = granularidade === 'DIA' ? 'date' : 'month'
  const consumoPorPeriodo = consumo?.estado === 'pronto' ? new Map(consumo.dados.pontos.map((p) => [p.inicioPeriodo, p])) : new Map()

  return (
    <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-historico">
      <h3 id="titulo-historico" className="text-lg font-semibold text-white">Histórico de energia, perdas e consumo</h3>
      <p className="mt-1 text-xs text-slate-500">
        Agregado por {granularidade === 'DIA' ? 'dia' : 'mês'} no calendário UTC, sem rateio entre períodos. O consumo medido vem dos medidores e não é substituído pela energia injetada.
      </p>

      <form onSubmit={handleConsultar} noValidate className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <CampoSelect
          id="historico-alimentador"
          label="Alimentador"
          placeholder={alimentadores.length ? 'Selecione' : 'Nenhum alimentador cadastrado'}
          opcoes={alimentadores.map((a) => ({ valor: String(a.id), label: a.nome }))}
          valor={alimentadorId}
          aoAlterar={setAlimentadorId}
        />
        <CampoSelect
          id="historico-granularidade"
          label="Agrupar por"
          opcoes={[{ valor: 'DIA', label: 'Dia' }, { valor: 'MES', label: 'Mês' }]}
          valor={granularidade}
          aoAlterar={(valor) => {
            setGranularidade(valor as GranularidadeHistorico)
            setDe('')
            setAte('')
          }}
        />
        <div>
          <label htmlFor="historico-de" className="mb-2 block text-sm font-medium text-slate-300">Início (UTC)</label>
          <input id="historico-de" type={tipoData} value={de} onChange={(e) => setDe(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
        <div>
          <label htmlFor="historico-ate" className="mb-2 block text-sm font-medium text-slate-300">Fim (UTC)</label>
          <input id="historico-ate" type={tipoData} value={ate} onChange={(e) => setAte(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
        <div className="flex items-end">
          <button type="submit" disabled={consultando} className="w-full rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:opacity-60">
            {consultando ? 'Consultando...' : 'Consultar histórico'}
          </button>
        </div>
      </form>

      {erro && <p className="mt-3 text-sm text-red-400">{erro}</p>}

      {(energia || consumo) && (
        <div className="mt-6 space-y-4" aria-label="Resultado do histórico">
          {energia?.estado === 'erro' && <p className="text-sm text-red-400">Energia e perdas: {energia.mensagem}</p>}
          {consumo?.estado === 'erro' && <p className="text-sm text-red-400">Consumo medido: {consumo.mensagem}</p>}

          {energia?.estado === 'pronto' && !energia.dados.possuiDados && consumo?.estado === 'pronto' && !consumo.dados.possuiDados && (
            <p className="py-6 text-center text-sm text-slate-500">Nenhuma medição de energia ou consumo no período.</p>
          )}

          <div className="grid gap-4 lg:grid-cols-3">
            {energia?.estado === 'pronto' && energia.dados.possuiDados && (
              <>
                <GraficoBarras titulo="Energia injetada" unidade="MWh" barras={energia.dados.pontos.map((p) => ({ rotulo: rotulo(p.inicioPeriodo, granularidade), valor: p.energiaInjetadaMwh }))} />
                <GraficoBarras titulo="Perda estimada" unidade="MWh" barras={energia.dados.pontos.map((p) => ({ rotulo: rotulo(p.inicioPeriodo, granularidade), valor: p.perdaEstimadaMwh }))} />
              </>
            )}
            {consumo?.estado === 'pronto' && consumo.dados.possuiDados && (
              <GraficoBarras titulo="Consumo medido" unidade="MWh" barras={consumo.dados.pontos.map((p) => ({ rotulo: rotulo(p.inicioPeriodo, granularidade), valor: p.consumoMedidoMwh }))} />
            )}
          </div>

          {consumo?.estado === 'pronto' && consumo.dados.possuiDados && (
            <p className="text-xs text-slate-500">
              {consumo.dados.quantidadeMedidoresConsiderados} medidor(es) considerado(s) · cobertura cadastral {consumo.dados.coberturaCadastral === 'NAO_AFERIDA' ? 'não aferida' : consumo.dados.coberturaCadastral}
            </p>
          )}

          {energia?.estado === 'pronto' && (energia.dados.possuiDados || (consumo?.estado === 'pronto' && consumo.dados.possuiDados)) && (
            <div className="overflow-x-auto">
              <table className="w-full text-sm" aria-label="Tabela do histórico">
                <thead>
                  <tr className="border-b border-slate-800 text-left text-slate-400">
                    <th className="pb-2 font-normal">Período (UTC)</th>
                    <th className="pb-2 font-normal">Energia injetada</th>
                    <th className="pb-2 font-normal">Perda estimada</th>
                    <th className="pb-2 font-normal">% ponderado</th>
                    <th className="pb-2 font-normal">Situação da energia</th>
                    <th className="pb-2 font-normal">Consumo medido</th>
                    <th className="pb-2 font-normal">Situação do consumo</th>
                  </tr>
                </thead>
                <tbody>
                  {energia.dados.pontos.map((p) => {
                    const c = consumoPorPeriodo.get(p.inicioPeriodo)
                    const mwh = (v: number | null | undefined) => (v === null || v === undefined ? '—' : `${formatarNumero(v)} MWh`)

                    return (
                      <tr key={p.inicioPeriodo} className="border-b border-slate-800/60 last:border-0">
                        <td className="py-2 pr-4 text-slate-300">{rotulo(p.inicioPeriodo, granularidade)}</td>
                        <td className="py-2 pr-4 text-slate-300">{mwh(p.energiaInjetadaMwh)}</td>
                        <td className="py-2 pr-4 text-slate-300">{mwh(p.perdaEstimadaMwh)}</td>
                        <td className="py-2 pr-4 text-slate-300">{p.percentualPerdaPonderado === null ? '—' : formatarPercentual(p.percentualPerdaPonderado)}</td>
                        <td className={`py-2 pr-4 text-xs ${p.situacao === 'DADOS_INSUFICIENTES' ? 'text-yellow-400' : 'text-slate-400'}`}>{SITUACAO[p.situacao]}</td>
                        <td className="py-2 pr-4 text-slate-300">{c ? mwh(c.consumoMedidoMwh) : '—'}</td>
                        <td className={`py-2 text-xs ${c?.situacao === 'DADOS_INSUFICIENTES' ? 'text-yellow-400' : 'text-slate-400'}`}>{c ? SITUACAO[c.situacao as keyof typeof SITUACAO] : '—'}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

export default HistoricoEnergia
