import type { PerfilAcesso, SituacaoAlerta, Usuario } from '../../types'
import { formatarDataHora } from '../../utils/formatacao'

export type AcaoTratativa = 'reconhecer' | 'resolver'

// Gerente e operador tratam alertas; o técnico apenas consulta
const PERFIS_TRATATIVA: PerfilAcesso[] = ['GERENTE_OPERACOES', 'OPERADOR']

export function podeTratar(usuario: Usuario | null) {
  return Boolean(usuario && PERFIS_TRATATIVA.includes(usuario.perfil))
}

// O backend só avança ABERTO → RECONHECIDO → RESOLVIDO
export function proximaAcao(situacao: SituacaoAlerta): AcaoTratativa | null {
  if (situacao === 'ABERTO') return 'reconhecer'
  if (situacao === 'RECONHECIDO') return 'resolver'
  return null
}

export const ROTULO_ACAO: Record<AcaoTratativa, string> = {
  reconhecer: 'Reconhecer',
  resolver: 'Resolver',
}

export function autorTratativa(id: number | null, usuario: Usuario | null) {
  if (id === null) return 'autor não registrado'
  return id === usuario?.id ? 'você' : `usuário #${id}`
}

// Alertas antigos podem ter a situação sem auditoria; nesse caso só a situação é exibida
export function descreverEtapa(rotulo: string, instante: string | null, autorId: number | null, usuario: Usuario | null) {
  if (!instante && autorId === null) return null
  return `${rotulo} por ${autorTratativa(autorId, usuario)}${instante ? ` em ${formatarDataHora(instante)}` : ''}`
}
