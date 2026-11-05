import { useEffect, useMemo, useState } from 'react'
import { AlertTriangle, AlertCircle, Info, CheckCheck, CheckCircle2, ChevronLeft, ChevronRight, Network } from 'lucide-react'
import { CampoSelect } from '../../components/formulario'
import { useAuth } from '../../hooks/useAuth'
import { listarFilaAlertas, reconhecerAlertaSobrecarga, reconhecerAlertaTensao } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import type {
  AlertaFila,
  Criticidade,
  EscopoAlerta,
  FiltrosFilaAlertas,
  PageResponse,
  TipoAlertaHistorico,
  TipoAtivoAlerta,
} from '../../types'
import { CRITICIDADE_STYLES, CRITICIDADES } from '../../utils/constants'
import { formatarDataHora, inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'
import AcoesTratativa from './AcoesTratativa'
import HistoricoTratativa from './HistoricoTratativa'
import TratativaForm from './TratativaForm'
import { opcoes, STATUS_LABEL, TIPO_ATIVO_LABEL, TIPO_LABEL } from './rotulos'
import { descreverEtapa, type AcaoTratativa } from './tratativa'

type Props = {
  ativos: AtivoCatalogo[]
  // Muda quando a página registra um alerta, para a fila buscar a posição definida pelo backend
  versao: number
  // Avisa a página depois de uma ação, para as seções por origem refletirem o novo estado
  aoAlterar?: () => void
}

type Valores = {
  criticidade: Criticidade | ''
  tipoAlerta: TipoAlertaHistorico | ''
  escopo: EscopoAlerta | ''
  tipoAtivo: TipoAtivoAlerta | ''
  subestacaoId: string
  de: string
  ate: string
}

const vazio: Valores = { criticidade: '', tipoAlerta: '', escopo: '', tipoAtivo: '', subestacaoId: '', de: '', ate: '' }

// Mesma cadência proposta para o monitoramento; a fila é consultada sem cache no backend
const INTERVALO_ATUALIZACAO_MS = 30_000

const icones: Record<Criticidade, typeof AlertTriangle> = {
  CRITICA: AlertTriangle,
  ALTA: AlertTriangle,
  MEDIA: AlertCircle,
  BAIXA: Info,
}

const semCriticidade = { dot: 'bg-slate-500', text: 'text-slate-400', bg: 'bg-slate-500/10', border: 'border-slate-800', label: 'Sem criticidade' }

// O período é por dia local: início inclusivo e fim exclusivo no dia seguinte ao último selecionado
function paraFiltros(valores: Valores): FiltrosFilaAlertas {
  return {
    criticidade: valores.criticidade || undefined,
    tipoAlerta: valores.tipoAlerta || undefined,
    escopo: valores.escopo || undefined,
    tipoAtivo: valores.tipoAtivo || undefined,
    subestacaoId: valores.subestacaoId ? Number(valores.subestacaoId) : undefined,
    dataInicio: valores.de ? inicioDoDiaLocal(valores.de) : undefined,
    dataFim: valores.ate ? inicioDoDiaSeguinteLocal(valores.ate) : undefined,
  }
}

function FilaAlertasAtivos({ ativos, versao, aoAlterar }: Props) {
  const { usuario } = useAuth()
  const [valores, setValores] = useState<Valores>(vazio)
  const [pagina, setPagina] = useState(0)
  const [resultado, setResultado] = useState<PageResponse<AlertaFila> | null>(null)
  const [contagens, setContagens] = useState<Partial<Record<Criticidade, number>>>({})
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [atualizadoEm, setAtualizadoEm] = useState<Date | null>(null)
  const [ciclo, setCiclo] = useState(0)
  const [emTratativa, setEmTratativa] = useState<{ alerta: AlertaFila; acao: AcaoTratativa } | null>(null)
  const [tratativasDe, setTratativasDe] = useState<AlertaFila | null>(null)
  const [reconhecendo, setReconhecendo] = useState<string | null>(null)
  const [erroAcao, setErroAcao] = useState<Record<string, string>>({})

  const periodoInvalido = Boolean(valores.de && valores.ate && valores.ate < valores.de)
  const filtros = useMemo(() => paraFiltros(valores), [valores])
  const chaveContagem = JSON.stringify({ ...filtros, criticidade: undefined })
  const subestacoes = ativos.filter((a) => a.tipo === 'Subestação')

  useEffect(() => {
    const id = setInterval(() => setCiclo((atual) => atual + 1), INTERVALO_ATUALIZACAO_MS)
    return () => clearInterval(id)
  }, [])

  // Atualizações periódicas mantêm a lista visível; só a primeira carga e as trocas de filtro mostram o carregamento
  useEffect(() => {
    if (periodoInvalido) return
    let ativo = true
    setCarregando(true)
    setErro('')

    listarFilaAlertas(pagina, filtros)
      .then((resposta) => {
        if (!ativo) return
        // Alertas que saíram da fila podem esvaziar a página atual
        if (resposta.content.length === 0 && pagina > 0) {
          setPagina(Math.max(resposta.totalPages - 1, 0))
          return
        }
        setResultado(resposta)
        setAtualizadoEm(new Date())
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [filtros, pagina, periodoInvalido, ciclo, versao])

  // A contagem por criticidade aplica os demais filtros e usa o total informado pelo backend
  useEffect(() => {
    if (periodoInvalido) return
    let ativo = true
    const demaisFiltros: FiltrosFilaAlertas = JSON.parse(chaveContagem)

    Promise.all(CRITICIDADES.map((criticidade) => listarFilaAlertas(0, { ...demaisFiltros, criticidade }, 1)))
      .then((paginas) => {
        if (!ativo) return
        setContagens(Object.fromEntries(CRITICIDADES.map((criticidade, i) => [criticidade, paginas[i].totalElements])))
      })
      .catch(() => ativo && setContagens({}))

    return () => {
      ativo = false
    }
  }, [chaveContagem, periodoInvalido, ciclo, versao])

  function alterar(campo: keyof Valores) {
    return (valor: string) => {
      setValores((atuais) => ({ ...atuais, [campo]: valor }))
      setPagina(0)
    }
  }

  const atualizar = () => setCiclo((atual) => atual + 1)

  function descreverAfetado(alerta: AlertaFila) {
    if (alerta.escopo === 'REDE_MONITORADA') return 'Rede monitorada'
    const ativo = ativos.find((a) => a.id === alerta.ativoId)
    if (ativo) return `${ativo.tipo} · ${ativo.nome}`
    return alerta.tipoAtivo ? `${TIPO_ATIVO_LABEL[alerta.tipoAtivo]} #${alerta.ativoId}` : `Ativo #${alerta.ativoId}`
  }

  const rotuloAlerta = (alerta: AlertaFila) => alerta.descricao ?? alerta.mensagem ?? `${TIPO_LABEL[alerta.tipoAlerta]} #${alerta.idOrigem}`
  const chave = (alerta: AlertaFila) => `${alerta.tipoAlerta}-${alerta.idOrigem}`

  // Sobrecarga e tensão reconhecidas saem da fila; o reconhecimento não exige comentário
  async function reconhecerAutomatico(alerta: AlertaFila) {
    const id = chave(alerta)
    setReconhecendo(id)
    setErroAcao((atuais) => ({ ...atuais, [id]: '' }))

    try {
      await (alerta.tipoAlerta === 'SOBRECARGA' ? reconhecerAlertaSobrecarga : reconhecerAlertaTensao)(alerta.idOrigem)
      atualizar()
      aoAlterar?.()
    } catch (error) {
      setErroAcao((atuais) => ({ ...atuais, [id]: extrairErroApi(error).mensagem }))
    } finally {
      setReconhecendo(null)
    }
  }

  // O reconhecido continua na fila com a autoria; o resolvido sai dela na nova consulta
  function handleTratativa() {
    setEmTratativa(null)
    atualizar()
    aoAlterar?.()
  }

  function acoes(alerta: AlertaFila) {
    if (alerta.tipoAlerta === 'GENERICO' && (alerta.statusOrigem === 'ABERTO' || alerta.statusOrigem === 'RECONHECIDO')) {
      return (
        <AcoesTratativa
          situacao={alerta.statusOrigem}
          rotulo={rotuloAlerta(alerta)}
          aoTratar={(acao) => setEmTratativa({ alerta, acao })}
          aoVerHistorico={() => setTratativasDe(alerta)}
        />
      )
    }

    if ((alerta.tipoAlerta === 'SOBRECARGA' || alerta.tipoAlerta === 'TENSAO') && alerta.statusOrigem === 'ATIVO') {
      const ocupado = reconhecendo === chave(alerta)
      return (
        <button
          type="button"
          onClick={() => reconhecerAutomatico(alerta)}
          disabled={ocupado}
          aria-label={`Reconhecer ${rotuloAlerta(alerta)}`}
          className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
        >
          <CheckCheck className="h-3.5 w-3.5" />
          {ocupado ? 'Reconhecendo...' : 'Reconhecer'}
        </button>
      )
    }

    if (alerta.tipoAlerta === 'COMUNICACAO') {
      return <span className="text-xs text-slate-500">Sai da fila quando a comunicação voltar</span>
    }

    return null
  }

  const alertas = resultado?.content ?? []
  const semFiltros = JSON.stringify(valores) === JSON.stringify(vazio)

  return (
    <section aria-label="Fila de alertas ativos">
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
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-7">
          <CampoSelect id="fila-criticidade" label="Criticidade" placeholder="Todas" opcoes={CRITICIDADES.map((c) => ({ valor: c, label: CRITICIDADE_STYLES[c].label }))} valor={valores.criticidade} aoAlterar={alterar('criticidade')} />
          <CampoSelect id="fila-tipo" label="Origem" placeholder="Todas" opcoes={opcoes(TIPO_LABEL)} valor={valores.tipoAlerta} aoAlterar={alterar('tipoAlerta')} />
          <CampoSelect id="fila-escopo" label="Escopo" placeholder="Todos" opcoes={[{ valor: 'ATIVO', label: 'Ativo específico' }, { valor: 'REDE_MONITORADA', label: 'Rede monitorada' }]} valor={valores.escopo} aoAlterar={alterar('escopo')} />
          <CampoSelect id="fila-tipo-ativo" label="Tipo de ativo" placeholder="Todos" opcoes={opcoes(TIPO_ATIVO_LABEL)} valor={valores.tipoAtivo} aoAlterar={alterar('tipoAtivo')} />
          <CampoSelect id="fila-subestacao" label="Subestação" placeholder="Todas" opcoes={subestacoes.map((s) => ({ valor: String(s.id), label: s.nome }))} valor={valores.subestacaoId} aoAlterar={alterar('subestacaoId')} />
          <div>
            <label htmlFor="fila-de" className="mb-2 block text-sm font-medium text-slate-300">De</label>
            <input id="fila-de" type="date" value={valores.de} onChange={(e) => alterar('de')(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
          </div>
          <div>
            <label htmlFor="fila-ate" className="mb-2 block text-sm font-medium text-slate-300">Até</label>
            <input id="fila-ate" type="date" value={valores.ate} onChange={(e) => alterar('ate')(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
          </div>
        </div>

        <div className="mt-3 flex flex-wrap items-center justify-between gap-2">
          {periodoInvalido ? (
            <p className="text-sm text-red-400">A data final deve ser igual ou posterior à inicial.</p>
          ) : (
            <p className="text-xs text-slate-500">
              {resultado ? `${resultado.totalElements} alerta(s) ativo(s)` : ''}
              {atualizadoEm && ` · atualizado às ${atualizadoEm.toLocaleTimeString('pt-BR')}, a cada 30 s`}
            </p>
          )}

          <div className="flex gap-3">
            <button type="button" onClick={atualizar} className="text-xs text-slate-400 underline-offset-2 hover:text-slate-200 hover:underline">
              Atualizar agora
            </button>
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
        </div>

        {!periodoInvalido && (
          <ul className="mt-4 space-y-3" aria-label="Alertas ativos">
            {alertas.map((alerta) => {
              const estilo = alerta.criticidade ? CRITICIDADE_STYLES[alerta.criticidade] : semCriticidade
              const Icon = alerta.criticidade ? icones[alerta.criticidade] : Info
              const erroItem = erroAcao[chave(alerta)]
              const reconhecimento = descreverEtapa('Reconhecido', alerta.reconhecidoEm, alerta.reconhecidoPorUsuarioId, usuario)

              return (
                <li key={chave(alerta)} className={`flex flex-wrap items-start justify-between gap-4 rounded-lg border p-4 transition hover:border-slate-700 ${estilo.border}`}>
                  <div className="flex items-start gap-3">
                    <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${estilo.bg}`}>
                      <Icon className={`h-4 w-4 ${estilo.text}`} />
                    </span>

                    <div>
                      <p className="text-sm text-slate-200">{rotuloAlerta(alerta)}</p>
                      <p className="mt-1 flex flex-wrap items-center gap-1 text-xs text-slate-500">
                        {alerta.escopo === 'REDE_MONITORADA' && <Network className="h-3.5 w-3.5" />}
                        {TIPO_LABEL[alerta.tipoAlerta]} • {descreverAfetado(alerta)} • {alerta.ocorridoEm ? formatarDataHora(alerta.ocorridoEm) : 'sem data'}
                      </p>
                      {reconhecimento && <p className="mt-1 text-xs text-slate-400">{reconhecimento}</p>}
                      {erroItem && <p className="mt-1 text-xs text-red-400">{erroItem}</p>}
                    </div>
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-1.5">
                    <span className={`flex items-center gap-1.5 text-xs font-medium ${estilo.text}`}>
                      <span className={`h-1.5 w-1.5 rounded-full ${estilo.dot}`} />
                      {estilo.label}
                    </span>
                    <span className={`text-xs ${alerta.statusOrigem === 'RECONHECIDO' ? 'text-yellow-400' : 'text-red-400'}`}>
                      {STATUS_LABEL[alerta.statusOrigem]}
                    </span>
                    {acoes(alerta)}
                  </div>
                </li>
              )
            })}
          </ul>
        )}

        {carregando && !resultado && !periodoInvalido && (
          <p className="py-10 text-center text-sm text-slate-500">Carregando alertas...</p>
        )}

        {!carregando && erro && !periodoInvalido && (
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

        {!carregando && !erro && !periodoInvalido && alertas.length === 0 && (
          semFiltros ? (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <CheckCircle2 className="h-8 w-8 text-green-500" />
              <p className="text-sm">Nenhum alerta ativo no momento.</p>
            </div>
          ) : (
            <p className="py-10 text-center text-sm text-slate-500">Nenhum alerta corresponde ao filtro.</p>
          )
        )}

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

      {emTratativa && (
        <TratativaForm
          id={emTratativa.alerta.idOrigem}
          rotulo={rotuloAlerta(emTratativa.alerta)}
          acao={emTratativa.acao}
          aoFechar={() => setEmTratativa(null)}
          aoSalvar={handleTratativa}
          aoDesatualizar={atualizar}
        />
      )}

      {tratativasDe && (
        <HistoricoTratativa id={tratativasDe.idOrigem} rotulo={rotuloAlerta(tratativasDe)} aoFechar={() => setTratativasDe(null)} />
      )}
    </section>
  )
}

export default FilaAlertasAtivos
