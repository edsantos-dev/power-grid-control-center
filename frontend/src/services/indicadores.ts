import api from './api'
import type { IndicadoresOperacionais, TipoAtivoIndicador } from '../types'

type Filtros = { dataInicio: string; dataFim: string; tipos: TipoAtivoIndicador[]; subestacaoId?: number }

// Exclusivo do gerente; o período é semiaberto e o fim não pode ser futuro
export async function consultarIndicadores({ dataInicio, dataFim, tipos, subestacaoId }: Filtros) {
  const { data } = await api.get<IndicadoresOperacionais>('/api/indicadores/operacionais', {
    params: { dataInicio, dataFim, tipos: tipos.join(','), ...(subestacaoId && { subestacaoId }) },
  })
  return data
}
