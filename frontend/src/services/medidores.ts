import api from './api'
import type { InstalacaoMedidorRequest, Medidor, MedidorRequest, MedicaoConsumo } from '../types'

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

// A API consulta uma medição pelo identificador: não há listagem de medições por medidor
export async function consultarMedicaoConsumo(medidorId: number, medicaoId: number) {
  const { data } = await api.get<MedicaoConsumo>(`${ROTA}/${medidorId}/medicoes-consumo/${medicaoId}`)
  return data
}
