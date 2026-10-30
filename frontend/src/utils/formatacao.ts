export function formatarNumero(valor: number) {
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 6 })
}

// A data não tem fuso: converter com new Date pode exibir o dia anterior
export function formatarData(data: string) {
  return data.split('-').reverse().join('/')
}
