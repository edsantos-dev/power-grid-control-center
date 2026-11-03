import { useEffect, useMemo, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { CampoSelect } from '../../components/formulario'
import Modal from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import { listarManutencoesDoAtivo } from '../../services/eventos'
import type { EventoOperacional, FiltrosManutencao, ManutencaoHistorico, PageResponse, SituacaoEvento } from '../../types'
import { formatarDataHora, inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'
import { SITUACAO_EVENTO_STYLES, SITUACOES_EVENTO } from './constantes'

type Props = {
  ativos: AtivoCatalogo[]
  aoFechar: () => void
  aoVerTransicoes: (evento: EventoOperacional) => void
}

type Valores = { ativoId: string; situacao: SituacaoEvento | ''; minhas: boolean; de: string; ate: string }

function HistoricoManutencao({ ativos, aoFechar, aoVerTransicoes }: Props) {
  const { usuario } = useAuth()
  const [valores, setValores] = useState<Valores>({ ativoId: '', situacao: '', minhas: false, de: '', ate: '' })
  const [pagina, setPagina] = useState(0)
  const [resultado, setResultado] = useState<PageResponse<ManutencaoHistorico> | null>(null)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState('')

  const periodoInvalido = Boolean(valores.de && valores.ate && valores.ate < valores.de)

  // O período filtra o início efetivo: manutenções ainda não iniciadas ficam de fora
  const filtros = useMemo<FiltrosManutencao>(
    () => ({
      situacao: valores.situacao || undefined,
      responsavelUsuarioId: valores.minhas ? usuario?.id : undefined,
      dataInicio: valores.de ? inicioDoDiaLocal(valores.de) : undefined,
      dataFim: valores.ate ? inicioDoDiaSeguinteLocal(valores.ate) : undefined,
    }),
    [valores.situacao, valores.minhas, valores.de, valores.ate, usuario?.id]
  )

  useEffect(() => {
    if (!valores.ativoId || periodoInvalido) return
    let ativo = true
    setCarregando(true)
    setErro('')

    listarManutencoesDoAtivo(Number(valores.ativoId), pagina, filtros)
      .then((resposta) => ativo && setResultado(resposta))
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [valores.ativoId, filtros, pagina, periodoInvalido])

  function alterar<K extends keyof Valores>(campo: K, valor: Valores[K]) {
    setValores((atuais) => ({ ...atuais, [campo]: valor }))
    setPagina(0)
  }

  const opcoesAtivo = [...ativos]
    .sort((a, b) => a.tipo.localeCompare(b.tipo) || a.nome.localeCompare(b.nome))
    .map((a) => ({ valor: String(a.id), label: `${a.tipo} · ${a.nome}` }))
  const autor = (id: number) => (id === usuario?.id ? 'você' : `usuário #${id}`)
  const itens = valores.ativoId && !periodoInvalido ? (resultado?.content ?? []) : []

  return (
    <Modal titulo="Histórico de manutenção por ativo" aoFechar={aoFechar} largura="max-w-5xl">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <CampoSelect
          id="manutencao-ativo"
          label="Ativo"
          className="lg:col-span-2"
          placeholder="Selecione o ativo"
          opcoes={opcoesAtivo}
          valor={valores.ativoId}
          aoAlterar={(valor) => alterar('ativoId', valor)}
        />
        <CampoSelect
          id="manutencao-situacao"
          label="Situação"
          placeholder="Todas"
          opcoes={SITUACOES_EVENTO.map((s) => ({ valor: s, label: SITUACAO_EVENTO_STYLES[s].label }))}
          valor={valores.situacao}
          aoAlterar={(valor) => alterar('situacao', valor as SituacaoEvento | '')}
        />
        <div>
          <label htmlFor="manutencao-de" className="mb-2 block text-sm font-medium text-slate-300">Início efetivo de</label>
          <input id="manutencao-de" type="date" value={valores.de} onChange={(e) => alterar('de', e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
        <div>
          <label htmlFor="manutencao-ate" className="mb-2 block text-sm font-medium text-slate-300">até</label>
          <input id="manutencao-ate" type="date" value={valores.ate} onChange={(e) => alterar('ate', e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
      </div>

      <label className="mt-3 flex items-center gap-2 text-sm text-slate-300">
        <input type="checkbox" checked={valores.minhas} onChange={(e) => alterar('minhas', e.target.checked)} />
        Somente as que estão sob minha responsabilidade
      </label>

      {periodoInvalido && <p className="mt-3 text-sm text-red-400">A data final deve ser igual ou posterior à inicial.</p>}

      <div className="mt-4 overflow-x-auto">
        {!valores.ativoId && <p className="py-8 text-center text-sm text-slate-500">Selecione um ativo para consultar as manutenções.</p>}

        {itens.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Início efetivo</th>
                <th className="pb-3 font-normal">Descrição</th>
                <th className="pb-3 font-normal">Situação</th>
                <th className="pb-3 font-normal">Responsável</th>
                <th className="pb-3 font-normal">Última transição</th>
                <th className="pb-3 font-normal"><span className="sr-only">Ações</span></th>
              </tr>
            </thead>
            <tbody>
              {itens.map(({ evento, ultimaTransicao }) => (
                <tr key={evento.id} className="border-b border-slate-800/60 align-top last:border-0">
                  <td className="py-3 pr-4 text-slate-300">
                    {evento.inicioEfetivo ? formatarDataHora(evento.inicioEfetivo) : <span className="text-slate-500">Não iniciada</span>}
                    {evento.terminoEfetivo && <p className="mt-1 text-xs text-slate-500">Término {formatarDataHora(evento.terminoEfetivo)}</p>}
                  </td>
                  <td className="py-3 pr-4">
                    <p className="text-slate-200">{evento.descricao}</p>
                    {evento.criticidade && <p className="mt-1 text-xs text-slate-500">Criticidade: {evento.criticidade}</p>}
                    {evento.inicioPrevisto && <p className="mt-1 text-xs text-slate-500">Previsto {formatarDataHora(evento.inicioPrevisto)}</p>}
                  </td>
                  <td className={`py-3 pr-4 text-xs font-medium ${SITUACAO_EVENTO_STYLES[evento.situacao].text}`}>
                    {SITUACAO_EVENTO_STYLES[evento.situacao].label}
                  </td>
                  <td className="py-3 pr-4 text-slate-400">
                    {autor(evento.responsavelUsuarioId)}
                    <p className="mt-1 text-xs text-slate-500">Registrada por {autor(evento.registradorUsuarioId)}</p>
                  </td>
                  <td className="py-3 pr-4 text-slate-400">
                    {ultimaTransicao ? (
                      <>
                        <p className="text-xs">
                          {SITUACAO_EVENTO_STYLES[ultimaTransicao.situacaoAnterior].label} → {SITUACAO_EVENTO_STYLES[ultimaTransicao.situacaoNova].label}
                        </p>
                        <p className="mt-1 text-xs text-slate-500">{formatarDataHora(ultimaTransicao.alteradoEm)} por {autor(ultimaTransicao.usuarioId)}</p>
                        <p className="mt-1 line-clamp-2 text-xs text-slate-300">{ultimaTransicao.comentario}</p>
                      </>
                    ) : (
                      <span className="text-xs text-slate-500">Sem transições</span>
                    )}
                  </td>
                  <td className="py-3">
                    <button
                      type="button"
                      onClick={() => aoVerTransicoes(evento)}
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

        {valores.ativoId && carregando && <p className="py-8 text-center text-sm text-slate-500">Carregando manutenções...</p>}
        {valores.ativoId && !carregando && erro && <p className="py-8 text-center text-sm text-red-400">{erro}</p>}
        {valores.ativoId && !carregando && !erro && !periodoInvalido && itens.length === 0 && (
          <p className="py-8 text-center text-sm text-slate-500">Nenhuma manutenção registrada para este ativo com estes filtros.</p>
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
        Mostra apenas eventos de manutenção do ativo, dos mais recentes aos ainda não iniciados. O executor é o responsável registrado; integrantes de equipe não são inferidos.
      </p>
    </Modal>
  )
}

export default HistoricoManutencao
