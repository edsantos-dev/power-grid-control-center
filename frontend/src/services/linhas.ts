import api from './api'
import type { LinhaTransmissao, LinhaTransmissaoRequest } from '../types'

const ROTA = '/api/linhas-transmissao'

export async function listarLinhas() {
  const { data } = await api.get<LinhaTransmissao[]>(ROTA)
  return data
}

export async function criarLinha(dados: LinhaTransmissaoRequest) {
  const { data } = await api.post<LinhaTransmissao>(ROTA, dados)
  return data
}

export async function atualizarLinha(id: number, dados: LinhaTransmissaoRequest) {
  const { data } = await api.put<LinhaTransmissao>(`${ROTA}/${id}`, dados)
  return data
}
