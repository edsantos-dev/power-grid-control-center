import api from './api'
import type { Equipe, EquipeRequest, Integrante, PageResponse, SituacaoEquipe } from '../types'

const ROTA = '/api/equipes'

export async function listarEquipes(page: number, filtros: { nome?: string; situacao?: SituacaoEquipe }) {
  const params = Object.fromEntries(Object.entries({ page, size: 20, ...filtros }).filter(([, valor]) => valor !== undefined && valor !== ''))
  const { data } = await api.get<PageResponse<Equipe>>(ROTA, { params })
  return data
}

// Todas as páginas, para nomear e escolher equipes fora da tela de cadastro
export async function listarTodasEquipes() {
  const equipes: Equipe[] = []
  for (let page = 0; ; page++) {
    const { data } = await api.get<PageResponse<Equipe>>(ROTA, { params: { page, size: 100 } })
    equipes.push(...data.content)
    if (data.last) return equipes
  }
}

// Escritas exclusivas do gerente e do técnico de manutenção
export async function criarEquipe(dados: EquipeRequest) {
  const { data } = await api.post<Equipe>(ROTA, dados)
  return data
}

export async function atualizarEquipe(id: number, dados: EquipeRequest) {
  const { data } = await api.put<Equipe>(`${ROTA}/${id}`, dados)
  return data
}

// DISPONIVEL e EM_ATENDIMENTO exigem ao menos um integrante ativo (409)
export async function alterarSituacaoEquipe(id: number, situacao: SituacaoEquipe) {
  const { data } = await api.patch<Equipe>(`${ROTA}/${id}/situacao`, { situacao })
  return data
}

export async function listarIntegrantes(id: number) {
  const { data } = await api.get<PageResponse<Integrante>>(`${ROTA}/${id}/integrantes`, { params: { page: 0, size: 100 } })
  return data.content
}

// Idempotente: repetir a inclusão de um integrante vigente devolve o mesmo vínculo
export async function incluirIntegrante(id: number, usuarioId: number) {
  const { data } = await api.put<Integrante>(`${ROTA}/${id}/integrantes/${usuarioId}`)
  return data
}

// Encerra a vigência do vínculo, preservando o histórico
export async function removerIntegrante(id: number, usuarioId: number) {
  await api.delete(`${ROTA}/${id}/integrantes/${usuarioId}`)
}
