import type { AlimentadorRequest } from '../../types'
import {
  normalizarDecimal,
  removerCamposValidos,
  validarCoordenada,
  validarNome,
  validarObrigatorio,
  validarPositivo,
} from '../../utils/validacao'

export type AlimentadorFormValues = Omit<AlimentadorRequest, 'subestacaoId'> & { subestacaoId: string }

export type ErrosAlimentador = Partial<Record<keyof AlimentadorFormValues, string>>

export function normalizarAlimentador(dados: AlimentadorFormValues): AlimentadorFormValues {
  return {
    ...dados,
    nome: dados.nome.trim(),
    latitude: normalizarDecimal(dados.latitude),
    longitude: normalizarDecimal(dados.longitude),
    tensaoNominalKv: normalizarDecimal(dados.tensaoNominalKv),
  }
}

export function validarAlimentador(dados: AlimentadorFormValues) {
  return removerCamposValidos<ErrosAlimentador>({
    nome: validarNome(dados.nome),
    subestacaoId: validarObrigatorio(dados.subestacaoId),
    latitude: validarCoordenada(dados.latitude, 90),
    longitude: validarCoordenada(dados.longitude, 180),
    tensaoNominalKv: validarPositivo(dados.tensaoNominalKv),
    dataInstalacao: validarObrigatorio(dados.dataInstalacao),
  })
}

export function paraRequisicao(dados: AlimentadorFormValues): AlimentadorRequest {
  return { ...dados, subestacaoId: Number(dados.subestacaoId) }
}
