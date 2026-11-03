import type { SituacaoEquipe } from '../../types'

export const SITUACAO_EQUIPE: Record<SituacaoEquipe, { label: string; text: string; dot: string }> = {
  DISPONIVEL: { label: 'Disponível', text: 'text-green-400', dot: 'bg-green-500' },
  EM_ATENDIMENTO: { label: 'Em atendimento', text: 'text-yellow-400', dot: 'bg-yellow-500' },
  FORA_DE_SERVICO: { label: 'Fora de serviço', text: 'text-slate-400', dot: 'bg-slate-500' },
}

export const SITUACOES_EQUIPE = Object.keys(SITUACAO_EQUIPE) as SituacaoEquipe[]
