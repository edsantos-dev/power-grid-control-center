import type { Equipe, EventoOperacional, Usuario } from '../../types'

// Mesma regra do serviço: gerente sempre; operador apenas quando é o responsável; técnico só consulta
export function podeDesignar(evento: EventoOperacional, usuario: Usuario | null) {
  if (!usuario || evento.situacao === 'CONCLUIDO') return false
  if (usuario.perfil === 'GERENTE_OPERACOES') return true
  return usuario.perfil === 'OPERADOR' && evento.responsavelUsuarioId === usuario.id
}

// Uma nova designação exige equipe DISPONIVEL ou EM_ATENDIMENTO; o backend ainda confere os integrantes ativos
export function podeReceberDesignacao(equipe: Equipe) {
  return equipe.situacao === 'DISPONIVEL' || equipe.situacao === 'EM_ATENDIMENTO'
}

export function nomeEquipe(id: number | null, equipes: Equipe[]) {
  if (id === null) return 'Sem equipe'
  return equipes.find((e) => e.id === id)?.nome ?? `Equipe #${id}`
}
