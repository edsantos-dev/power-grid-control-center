import type { InstalacaoMedidorRequest, MedidorRequest } from '../../types'
import {
  CAMPO_OBRIGATORIO,
  normalizarDecimal,
  removerCamposValidos,
  validarCoordenada,
  validarNome,
  validarObrigatorio,
} from '../../utils/validacao'

export type ErrosMedidor = Partial<Record<keyof MedidorRequest, string>>

export type InstalacaoFormValues = Omit<
  InstalacaoMedidorRequest,
  'consumidorId' | 'transformadorId' | 'enderecoFornecimento'
> & {
  consumidorId: string
  transformadorId: string
  enderecoFornecimento: string
}

export type ErrosInstalacao = Partial<Record<keyof InstalacaoFormValues, string>>

// A série preserva capitalização, zeros e pontuação interna; só as extremidades são removidas
export function normalizarMedidor(dados: MedidorRequest): MedidorRequest {
  return { ...dados, nome: dados.nome.trim(), numeroSerie: dados.numeroSerie.trim() }
}

export function validarMedidor(dados: MedidorRequest) {
  return removerCamposValidos<ErrosMedidor>({
    nome: validarNome(dados.nome),
    numeroSerie: !dados.numeroSerie
      ? CAMPO_OBRIGATORIO
      : dados.numeroSerie.length > 128
        ? 'Informe até 128 caracteres.'
        : undefined,
  })
}

// Data local no formato do input date, para comparar sem conversão de fuso
export function hojeLocal(agora = new Date()) {
  const doisDigitos = (valor: number) => String(valor).padStart(2, '0')
  return `${agora.getFullYear()}-${doisDigitos(agora.getMonth() + 1)}-${doisDigitos(agora.getDate())}`
}

export function normalizarInstalacao(dados: InstalacaoFormValues): InstalacaoFormValues {
  return {
    ...dados,
    latitude: normalizarDecimal(dados.latitude),
    longitude: normalizarDecimal(dados.longitude),
    enderecoFornecimento: dados.enderecoFornecimento.trim(),
  }
}

export function validarInstalacao(dados: InstalacaoFormValues, hoje = hojeLocal()) {
  return removerCamposValidos<ErrosInstalacao>({
    consumidorId: validarObrigatorio(dados.consumidorId),
    transformadorId: validarObrigatorio(dados.transformadorId),
    latitude: validarCoordenada(dados.latitude, 90),
    longitude: validarCoordenada(dados.longitude, 180),
    dataInstalacao: !dados.dataInstalacao
      ? CAMPO_OBRIGATORIO
      : dados.dataInstalacao > hoje
        ? 'A data de instalação não pode ser futura.'
        : undefined,
  })
}

export function paraRequisicaoInstalacao(dados: InstalacaoFormValues): InstalacaoMedidorRequest {
  return {
    ...dados,
    consumidorId: Number(dados.consumidorId),
    transformadorId: Number(dados.transformadorId),
    enderecoFornecimento: dados.enderecoFornecimento || null,
  }
}
