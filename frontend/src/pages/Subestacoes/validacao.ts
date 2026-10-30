import type { SubestacaoRequest } from '../../types'
import {
  normalizarDecimal,
  removerCamposValidos,
  validarCoordenada,
  validarNome,
  validarObrigatorio,
  validarPositivo,
} from '../../utils/validacao'

export type ErrosSubestacao = Partial<Record<keyof SubestacaoRequest, string>>

export function normalizarSubestacao(dados: SubestacaoRequest): SubestacaoRequest {
  return {
    ...dados,
    nome: dados.nome.trim(),
    latitude: normalizarDecimal(dados.latitude),
    longitude: normalizarDecimal(dados.longitude),
    tensaoNominalKv: normalizarDecimal(dados.tensaoNominalKv),
    capacidadeMva: normalizarDecimal(dados.capacidadeMva),
  }
}

export function validarSubestacao(dados: SubestacaoRequest) {
  return removerCamposValidos<ErrosSubestacao>({
    nome: validarNome(dados.nome),
    latitude: validarCoordenada(dados.latitude, 90),
    longitude: validarCoordenada(dados.longitude, 180),
    tensaoNominalKv: validarPositivo(dados.tensaoNominalKv),
    capacidadeMva: validarPositivo(dados.capacidadeMva),
    dataInstalacao: validarObrigatorio(dados.dataInstalacao),
  })
}
