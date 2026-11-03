import api from './api'
import type { MedicaoTransformador, PageResponse, Transformador, TransformadorRequest } from '../types'

const ROTA = '/api/transformadores'

export async function listarTransformadores() {
  const { data } = await api.get<Transformador[]>(ROTA)
  return data
}

export async function criarTransformador(dados: TransformadorRequest) {
  const { data } = await api.post<Transformador>(ROTA, dados)
  return data
}

export async function atualizarTransformador(id: number, dados: TransformadorRequest) {
  const { data } = await api.put<Transformador>(`${ROTA}/${id}`, dados)
  return data
}

// Ordem fixa do backend: medidoEm e id decrescentes; size máximo de 100
export async function listarMedicoesTransformador(id: number, size = 20) {
  const { data } = await api.get<PageResponse<MedicaoTransformador>>(`${ROTA}/${id}/medicoes`, { params: { page: 0, size } })
  return data.content
}

// A API ordena por medidoEm decrescente, então a primeira leitura é a mais recente
export async function consultarUltimaMedicao(id: number): Promise<MedicaoTransformador | null> {
  const { data } = await api.get<PageResponse<MedicaoTransformador>>(`${ROTA}/${id}/medicoes`, {
    params: { page: 0, size: 1 },
  })
  return data.content[0] ?? null
}
