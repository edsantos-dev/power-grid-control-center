import type { ConsumidorRequest, TipoPessoa } from '../../types'
import { CAMPO_OBRIGATORIO, removerCamposValidos, validarNome } from '../../utils/validacao'

export type ConsumidorFormValues = Omit<ConsumidorRequest, 'tipoPessoa' | 'enderecoCorrespondencia'> & {
  tipoPessoa: TipoPessoa | ''
  enderecoCorrespondencia: string
}

export type ErrosConsumidor = Partial<Record<keyof ConsumidorFormValues, string>>

const CPF = /^\d{11}$/
// CNPJ alfanumérico: doze posições com letras ou dígitos e dois dígitos verificadores
const CNPJ = /^[A-Z0-9]{12}\d{2}$/

// Igual ao backend: remove a máscara, preserva zeros e converte letras para maiúsculas
export function normalizarDocumento(documento: string) {
  return documento.replace(/[\s./-]/g, '').toUpperCase()
}

function validarDocumento(documento: string, tipo: TipoPessoa | '') {
  if (!documento) return CAMPO_OBRIGATORIO
  if (tipo === 'PF' && !CPF.test(documento)) return 'Informe um CPF com 11 dígitos.'
  if (tipo === 'PJ' && !CNPJ.test(documento)) return 'Informe um CNPJ com 14 posições.'
  return undefined
}

export function validarConsumidor(dados: ConsumidorFormValues) {
  return removerCamposValidos<ErrosConsumidor>({
    nomeRazaoSocial: validarNome(dados.nomeRazaoSocial.trim()),
    tipoPessoa: dados.tipoPessoa ? undefined : CAMPO_OBRIGATORIO,
    documento: validarDocumento(normalizarDocumento(dados.documento), dados.tipoPessoa),
  })
}

// Os dígitos verificadores são conferidos pelo backend, que é a regra oficial
export function paraRequisicao(dados: ConsumidorFormValues): ConsumidorRequest {
  return {
    nomeRazaoSocial: dados.nomeRazaoSocial.trim(),
    tipoPessoa: dados.tipoPessoa as TipoPessoa,
    documento: normalizarDocumento(dados.documento),
    enderecoCorrespondencia: dados.enderecoCorrespondencia.trim() || null,
  }
}
