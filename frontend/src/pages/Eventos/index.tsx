import { useEffect, useState } from 'react'
import { Search, Calendar, User, Plus, ZapOff, History } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import { listarCatalogoAtivos, type AtivoCatalogo } from '../../services/ativos'
import { listarEventos } from '../../services/eventos'
import type { CategoriaEvento, EventoOperacional, SituacaoEvento } from '../../types'
import { formatarDataHora } from '../../utils/formatacao'
import { CATEGORIA_LABEL, SITUACAO_EVENTO_STYLES, SITUACOES_EVENTO } from './constantes'
import EventoForm from './EventoForm'
import HistoricoTransicoes from './HistoricoTransicoes'
import InterrupcaoForm from './InterrupcaoForm'
import TransicaoForm from './TransicaoForm'
import { podeTransicionar, proximaTransicao, type Transicao } from './transicoes'

// O instante de referência é o efetivo quando existe; senão, o previsto
function instanteDoEvento(evento: EventoOperacional) {
  return evento.inicioEfetivo ?? evento.inicioPrevisto
}

function Eventos() {
  const { usuario } = useAuth()
  const [eventos, setEventos] = useState<EventoOperacional[]>([])
  const [ativos, setAtivos] = useState<AtivoCatalogo[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroCategoria, setFiltroCategoria] = useState<CategoriaEvento | 'todos'>('todos')
  const [filtroSituacao, setFiltroSituacao] = useState<SituacaoEvento | 'todos'>('todos')
  const [busca, setBusca] = useState('')
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [interrupcaoAberta, setInterrupcaoAberta] = useState(false)
  const [emTransicao, setEmTransicao] = useState<{ evento: EventoOperacional; transicao: Transicao } | null>(null)
  const [historico, setHistorico] = useState<EventoOperacional | null>(null)

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      const [lista, catalogo] = await Promise.all([listarEventos(), listarCatalogoAtivos()])
      setEventos(lista)
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

  function nomeAtivo(id: number) {
    const ativo = ativos.find((a) => a.id === id)
    return ativo ? `${ativo.tipo} · ${ativo.nome}` : `Ativo #${id}`
  }

  function nomeUsuario(id: number) {
    return id === usuario?.id ? 'Você' : `Usuário #${id}`
  }

  function handleSalvar(salvo: EventoOperacional) {
    setEventos((atuais) => [...atuais.filter((e) => e.id !== salvo.id), salvo])
    setFormularioAberto(false)
    setInterrupcaoAberta(false)
    setEmTransicao(null)
  }

  // A API não garante ordenação: os mais recentes ficam primeiro
  const eventosFiltrados = eventos
    .filter((e) => {
      const termo = busca.trim().toLowerCase()
      const passaCategoria = filtroCategoria === 'todos' || e.categoria === filtroCategoria
      const passaSituacao = filtroSituacao === 'todos' || e.situacao === filtroSituacao
      const passaBusca = e.descricao.toLowerCase().includes(termo) || nomeAtivo(e.ativoId).toLowerCase().includes(termo)
      return passaCategoria && passaSituacao && passaBusca
    })
    .sort((a, b) => (instanteDoEvento(b) ?? '').localeCompare(instanteDoEvento(a) ?? '') || b.id - a.id)

  const categoriasPresentes = (Object.keys(CATEGORIA_LABEL) as CategoriaEvento[]).filter((c) =>
    eventos.some((e) => e.categoria === c)
  )
  const contagem = (situacao: SituacaoEvento) => eventos.filter((e) => e.situacao === situacao).length

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Eventos
          </h2>

          <p className="mt-2 text-slate-400">
            Registro e rastreabilidade das ações realizadas na rede.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setInterrupcaoAberta(true)}
            className="flex items-center gap-2 rounded-lg border border-red-500/50 px-4 py-2 text-sm font-semibold text-red-300 transition hover:bg-red-500/10"
          >
            <ZapOff className="h-4 w-4" />
            Registrar interrupção
          </button>

          <button
            type="button"
            onClick={() => setFormularioAberto(true)}
            className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
          >
            <Plus className="h-4 w-4" />
            Registrar evento programado
          </button>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Eventos</p>
          <p className="mt-2 text-3xl font-bold text-white">{eventos.length}</p>
        </div>

        {SITUACOES_EVENTO.map((situacao) => (
          <div key={situacao} className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
            <p className="text-sm text-slate-400">{SITUACAO_EVENTO_STYLES[situacao].label}</p>
            <p className={`mt-2 text-3xl font-bold ${SITUACAO_EVENTO_STYLES[situacao].text}`}>{contagem(situacao)}</p>
          </div>
        ))}
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative w-full lg:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Buscar descrição ou ativo..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <select
              aria-label="Filtrar por categoria"
              value={filtroCategoria}
              onChange={(e) => setFiltroCategoria(e.target.value as CategoriaEvento | 'todos')}
              className="rounded-lg border border-slate-800 bg-slate-950 px-3 py-1.5 text-xs text-slate-300"
            >
              <option value="todos">Todas as categorias</option>
              {categoriasPresentes.map((c) => (
                <option key={c} value={c}>{CATEGORIA_LABEL[c]}</option>
              ))}
            </select>

            {(['todos', ...SITUACOES_EVENTO] as const).map((situacao) => (
              <button
                key={situacao}
                onClick={() => setFiltroSituacao(situacao)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroSituacao === situacao
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {situacao === 'todos' ? 'Todas' : SITUACAO_EVENTO_STYLES[situacao].label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Data e hora</th>
                <th className="pb-3 font-normal">Ativo</th>
                <th className="pb-3 font-normal">Categoria</th>
                <th className="pb-3 font-normal">Descrição</th>
                <th className="pb-3 font-normal">Responsável</th>
                <th className="pb-3 font-normal">Situação</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {eventosFiltrados.map((e) => {
                const situacao = SITUACAO_EVENTO_STYLES[e.situacao]
                const instante = instanteDoEvento(e)
                const transicao = proximaTransicao(e)

                return (
                  <tr key={e.id} className="border-b border-slate-800/60 align-top last:border-0">
                    <td className="py-3 pr-4 text-slate-300">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="h-3.5 w-3.5 text-slate-500" />
                        {instante ? formatarDataHora(instante) : '—'}
                      </span>
                      <p className="mt-1 text-xs text-slate-500">{e.inicioEfetivo ? 'Início efetivo' : 'Início previsto'}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">{nomeAtivo(e.ativoId)}</td>
                    <td className="py-3 pr-4 text-slate-300">{CATEGORIA_LABEL[e.categoria]}</td>
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{e.descricao}</p>
                      {e.criticidade && <p className="mt-1 text-xs text-slate-500">Criticidade: {e.criticidade}</p>}
                      {e.terminoPrevisto && (
                        <p className="mt-1 text-xs text-slate-500">Término previsto {formatarDataHora(e.terminoPrevisto)}</p>
                      )}
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <User className="h-3.5 w-3.5 text-slate-500" />
                        {nomeUsuario(e.responsavelUsuarioId)}
                      </span>
                      <p className="mt-1 text-xs text-slate-500">Registrado por {nomeUsuario(e.registradorUsuarioId).toLowerCase()}</p>
                    </td>
                    <td className={`py-3 pr-4 text-xs font-medium ${situacao.text}`}>
                      {situacao.label}
                      {e.terminoEfetivo && (
                        <p className="mt-1 font-normal text-slate-500">Término {formatarDataHora(e.terminoEfetivo)}</p>
                      )}
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-1.5">
                        {transicao && podeTransicionar(e, usuario) && (
                          <button
                            type="button"
                            onClick={() => setEmTransicao({ evento: e, transicao })}
                            aria-label={`${transicao.rotulo} ${e.descricao}`}
                            className="rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white"
                          >
                            {transicao.rotulo}
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setHistorico(e)}
                          aria-label={`Histórico de ${e.descricao}`}
                          className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white"
                        >
                          <History className="h-4 w-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {carregando && (
            <p className="py-10 text-center text-sm text-slate-500">Carregando eventos...</p>
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

          {!carregando && !erro && eventosFiltrados.length === 0 && (
            <p className="py-10 text-center text-sm text-slate-500">Nenhum evento encontrado.</p>
          )}
        </div>
      </div>

      {emTransicao && (
        <TransicaoForm
          evento={emTransicao.evento}
          transicao={emTransicao.transicao}
          aoFechar={() => setEmTransicao(null)}
          aoSalvar={handleSalvar}
          aoDesatualizar={carregar}
        />
      )}

      {historico && <HistoricoTransicoes evento={historico} aoFechar={() => setHistorico(null)} />}

      {interrupcaoAberta && (
        <InterrupcaoForm ativos={ativos} aoFechar={() => setInterrupcaoAberta(false)} aoSalvar={handleSalvar} />
      )}

      {formularioAberto && (
        <EventoForm
          ativos={ativos}
          categorias={['MANOBRA', 'DESLIGAMENTO_PROGRAMADO']}
          aoFechar={() => setFormularioAberto(false)}
          aoSalvar={handleSalvar}
        />
      )}
    </div>
  )
}

export default Eventos
