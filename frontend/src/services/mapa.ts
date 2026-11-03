import api from './api'
import type { AreaMapa, ColecaoMapa, TipoAtivoMapa } from '../types'

// Acima do limite o backend responde 422 em vez de truncar a coleção
export const LIMITE_FEATURES = 1000

export async function consultarMapa(area: AreaMapa, tipos: TipoAtivoMapa[]) {
  const { data } = await api.get<ColecaoMapa>('/api/mapa/ativos', {
    params: { ...area, tipos: tipos.join(','), limite: LIMITE_FEATURES },
  })
  return data
}
