import api from './api'
import type {
  DesignacaoEquipe,
  FalhaEquipamento,
  FalhaEquipamentoRequest,
  FiltrosFalhas,
  DesignacaoEquipeRequest,
  EventoOperacional,
  EventoOperacionalRequest,
  InterrupcaoRequest,
  FiltrosManutencao,
  ManutencaoHistorico,
  PageResponse,
  TransicaoEvento,
  TransicaoEventoRequest,
} from '../types'

const ROTA = '/api/eventos-operacionais'

// A listagem inclui todas as categorias, sem paginação nem ordenação garantida
export async function listarEventos() {
  const { data } = await api.get<EventoOperacional[]>(ROTA)
  return data
}

export async function criarEvento(dados: EventoOperacionalRequest) {
  const { data } = await api.post<EventoOperacional>(ROTA, dados)
  return data
}

export async function buscarEvento(id: number) {
  const { data } = await api.get<EventoOperacional>(`${ROTA}/${id}`)
  return data
}

// A situação esperada protege contra alterações concorrentes: divergência retorna 409
export async function transicionarEvento(id: number, dados: TransicaoEventoRequest) {
  const { data } = await api.post<TransicaoEvento>(`${ROTA}/${id}/transicoes`, dados)
  return data
}

// Ordem fixa do backend: alteradoEm e id decrescentes
export async function listarTransicoes(id: number, page = 0) {
  const { data } = await api.get<PageResponse<TransicaoEvento>>(`${ROTA}/${id}/transicoes`, { params: { page, size: 20 } })
  return data
}

// Somente eventos MANUTENCAO do ativo; ordem fixa por início efetivo, com início nulo ao final
export async function listarManutencoesDoAtivo(ativoId: number, page: number, filtros: FiltrosManutencao) {
  const params = Object.fromEntries(Object.entries({ page, size: 20, ...filtros }).filter(([, valor]) => valor !== undefined))
  const { data } = await api.get<PageResponse<ManutencaoHistorico>>(`/api/ativos-rede/${ativoId}/manutencoes`, { params })
  return data
}

// Perda efetiva de fornecimento: o início físico é obrigatório e não pode ser futuro
export async function registrarInterrupcao(dados: InterrupcaoRequest) {
  const { data } = await api.post<EventoOperacional>('/api/interrupcoes', dados)
  return data
}

// Gerente ou operador responsável; o evento precisa estar ABERTO ou EM_ANDAMENTO e a versão precisa coincidir (409)
export async function designarEquipe(id: number, dados: DesignacaoEquipeRequest) {
  const { data } = await api.put<EventoOperacional>(`${ROTA}/${id}/equipe-designada`, dados)
  return data
}

// Ordem fixa do backend: registradoEm e id decrescentes
export async function listarDesignacoes(id: number, page = 0) {
  const { data } = await api.get<PageResponse<DesignacaoEquipe>>(`${ROTA}/${id}/designacoes`, { params: { page, size: 20 } })
  return data
}

const ROTA_FALHAS = '/api/falhas-equipamento'

// Gerente ou operador; um ativo admite uma única falha não concluída (409)
export async function registrarFalha(dados: FalhaEquipamentoRequest) {
  const { data } = await api.post<FalhaEquipamento>(ROTA_FALHAS, dados)
  return data
}

// Ordem fixa do backend: registradoEm e id decrescentes; o período filtra o registro da falha
export async function listarFalhas(page: number, filtros: FiltrosFalhas) {
  const params = Object.fromEntries(Object.entries({ page, size: 20, ...filtros }).filter(([, valor]) => valor !== undefined))
  const { data } = await api.get<PageResponse<FalhaEquipamento>>(ROTA_FALHAS, { params })
  return data
}
