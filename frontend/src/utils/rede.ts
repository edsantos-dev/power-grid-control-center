export type NomesRede = Record<'transformadores' | 'alimentadores' | 'subestacoes', Map<number, string>>

export function nomesPorId(lista: { id: number; nome: string }[]) {
  return new Map(lista.map((item) => [item.id, item.nome]))
}
