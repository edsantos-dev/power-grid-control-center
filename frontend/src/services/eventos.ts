import api from './api'
import type {
  EventoOperacional,
  EventoOperacionalRequest,
  InterrupcaoRequest,
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

// Perda efetiva de fornecimento: o início físico é obrigatório e não pode ser futuro
export async function registrarInterrupcao(dados: InterrupcaoRequest) {
  const { data } = await api.post<EventoOperacional>('/api/interrupcoes', dados)
  return data
}
