import api from './api'
import type { Consumidor, ConsumidorRequest, Medidor } from '../types'

const ROTA = '/api/consumidores'

export async function listarConsumidores() {
  const { data } = await api.get<Consumidor[]>(ROTA)
  return data
}

export async function criarConsumidor(dados: ConsumidorRequest) {
  const { data } = await api.post<Consumidor>(ROTA, dados)
  return data
}

export async function listarMedidoresDoConsumidor(id: number) {
  const { data } = await api.get<Medidor[]>(`${ROTA}/${id}/medidores`)
  return data
}
