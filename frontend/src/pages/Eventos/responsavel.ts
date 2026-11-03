export type Responsavel = { modo: 'eu' | 'outro'; outroId: string }

// Não há consulta de usuários na API: o responsável é o usuário atual ou um ID informado
export function idDoResponsavel(valor: Responsavel, usuarioId: number | undefined) {
  return valor.modo === 'eu' ? usuarioId : Number(valor.outroId)
}

export function validarResponsavel(valor: Responsavel) {
  if (valor.modo === 'eu') return undefined
  return /^[1-9]\d*$/.test(valor.outroId.trim()) ? undefined : 'Informe o ID numérico do usuário responsável.'
}
