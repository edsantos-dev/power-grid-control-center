import type { SubestacaoRequest } from '../../types'

export type ErrosSubestacao = Partial<Record<keyof SubestacaoRequest, string>>

const COORDENADA = /^-?\d{1,3}(\.\d{1,7})?$/
const POSITIVO = /^\d{1,13}(\.\d{1,6})?$/

function validarCoordenada(valor: string, limite: number) {
  if (!valor) return 'Campo obrigatório.'
  if (!COORDENADA.test(valor)) return 'Informe um número com até sete casas decimais.'
  if (Math.abs(Number(valor)) > limite) return `Informe um valor entre -${limite} e ${limite}.`
  return undefined
}

function validarPositivo(valor: string) {
  if (!valor) return 'Campo obrigatório.'
  if (!POSITIVO.test(valor)) return 'Informe um número com até 13 dígitos inteiros e seis casas decimais.'
  if (Number(valor) <= 0) return 'Informe um valor maior que zero.'
  return undefined
}

export function normalizarSubestacao(dados: SubestacaoRequest): SubestacaoRequest {
  const decimal = (valor: string) => valor.trim().replace(',', '.')

  return {
    ...dados,
    nome: dados.nome.trim(),
    latitude: decimal(dados.latitude),
    longitude: decimal(dados.longitude),
    tensaoNominalKv: decimal(dados.tensaoNominalKv),
    capacidadeMva: decimal(dados.capacidadeMva),
  }
}

export function validarSubestacao(dados: SubestacaoRequest) {
  const erros: ErrosSubestacao = {
    nome: !dados.nome ? 'Campo obrigatório.' : dados.nome.length > 255 ? 'Informe até 255 caracteres.' : undefined,
    latitude: validarCoordenada(dados.latitude, 90),
    longitude: validarCoordenada(dados.longitude, 180),
    tensaoNominalKv: validarPositivo(dados.tensaoNominalKv),
    capacidadeMva: validarPositivo(dados.capacidadeMva),
    dataInstalacao: !dados.dataInstalacao ? 'Campo obrigatório.' : undefined,
  }

  return Object.fromEntries(Object.entries(erros).filter(([, erro]) => erro)) as ErrosSubestacao
}
