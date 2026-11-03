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

// Interpreta o valor de um input datetime-local no fuso do navegador e devolve o instante em UTC
export function instanteDeDataHoraLocal(valor: string) {
  return new Date(valor).toISOString()
}

// Valor atual no formato de um input datetime-local, no fuso do navegador
export function dataHoraLocalAtual(agora = new Date()) {
  const dois = (valor: number) => String(valor).padStart(2, '0')
  return `${agora.getFullYear()}-${dois(agora.getMonth() + 1)}-${dois(agora.getDate())}T${dois(agora.getHours())}:${dois(agora.getMinutes())}`
}

// Fim exclusivo de um período por dia local: início do dia seguinte ao último dia selecionado
export function inicioDoDiaSeguinteLocal(data: string) {
  const [ano, mes, dia] = data.split('-').map(Number)
  return new Date(ano, mes - 1, dia + 1).toISOString()
}

export function formatarDuracao(segundos: number) {
  const minutos = Math.round(segundos / 60)
  if (minutos < 60) return `${minutos} min`
  const horas = Math.floor(minutos / 60)
  const resto = minutos % 60
  return resto ? `${horas} h ${resto} min` : `${horas} h`
}

// A data não tem fuso: converter com new Date pode exibir o dia anterior
export function formatarData(data: string) {
  return data.split('-').reverse().join('/')
}
