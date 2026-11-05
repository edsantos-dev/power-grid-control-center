import { useEffect, useState } from 'react'
import { AlertTriangle, AlertCircle, Info, CheckCircle2, Search, Plus, Network } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { listarAlertasAtivos } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import { listarCatalogoAtivos, type AtivoCatalogo } from '../../services/ativos'
import type { Alerta, Criticidade } from '../../types'
import { CRITICIDADE_STYLES, CRITICIDADES, SITUACAO_ALERTA_LABEL } from '../../utils/constants'
import { formatarDataHora } from '../../utils/formatacao'
import AcoesTratativa from './AcoesTratativa'
import AlertaForm from './AlertaForm'
import AlertasSobrecarga from './AlertasSobrecarga'
import AlertasTensao from './AlertasTensao'
import AlertasComunicacao from './AlertasComunicacao'
import HistoricoAlertas from './HistoricoAlertas'
import HistoricoTratativa from './HistoricoTratativa'
import TratativaForm from './TratativaForm'
import { descreverEtapa, type AcaoTratativa } from './tratativa'

const icones: Record<Criticidade, typeof AlertTriangle> = {
  CRITICA: AlertTriangle,
  ALTA: AlertTriangle,
  MEDIA: AlertCircle,
  BAIXA: Info,
}

