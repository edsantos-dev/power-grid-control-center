const COORDENADA = /^-?\d{1,3}(\.\d{1,7})?$/
const POSITIVO = /^\d{1,13}(\.\d{1,6})?$/

export const CAMPO_OBRIGATORIO = 'Campo obrigatório.'

export function normalizarDecimal(valor: string) {
  return valor.trim().replace(',', '.')
}

export function validarNome(valor: string) {
  if (!valor) return CAMPO_OBRIGATORIO
  if (valor.length > 255) return 'Informe até 255 caracteres.'
  return undefined
}

export function validarCoordenada(valor: string, limite: number) {
  if (!valor) return CAMPO_OBRIGATORIO
  if (!COORDENADA.test(valor)) return 'Informe um número com até sete casas decimais.'
  if (Math.abs(Number(valor)) > limite) return `Informe um valor entre -${limite} e ${limite}.`
  return undefined
}

export function validarPositivo(valor: string) {
  if (!valor) return CAMPO_OBRIGATORIO
  if (!POSITIVO.test(valor)) return 'Informe um número com até 13 dígitos inteiros e seis casas decimais.'
  if (Number(valor) <= 0) return 'Informe um valor maior que zero.'
  return undefined
}

export function validarObrigatorio(valor: string) {
  return valor ? undefined : CAMPO_OBRIGATORIO
}

export function removerCamposValidos<T extends object>(erros: T) {
  return Object.fromEntries(Object.entries(erros).filter(([, erro]) => erro)) as T
}
