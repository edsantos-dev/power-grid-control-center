import type { TipoGeracao } from '../../types'

export const TIPO_GERACAO_LABEL: Record<TipoGeracao, string> = {
  HIDRELETRICA: 'Hidrelétrica',
  SOLAR: 'Solar',
  EOLICA: 'Eólica',
  TERMICA: 'Térmica',
}

export const TIPOS_GERACAO = Object.keys(TIPO_GERACAO_LABEL) as TipoGeracao[]
