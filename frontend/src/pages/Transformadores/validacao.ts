import type { TransformadorRequest } from '../../types'
import {
  normalizarDecimal,
  removerCamposValidos,
  validarCoordenada,
  validarNome,
  validarObrigatorio,
  validarPositivo,
} from '../../utils/validacao'

export type TransformadorFormValues = Omit<TransformadorRequest, 'alimentadorId'> & { alimentadorId: string }

export type ErrosTransformador = Partial<Record<keyof TransformadorFormValues, string>>

export function normalizarTransformador(dados: TransformadorFormValues): TransformadorFormValues {
  return {
    ...dados,
    nome: dados.nome.trim(),
    latitude: normalizarDecimal(dados.latitude),
    longitude: normalizarDecimal(dados.longitude),
    capacidadeMva: normalizarDecimal(dados.capacidadeMva),
  }
}

export function validarTransformador(dados: TransformadorFormValues) {
  return removerCamposValidos<ErrosTransformador>({
    nome: validarNome(dados.nome),
    alimentadorId: validarObrigatorio(dados.alimentadorId),
    latitude: validarCoordenada(dados.latitude, 90),
    longitude: validarCoordenada(dados.longitude, 180),
    capacidadeMva: validarPositivo(dados.capacidadeMva),
    dataInstalacao: validarObrigatorio(dados.dataInstalacao),
  })
}

export function paraRequisicao(dados: TransformadorFormValues): TransformadorRequest {
  return { ...dados, alimentadorId: Number(dados.alimentadorId) }
}
