import api from './api'
import type { Subestacao, SubestacaoRequest } from '../types'

const ROTA = '/api/subestacoes'

export async function listarSubestacoes() {
  const { data } = await api.get<Subestacao[]>(ROTA)
  return data
}

export async function criarSubestacao(dados: SubestacaoRequest) {
  const { data } = await api.post<Subestacao>(ROTA, dados)
  return data
}

export async function atualizarSubestacao(id: number, dados: SubestacaoRequest) {
  const { data } = await api.put<Subestacao>(`${ROTA}/${id}`, dados)
  return data
}
