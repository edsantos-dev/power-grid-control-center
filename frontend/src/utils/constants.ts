import type { PerfilAcesso } from '../types'

export const PERFIL_LABEL: Record<PerfilAcesso, string> = {
  OPERADOR: 'Operador',
  GERENTE_OPERACOES: 'Gerente de Operações',
  TECNICO_MANUTENCAO: 'Técnico de Manutenção',
}
