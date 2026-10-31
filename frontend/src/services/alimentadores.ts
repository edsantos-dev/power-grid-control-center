import api from './api'
import type { Alimentador, AlimentadorRequest } from '../types'

const ROTA = '/api/alimentadores'

export async function listarAlimentadores() {
  const { data } = await api.get<Alimentador[]>(ROTA)
  return data
}

export async function criarAlimentador(dados: AlimentadorRequest) {
  const { data } = await api.post<Alimentador>(ROTA, dados)
  return data
}

export async function atualizarAlimentador(id: number, dados: AlimentadorRequest) {
  const { data } = await api.put<Alimentador>(`${ROTA}/${id}`, dados)
  return data
}
