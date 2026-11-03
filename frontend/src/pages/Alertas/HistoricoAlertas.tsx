import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { CampoSelect } from '../../components/formulario'
import { listarHistoricoAlertas } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import type {
  AlertaHistorico,
  Criticidade,
  EscopoAlerta,
  FiltrosHistoricoAlertas,
  PageResponse,
  StatusOrigemAlerta,
  TipoAlertaHistorico,
} from '../../types'
import { CRITICIDADE_STYLES, CRITICIDADES } from '../../utils/constants'
import { formatarDataHora, inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'

type Props = {
  ativos: AtivoCatalogo[]
}

type Valores = {
  criticidade: Criticidade | ''
  tipoAlerta: TipoAlertaHistorico | ''
  escopo: EscopoAlerta | ''
  statusOrigem: StatusOrigemAlerta | ''
  de: string
  ate: string
}

const vazio: Valores = { criticidade: '', tipoAlerta: '', escopo: '', statusOrigem: '', de: '', ate: '' }

const TIPO_LABEL: Record<TipoAlertaHistorico, string> = {
  GENERICO: 'Registrado',
  SOBRECARGA: 'Sobrecarga',
  TENSAO: 'Tensão',
  COMUNICACAO: 'Comunicação',
}

const STATUS_LABEL: Record<StatusOrigemAlerta, string> = {
  ABERTO: 'Aberto',
  ATIVO: 'Ativo',
  RECONHECIDO: 'Reconhecido',
  RESOLVIDO: 'Resolvido',
  RESTABELECIDO: 'Restabelecido',
}

const opcoes = <K extends string>(rotulos: Record<K, string>) =>
  (Object.keys(rotulos) as K[]).map((valor) => ({ valor, label: rotulos[valor] }))

// O período é por dia local: início inclusivo e fim exclusivo no dia seguinte ao último selecionado
function paraFiltros(valores: Valores): FiltrosHistoricoAlertas {
  return {
    criticidade: valores.criticidade || undefined,
    tipoAlerta: valores.tipoAlerta || undefined,
    escopo: valores.escopo || undefined,
    statusOrigem: valores.statusOrigem || undefined,
    dataInicio: valores.de ? inicioDoDiaLocal(valores.de) : undefined,
    dataFim: valores.ate ? inicioDoDiaSeguinteLocal(valores.ate) : undefined,
  }
}

function HistoricoAlertas({ ativos }: Props) {
  const [valores, setValores] = useState<Valores>(vazio)
  const [pagina, setPagina] = useState(0)
  const [resultado, setResultado] = useState<PageResponse<AlertaHistorico> | null>(null)
  const [contagens, setContagens] = useState<Partial<Record<Criticidade, number>>>({})
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [tentativa, setTentativa] = useState(0)

  const periodoInvalido = Boolean(valores.de && valores.ate && valores.ate < valores.de)
  const filtros = useMemo(() => paraFiltros(valores), [valores])
  // A contagem ignora a criticidade selecionada para mostrar o total de cada uma
  const chaveContagem = JSON.stringify({ ...filtros, criticidade: undefined })

  useEffect(() => {
    if (periodoInvalido) return
    let ativo = true
    setCarregando(true)
    setErro('')

    listarHistoricoAlertas(pagina, filtros)
      .then((resposta) => ativo && setResultado(resposta))
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [filtros, pagina, periodoInvalido, tentativa])

  // A contagem por criticidade aplica os demais filtros e usa o total informado pelo backend
  useEffect(() => {
    if (periodoInvalido) return
    let ativo = true

    const demaisFiltros: FiltrosHistoricoAlertas = JSON.parse(chaveContagem)

    Promise.all(CRITICIDADES.map((criticidade) => listarHistoricoAlertas(0, { ...demaisFiltros, criticidade }, 1)))
      .then((paginas) => {
        if (!ativo) return
        setContagens(Object.fromEntries(CRITICIDADES.map((criticidade, i) => [criticidade, paginas[i].totalElements])))
      })
      .catch(() => ativo && setContagens({}))

    return () => {
      ativo = false
    }
  }, [chaveContagem, periodoInvalido, tentativa])

  function alterar(campo: keyof Valores) {
    return (valor: string) => {
      setValores((atuais) => ({ ...atuais, [campo]: valor }))
      setPagina(0)
    }
  }

  function descreverAfetado(alerta: AlertaHistorico) {
    if (alerta.escopo === 'REDE_MONITORADA') return 'Rede monitorada'
    const ativo = ativos.find((a) => a.id === alerta.ativoId)
    return ativo ? `${ativo.tipo} · ${ativo.nome}` : `Ativo #${alerta.ativoId}`
  }

  function detalheSituacao(alerta: AlertaHistorico) {
    if (alerta.restabelecidoEm) return `em ${formatarDataHora(alerta.restabelecidoEm)}`
    if (alerta.reconhecidoEm) return `em ${formatarDataHora(alerta.reconhecidoEm)}`
    return ''
  }

  const alertas = resultado?.content ?? []

  return (
    <section aria-label="Histórico de alertas">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {CRITICIDADES.map((criticidade) => {
          const estilo = CRITICIDADE_STYLES[criticidade]
          const selecionada = valores.criticidade === criticidade

          return (
            <button
              key={criticidade}
              type="button"
              aria-pressed={selecionada}
              onClick={() => alterar('criticidade')(selecionada ? '' : criticidade)}
              className={`rounded-xl border bg-slate-900 p-5 text-left transition hover:border-slate-600 ${selecionada ? 'border-blue-500' : 'border-slate-800'}`}
            >
              <p className="text-sm text-slate-400">{estilo.label}</p>
              <p className={`mt-2 text-2xl font-bold ${estilo.text}`}>{contagens[criticidade] ?? '—'}</p>
            </button>
          )
        })}
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-6">
          <CampoSelect id="hist-criticidade" label="Criticidade" placeholder="Todas" opcoes={CRITICIDADES.map((c) => ({ valor: c, label: CRITICIDADE_STYLES[c].label }))} valor={valores.criticidade} aoAlterar={alterar('criticidade')} />
          <CampoSelect id="hist-tipo" label="Origem" placeholder="Todas" opcoes={opcoes(TIPO_LABEL)} valor={valores.tipoAlerta} aoAlterar={alterar('tipoAlerta')} />
          <CampoSelect id="hist-escopo" label="Escopo" placeholder="Todos" opcoes={[{ valor: 'ATIVO', label: 'Ativo específico' }, { valor: 'REDE_MONITORADA', label: 'Rede monitorada' }]} valor={valores.escopo} aoAlterar={alterar('escopo')} />
          <CampoSelect id="hist-status" label="Situação na origem" placeholder="Todas" opcoes={opcoes(STATUS_LABEL)} valor={valores.statusOrigem} aoAlterar={alterar('statusOrigem')} />
          <div>
            <label htmlFor="hist-de" className="mb-2 block text-sm font-medium text-slate-300">De</label>
            <input id="hist-de" type="date" value={valores.de} onChange={(e) => alterar('de')(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
          </div>
          <div>
            <label htmlFor="hist-ate" className="mb-2 block text-sm font-medium text-slate-300">Até</label>
            <input id="hist-ate" type="date" value={valores.ate} onChange={(e) => alterar('ate')(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
          </div>
        </div>

        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          {periodoInvalido ? (
            <p className="text-sm text-red-400">A data final deve ser igual ou posterior à inicial.</p>
          ) : (
            <p className="text-xs text-slate-500">
              {resultado ? `${resultado.totalElements} alerta(s) no histórico` : ''}
            </p>
          )}

          <button
            type="button"
            onClick={() => {
              setValores(vazio)
              setPagina(0)
            }}
            className="text-xs text-slate-400 underline-offset-2 hover:text-slate-200 hover:underline"
          >
            Limpar filtros
          </button>
        </div>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Ocorrido em</th>
                <th className="pb-3 font-normal">Origem</th>
                <th className="pb-3 font-normal">Criticidade</th>
                <th className="pb-3 font-normal">Afetado</th>
                <th className="pb-3 font-normal">Descrição</th>
                <th className="pb-3 font-normal">Situação</th>
              </tr>
            </thead>
            <tbody>
              {!periodoInvalido &&
                alertas.map((alerta) => {
                  const estilo = CRITICIDADE_STYLES[alerta.criticidade]

                  return (
                    <tr key={`${alerta.tipoAlerta}-${alerta.idOrigem}`} className="border-b border-slate-800/60 last:border-0">
                      <td className="py-3 pr-4 text-slate-300">
                        {alerta.ocorridoEm ? formatarDataHora(alerta.ocorridoEm) : <span className="text-slate-500">Sem data</span>}
                      </td>
                      <td className="py-3 pr-4 text-slate-300">{TIPO_LABEL[alerta.tipoAlerta]}</td>
                      <td className="py-3 pr-4">
                        <span className={`flex items-center gap-1.5 text-xs font-medium ${estilo.text}`}>
                          <span className={`h-1.5 w-1.5 rounded-full ${estilo.dot}`} />
                          {estilo.label}
                        </span>
                      </td>
                      <td className="py-3 pr-4 text-slate-400">{descreverAfetado(alerta)}</td>
                      <td className="py-3 pr-4 text-slate-300">{alerta.descricao ?? <span className="text-slate-500">—</span>}</td>
                      <td className="py-3 text-slate-300">
                        <p>{STATUS_LABEL[alerta.statusOrigem]}</p>
                        {detalheSituacao(alerta) && <p className="text-xs text-slate-500">{detalheSituacao(alerta)}</p>}
                      </td>
                    </tr>
                  )
                })}
            </tbody>
          </table>

          {carregando && !periodoInvalido && (
            <p className="py-10 text-center text-sm text-slate-500">Carregando histórico...</p>
          )}

          {!carregando && erro && !periodoInvalido && (
            <div className="flex flex-col items-center gap-3 py-10">
              <p className="text-sm text-red-400">{erro}</p>
              <button
                type="button"
                onClick={() => setTentativa((atual) => atual + 1)}
                className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
              >
                Tentar novamente
              </button>
            </div>
          )}

          {!carregando && !erro && !periodoInvalido && alertas.length === 0 && (
            <p className="py-10 text-center text-sm text-slate-500">Nenhum alerta no histórico para estes filtros.</p>
          )}
        </div>

        {resultado && resultado.totalPages > 1 && !periodoInvalido && (
          <div className="mt-4 flex items-center justify-end gap-3 text-sm text-slate-400">
            <button
              type="button"
              disabled={resultado.first || carregando}
              onClick={() => setPagina((atual) => atual - 1)}
              aria-label="Página anterior"
              className="rounded-lg border border-slate-700 p-1.5 transition hover:bg-slate-800 disabled:opacity-40"
            >
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span>
              Página {resultado.page + 1} de {resultado.totalPages}
            </span>
            <button
              type="button"
              disabled={resultado.last || carregando}
              onClick={() => setPagina((atual) => atual + 1)}
              aria-label="Próxima página"
              className="rounded-lg border border-slate-700 p-1.5 transition hover:bg-slate-800 disabled:opacity-40"
            >
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>
    </section>
  )
}

export default HistoricoAlertas
