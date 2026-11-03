import api from './api'
import type { InstalacaoMedidorRequest, Medidor, MedidorRequest } from '../types'

const ROTA = '/api/medidores'

export async function listarMedidores() {
  const { data } = await api.get<Medidor[]>(ROTA)
  return data
}

// O medidor nasce em estoque, sem titular nem transformador
export async function criarMedidor(dados: MedidorRequest) {
  const { data } = await api.post<Medidor>(ROTA, dados)
  return data
}

export async function instalarMedidor(id: number, dados: InstalacaoMedidorRequest) {
  const { data } = await api.patch<Medidor>(`${ROTA}/${id}/instalacao`, dados)
  return data
}
