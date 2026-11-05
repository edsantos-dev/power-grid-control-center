import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { CampoSelect } from '../../components/formulario'
import Modal from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import { listarFalhas } from '../../services/eventos'
import type { EstadoOperacional, EventoOperacional, FalhaEquipamento, FiltrosFalhas, PageResponse, SituacaoEvento } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { formatarDataHora, inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'
import { SITUACAO_EVENTO_STYLES, SITUACOES_EVENTO } from './constantes'

type Props = {
  ativos: AtivoCatalogo[]
  // Muda quando a página registra ou conclui uma falha, para a consulta refletir o novo estado
  versao: number
  aoFechar: () => void
  aoVerTransicoes: (evento: EventoOperacional) => void
}

type Valores = { ativoId: string; situacao: SituacaoEvento | ''; de: string; ate: string }

function Estado({ estado }: { estado: EstadoOperacional | null }) {
  if (!estado) return <span className="text-slate-500">—</span>
  const estilo = ESTADO_STYLES[estado]
  return (
    <span className={`flex items-center gap-1.5 text-xs ${estilo.text}`}>
      <span className={`h-1.5 w-1.5 rounded-full ${estilo.dot}`} />
      {estilo.label}
    </span>
  )
}

function HistoricoFalhas({ ativos, versao, aoFechar, aoVerTransicoes }: Props) {
  const { usuario } = useAuth()
  const [valores, setValores] = useState<Valores>({ ativoId: '', situacao: '', de: '', ate: '' })
  const [pagina, setPagina] = useState(0)
  const [resultado, setResultado] = useState<PageResponse<FalhaEquipamento> | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')

  const periodoInvalido = Boolean(valores.de && valores.ate && valores.ate < valores.de)

  // O período filtra o instante de registro da falha
  const filtros = useMemo<FiltrosFalhas>(
    () => ({
      ativoId: valores.ativoId ? Number(valores.ativoId) : undefined,
      situacao: valores.situacao || undefined,
      dataInicio: valores.de ? inicioDoDiaLocal(valores.de) : undefined,
      dataFim: valores.ate ? inicioDoDiaSeguinteLocal(valores.ate) : undefined,
    }),
    [valores]
  )

  useEffect(() => {
    if (periodoInvalido) return
    let ativo = true
    setCarregando(true)
    setErro('')

    listarFalhas(pagina, filtros)
      .then((resposta) => ativo && setResultado(resposta))
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [filtros, pagina, periodoInvalido, versao])

  function alterar<K extends keyof Valores>(campo: K, valor: Valores[K]) {
    setValores((atuais) => ({ ...atuais, [campo]: valor }))
    setPagina(0)
  }

  const opcoesAtivo = [...ativos]
    .sort((a, b) => a.tipo.localeCompare(b.tipo) || a.nome.localeCompare(b.nome))
    .map((a) => ({ valor: String(a.id), label: `${a.tipo} · ${a.nome}` }))
  const nomeAtivo = (id: number) => {
    const ativo = ativos.find((a) => a.id === id)
    return ativo ? `${ativo.tipo} · ${ativo.nome}` : `Ativo #${id}`
  }
  const autor = (id: number) => (id === usuario?.id ? 'você' : `usuário #${id}`)
  const itens = periodoInvalido ? [] : (resultado?.content ?? [])

  return (
    <Modal titulo="Falhas de equipamento" aoFechar={aoFechar} largura="max-w-6xl">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <CampoSelect
          id="falhas-ativo"
          label="Ativo"
          className="lg:col-span-2"
          placeholder="Todos"
          opcoes={opcoesAtivo}
          valor={valores.ativoId}
          aoAlterar={(valor) => alterar('ativoId', valor)}
        />
        <CampoSelect
          id="falhas-situacao"
          label="Situação"
          placeholder="Todas"
          opcoes={SITUACOES_EVENTO.map((s) => ({ valor: s, label: SITUACAO_EVENTO_STYLES[s].label }))}
          valor={valores.situacao}
          aoAlterar={(valor) => alterar('situacao', valor as SituacaoEvento | '')}
        />
        <div>
          <label htmlFor="falhas-de" className="mb-2 block text-sm font-medium text-slate-300">Registrada de</label>
          <input id="falhas-de" type="date" value={valores.de} onChange={(e) => alterar('de', e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
        <div>
          <label htmlFor="falhas-ate" className="mb-2 block text-sm font-medium text-slate-300">até</label>
          <input id="falhas-ate" type="date" value={valores.ate} onChange={(e) => alterar('ate', e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
      </div>

      {periodoInvalido && <p className="mt-3 text-sm text-red-400">A data final deve ser igual ou posterior à inicial.</p>}

      <div className="mt-4 overflow-x-auto">
        {itens.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Registrada em</th>
                <th className="pb-3 font-normal">Ativo</th>
                <th className="pb-3 font-normal">Descrição</th>
                <th className="pb-3 font-normal">Situação</th>
                <th className="pb-3 font-normal">Estado anterior</th>
                <th className="pb-3 font-normal">Após o reparo</th>
                <th className="pb-3 font-normal">Estado atual</th>
                <th className="pb-3 font-normal"><span className="sr-only">Ações</span></th>
              </tr>
            </thead>
            <tbody>
              {itens.map((falha) => (
                <tr key={falha.id} className="border-b border-slate-800/60 align-top last:border-0">
                  <td className="py-3 pr-4 text-slate-300">
                    {formatarDataHora(falha.registradoEm)}
                    {falha.evento.terminoEfetivo && <p className="mt-1 text-xs text-slate-500">Concluída {formatarDataHora(falha.evento.terminoEfetivo)}</p>}
                  </td>
                  <td className="py-3 pr-4 text-slate-400">{nomeAtivo(falha.evento.ativoId)}</td>
                  <td className="py-3 pr-4">
                    <p className="text-slate-200">{falha.evento.descricao}</p>
                    {falha.evento.criticidade && <p className="mt-1 text-xs text-slate-500">Criticidade: {falha.evento.criticidade}</p>}
                    <p className="mt-1 text-xs text-slate-500">Responsável: {autor(falha.evento.responsavelUsuarioId)}</p>
                  </td>
                  <td className={`py-3 pr-4 text-xs font-medium ${SITUACAO_EVENTO_STYLES[falha.evento.situacao].text}`}>
                    {SITUACAO_EVENTO_STYLES[falha.evento.situacao].label}
                  </td>
                  <td className="py-3 pr-4"><Estado estado={falha.estadoAnterior} /></td>
                  <td className="py-3 pr-4"><Estado estado={falha.estadoAposReparo} /></td>
                  <td className="py-3 pr-4"><Estado estado={falha.estadoOperacionalAtual} /></td>
                  <td className="py-3">
                    <button
                      type="button"
                      onClick={() => aoVerTransicoes(falha.evento)}
                      aria-label={`Transições de ${falha.evento.descricao}`}
                      className="rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-300 transition hover:bg-slate-800"
                    >
                      Transições
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {carregando && !periodoInvalido && <p className="py-8 text-center text-sm text-slate-500">Carregando falhas...</p>}
        {!carregando && erro && !periodoInvalido && <p className="py-8 text-center text-sm text-red-400">{erro}</p>}
        {!carregando && !erro && !periodoInvalido && itens.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-500">Nenhuma falha registrada com estes filtros.</p>
        )}
      </div>

      {resultado && resultado.totalPages > 1 && itens.length > 0 && (
        <div className="mt-4 flex items-center justify-end gap-3 text-sm text-slate-400">
          <button type="button" disabled={resultado.first || carregando} onClick={() => setPagina((atual) => atual - 1)} aria-label="Página anterior" className="rounded-lg border border-slate-700 p-1.5 transition hover:bg-slate-800 disabled:opacity-40">
            <ChevronLeft className="h-4 w-4" />
          </button>
          <span>Página {resultado.page + 1} de {resultado.totalPages}</span>
          <button type="button" disabled={resultado.last || carregando} onClick={() => setPagina((atual) => atual + 1)} aria-label="Próxima página" className="rounded-lg border border-slate-700 p-1.5 transition hover:bg-slate-800 disabled:opacity-40">
            <ChevronRight className="h-4 w-4" />
          </button>
        </div>
      )}

      <p className="mt-4 text-xs text-slate-500">
        O atendimento segue o ciclo do evento: iniciar e concluir pela lista de eventos. Na conclusão, informe se o ativo volta a operar ou segue em manutenção.
      </p>
    </Modal>
  )
}

export default HistoricoFalhas
