import type { CategoriaEvento, SituacaoEvento } from '../../types'

export const CATEGORIA_LABEL: Record<CategoriaEvento, string> = {
  MANOBRA: 'Manobra',
  DESLIGAMENTO_PROGRAMADO: 'Desligamento programado',
  INTERRUPCAO_FORNECIMENTO: 'Interrupção de fornecimento',
  SOBRECARGA: 'Sobrecarga',
  FALHA_EQUIPAMENTO: 'Falha de equipamento',
  MANUTENCAO: 'Manutenção',
}

export const SITUACAO_EVENTO_STYLES: Record<SituacaoEvento, { text: string; label: string }> = {
  ABERTO: { text: 'text-red-400', label: 'Aberto' },
  EM_ANDAMENTO: { text: 'text-yellow-400', label: 'Em andamento' },
  CONCLUIDO: { text: 'text-green-400', label: 'Concluído' },
}

export const SITUACOES_EVENTO: SituacaoEvento[] = ['ABERTO', 'EM_ANDAMENTO', 'CONCLUIDO']
