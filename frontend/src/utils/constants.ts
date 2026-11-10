import type { Criticidade, EstadoOperacional, PerfilAcesso, SituacaoAlerta } from '../types'

export const PERFIL_LABEL: Record<PerfilAcesso, string> = {
  OPERADOR: 'Operador',
  GERENTE_OPERACOES: 'Gerente de Operações',
  TECNICO_MANUTENCAO: 'Técnico de Manutenção',
}

export const ESTADO_STYLES: Record<EstadoOperacional, { dot: string; text: string; label: string }> = {
  OPERACIONAL: { dot: 'bg-green-500', text: 'text-green-400', label: 'Operacional' },
  FALHA: { dot: 'bg-red-500', text: 'text-red-400', label: 'Falha' },
  MANUTENCAO: { dot: 'bg-slate-500', text: 'text-slate-400', label: 'Manutenção' },
}

export const ESTADOS: EstadoOperacional[] = ['OPERACIONAL', 'FALHA', 'MANUTENCAO']

// Cadastros não atribuem nem retiram FALHA: o estado nasce e termina no fluxo de falha de equipamento.
// Um ativo já em falha mantém o estado ao editar os demais dados; os outros escolhem entre operacional e manutenção.
export function opcoesEstadoCadastro(atual?: EstadoOperacional) {
  const estados: EstadoOperacional[] = atual === 'FALHA' ? ['FALHA'] : ['OPERACIONAL', 'MANUTENCAO']
  return estados.map((valor) => ({ valor, label: ESTADO_STYLES[valor].label }))
}

// Da maior para a menor, mesma ordem aplicada pelo backend
export const CRITICIDADES: Criticidade[] = ['CRITICA', 'ALTA', 'MEDIA', 'BAIXA']

export const CRITICIDADE_STYLES: Record<Criticidade, { dot: string; text: string; bg: string; border: string; label: string }> = {
  CRITICA: { dot: 'bg-red-500', text: 'text-red-400', bg: 'bg-red-500/10', border: 'border-red-500/40', label: 'Crítica' },
  ALTA: { dot: 'bg-orange-500', text: 'text-orange-400', bg: 'bg-orange-500/10', border: 'border-orange-500/40', label: 'Alta' },
  MEDIA: { dot: 'bg-yellow-500', text: 'text-yellow-400', bg: 'bg-yellow-500/10', border: 'border-slate-800', label: 'Média' },
  BAIXA: { dot: 'bg-blue-500', text: 'text-blue-400', bg: 'bg-blue-500/10', border: 'border-slate-800', label: 'Baixa' },
}

export const SITUACAO_ALERTA_LABEL: Record<SituacaoAlerta, string> = {
  ABERTO: 'Aberto',
  RECONHECIDO: 'Reconhecido',
  RESOLVIDO: 'Resolvido',
}
