export function formatarNumero(valor: number) {
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 6 })
}

export function formatarPercentual(valor: number) {
  return `${valor.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 2 })}%`
}

export function formatarDataHora(instante: string) {
  return new Date(instante).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' })
}

// Converte um dia do calendário local no instante de início desse dia, em UTC
export function inicioDoDiaLocal(data: string) {
  const [ano, mes, dia] = data.split('-').map(Number)
  return new Date(ano, mes - 1, dia).toISOString()
}

// A data não tem fuso: converter com new Date pode exibir o dia anterior
export function formatarData(data: string) {
  return data.split('-').reverse().join('/')
}
