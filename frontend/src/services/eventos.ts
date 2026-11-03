import api from './api'
import type { EventoOperacional, EventoOperacionalRequest, InterrupcaoRequest } from '../types'

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

// Perda efetiva de fornecimento: o início físico é obrigatório e não pode ser futuro
export async function registrarInterrupcao(dados: InterrupcaoRequest) {
  const { data } = await api.post<EventoOperacional>('/api/interrupcoes', dados)
  return data
}
