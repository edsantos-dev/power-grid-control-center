import type { PerfilAcesso, Usuario } from '../types'

// O backend recusa com 403 o que o perfil não pode fazer; aqui só se decide o que a interface oferece.
// A matriz completa está em docs/modelo-dominio.md ("Matriz de acesso por perfil").

// Telas restritas a certos perfis; as demais ficam abertas a qualquer perfil autenticado
const TELAS_RESTRITAS: Record<string, PerfilAcesso[]> = {
  '/usinas': ['GERENTE_OPERACOES'],
  '/relatorios': ['GERENTE_OPERACOES'],
}

export function podeAcessarTela(usuario: Usuario | null, caminho: string) {
  if (!usuario) return false
  const permitidos = TELAS_RESTRITAS[caminho]
  return !permitidos || permitidos.includes(usuario.perfil)
}

// Subestações, linhas, alimentadores, transformadores, medidores e consumidores: o técnico apenas consulta
export function podeCadastrarRede(usuario: Usuario | null) {
  return usuario?.perfil === 'GERENTE_OPERACOES' || usuario?.perfil === 'OPERADOR'
}
