import api from './api'
import type { Consumidor, ConsumidorRequest, Medidor } from '../types'

const ROTA = '/api/consumidores'

export async function listarConsumidores() {
  const { data } = await api.get<Consumidor[]>(ROTA)
  return data
}

export async function criarConsumidor(dados: ConsumidorRequest) {
  const { data: { id, nomeRazaoSocial, tipoPessoa } } = await api.post<Consumidor>(ROTA, dados)
  return { id, nomeRazaoSocial, tipoPessoa }
}

export async function listarMedidoresDoConsumidor(id: number) {
  const { data } = await api.get<Medidor[]>(`${ROTA}/${id}/medidores`)
  return data
}