function Alertas() {
  const { usuario } = useAuth()
  const [alertas, setAlertas] = useState<Alerta[]>([])
  const [ativos, setAtivos] = useState<AtivoCatalogo[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroCriticidade, setFiltroCriticidade] = useState<Criticidade | 'todos'>('todos')
  const [busca, setBusca] = useState('')
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [aba, setAba] = useState<'ativos' | 'historico'>('ativos')
  const [emTratativa, setEmTratativa] = useState<{ alerta: Alerta; acao: AcaoTratativa } | null>(null)
  const [tratativasDe, setTratativasDe] = useState<Alerta | null>(null)

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      const [listaAlertas, catalogo] = await Promise.all([listarAlertasAtivos(), listarCatalogoAtivos()])
      setAlertas(listaAlertas)
      setAtivos(catalogo)
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [])

  function descreverAfetado(alerta: Alerta) {
    if (alerta.escopo === 'REDE_MONITORADA') {
      return 'Rede monitorada'
    }

    const ativo = ativos.find((a) => a.id === alerta.ativoId)
    return ativo ? `${ativo.tipo} · ${ativo.nome}` : `Ativo #${alerta.ativoId}`
  }

  // A lista mantém a ordem do backend; o filtro apenas remove itens
  const alertasFiltrados = alertas.filter((a) => {
    const termo = busca.trim().toLowerCase()
    const passaCriticidade = filtroCriticidade === 'todos' || a.criticidade === filtroCriticidade
    const passaBusca = a.tipo.toLowerCase().includes(termo) || descreverAfetado(a).toLowerCase().includes(termo)
    return passaCriticidade && passaBusca
  })

  const contagem = (criticidade: Criticidade) => alertas.filter((a) => a.criticidade === criticidade).length

  // Reconhecido continua ativo; resolvido sai da lista, como no backend
  function handleTratativa(atualizado: Alerta) {
    setEmTratativa(null)
    setAlertas((atuais) =>
      atualizado.situacao === 'RESOLVIDO'
        ? atuais.filter((a) => a.id !== atualizado.id)
        : atuais.map((a) => (a.id === atualizado.id ? atualizado : a))
    )
  }

  function handleSalvar() {
    setFormularioAberto(false)
    // Recarrega para exibir o novo alerta na posição definida pelo backend
    carregar()
  }

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Alertas
          </h2>

          <p className="mt-2 text-slate-400">
            Alertas ativos por criticidade, alertas automáticos e histórico de todas as origens.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setFormularioAberto(true)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Registrar alerta
        </button>
      </div>

      <div className="mb-6 flex gap-2 border-b border-slate-800" role="tablist" aria-label="Visão dos alertas">
        {(['ativos', 'historico'] as const).map((valor) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={aba === valor}
            onClick={() => setAba(valor)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition ${
              aba === valor ? 'border-blue-500 text-white' : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            {valor === 'ativos' ? 'Ativos' : 'Histórico'}
          </button>
        ))}
      </div>

      {aba === 'historico' ? (
        <HistoricoAlertas ativos={ativos} />
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
            {CRITICIDADES.map((criticidade) => {
              const estilo = CRITICIDADE_STYLES[criticidade]

              return (
                <div key={criticidade} className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
                  <p className="text-sm text-slate-400">{estilo.label}</p>
                  <p className={`mt-2 text-3xl font-bold ${estilo.text}`}>{contagem(criticidade)}</p>
                </div>
              )
            })}
          </div>

          <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="relative w-full sm:max-w-xs">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                <input
                  type="text"
                  value={busca}
                  onChange={(e) => setBusca(e.target.value)}
                  placeholder="Buscar alerta ou ativo..."
                  className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
                />
              </div>

              <div className="flex flex-wrap gap-2">
                {(['todos', ...CRITICIDADES] as const).map((criticidade) => (
                  <button
                    key={criticidade}
                    onClick={() => setFiltroCriticidade(criticidade)}
                    className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                      filtroCriticidade === criticidade
                        ? 'border-slate-600 bg-slate-800 text-white'
                        : 'border-slate-800 text-slate-400 hover:border-slate-700'
                    }`}
                  >
                    {criticidade === 'todos' ? 'Todas' : CRITICIDADE_STYLES[criticidade].label}
                  </button>
                ))}
              </div>
            </div>

            <ul className="mt-6 space-y-3" aria-label="Alertas ativos">
              {alertasFiltrados.map((alerta) => {
                const estilo = CRITICIDADE_STYLES[alerta.criticidade]
                const Icon = icones[alerta.criticidade]
                const reconhecimento =
                  alerta.situacao === 'RECONHECIDO'
                    ? descreverEtapa('Reconhecido', alerta.reconhecidoEm, alerta.reconhecidoPorUsuarioId, usuario)
                    : null

                return (
                  <li
                    key={alerta.id}
                    className={`flex items-start justify-between gap-4 rounded-lg border p-4 transition hover:border-slate-700 ${estilo.border}`}
                  >
                    <div className="flex items-start gap-3">
                      <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${estilo.bg}`}>
                        <Icon className={`h-4 w-4 ${estilo.text}`} />
                      </span>

                      <div>
                        <p className="text-sm text-slate-200">{alerta.tipo}</p>
                        <p className="mt-1 flex flex-wrap items-center gap-1 text-xs text-slate-500">
                          {alerta.escopo === 'REDE_MONITORADA' && <Network className="h-3.5 w-3.5" />}
                          {descreverAfetado(alerta)} • {formatarDataHora(alerta.geradoEm)}
                        </p>
                        {reconhecimento && <p className="mt-1 text-xs text-slate-400">{reconhecimento}</p>}
                      </div>
                    </div>

                    <div className="flex shrink-0 flex-col items-end gap-1.5">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${estilo.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${estilo.dot}`} />
                        {estilo.label}
                      </span>
                      <span className={`text-xs ${alerta.situacao === 'ABERTO' ? 'text-red-400' : 'text-yellow-400'}`}>
                        {SITUACAO_ALERTA_LABEL[alerta.situacao]}
                      </span>
                      <AcoesTratativa
                        situacao={alerta.situacao}
                        rotulo={alerta.tipo}
                        aoTratar={(acao) => setEmTratativa({ alerta, acao })}
                        aoVerHistorico={() => setTratativasDe(alerta)}
                      />
                    </div>
                  </li>
                )
              })}
            </ul>

            {carregando && (
              <p className="py-10 text-center text-sm text-slate-500">Carregando alertas...</p>
            )}

            {!carregando && erro && (
              <div className="flex flex-col items-center gap-3 py-10">
                <p className="text-sm text-red-400">{erro}</p>
                <button
                  type="button"
                  onClick={carregar}
                  className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
                >
                  Tentar novamente
                </button>
              </div>
            )}

            {!carregando && !erro && alertas.length === 0 && (
              <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
                <CheckCircle2 className="h-8 w-8 text-green-500" />
                <p className="text-sm">Nenhum alerta ativo no momento.</p>
              </div>
            )}

            {!carregando && !erro && alertas.length > 0 && alertasFiltrados.length === 0 && (
              <p className="py-10 text-center text-sm text-slate-500">Nenhum alerta corresponde ao filtro.</p>
            )}
          </div>

          <AlertasSobrecarga ativos={ativos} />
          <AlertasTensao ativos={ativos} />
          <AlertasComunicacao ativos={ativos} />
        </>
      )}

      {emTratativa && (
        <TratativaForm
          id={emTratativa.alerta.id}
          rotulo={emTratativa.alerta.tipo}
          acao={emTratativa.acao}
          aoFechar={() => setEmTratativa(null)}
          aoSalvar={handleTratativa}
          aoDesatualizar={carregar}
        />
      )}

      {tratativasDe && (
        <HistoricoTratativa id={tratativasDe.id} rotulo={tratativasDe.tipo} aoFechar={() => setTratativasDe(null)} />
      )}

      {formularioAberto && (
        <AlertaForm ativos={ativos} aoFechar={() => setFormularioAberto(false)} aoSalvar={handleSalvar} />
      )}
    </div>
  )
}

export default Alertas
