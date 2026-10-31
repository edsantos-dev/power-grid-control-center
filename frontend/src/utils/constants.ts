import type { EstadoOperacional, PerfilAcesso } from '../types'

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

export const ESTADO_OPCOES = ESTADOS.map((valor) => ({ valor, label: ESTADO_STYLES[valor].label }))
