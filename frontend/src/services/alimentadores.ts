import api from './api'
import type { Alimentador, AlimentadorRequest, PerdaTecnica } from '../types'

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

// Cálculo sob demanda: exige período completo, com início inclusivo e fim exclusivo
export async function calcularPerdasTecnicas(id: number, dataInicio: string, dataFim: string) {
  const { data } = await api.get<PerdaTecnica>(`${ROTA}/${id}/perdas-tecnicas`, { params: { dataInicio, dataFim } })
  return data
}

// Somente o gerente define o parâmetro; vale para medições ingeridas depois da alteração
export async function definirPercentualPerda(id: number, percentualPerdaTecnica: string) {
  const { data } = await api.patch<{ alimentadorId: number; percentualPerdaTecnica: number }>(
    `${ROTA}/${id}/parametros-perdas`,
    { percentualPerdaTecnica }
  )
  return data
}
