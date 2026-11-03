import type { EventoOperacional, SituacaoEvento, Usuario } from '../../types'

export type Transicao = { destino: SituacaoEvento; rotulo: string }

// O backend aceita apenas o avanço ABERTO → EM_ANDAMENTO → CONCLUIDO
export function proximaTransicao(evento: EventoOperacional): Transicao | null {
  if (evento.situacao === 'ABERTO') return { destino: 'EM_ANDAMENTO', rotulo: 'Iniciar' }
  if (evento.situacao === 'EM_ANDAMENTO') return { destino: 'CONCLUIDO', rotulo: 'Concluir' }
  return null
}

// Mesma regra do serviço: gerente sempre; operador ou técnico apenas quando é o responsável
export function podeTransicionar(evento: EventoOperacional, usuario: Usuario | null) {
  if (!usuario) return false
  if (usuario.perfil === 'GERENTE_OPERACOES') return true
  return evento.responsavelUsuarioId === usuario.id
}

export function exigeTerminoEfetivo(evento: EventoOperacional, destino: SituacaoEvento) {
  return destino === 'CONCLUIDO' && evento.categoria === 'INTERRUPCAO_FORNECIMENTO'
}
