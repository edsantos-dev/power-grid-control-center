import type { LinhaTransmissaoRequest } from '../../types'
import {
  normalizarDecimal,
  removerCamposValidos,
  validarCoordenada,
  validarNome,
  validarObrigatorio,
  validarPositivo,
} from '../../utils/validacao'

export type LinhaFormValues = Omit<
  LinhaTransmissaoRequest,
  'latitude' | 'longitude' | 'subestacaoOrigemId' | 'subestacaoDestinoId'
> & {
  latitude: string
  longitude: string
  subestacaoOrigemId: string
  subestacaoDestinoId: string
}

export type ErrosLinha = Partial<Record<keyof LinhaFormValues, string>>

const COORDENADAS_EM_CONJUNTO = 'Informe latitude e longitude em conjunto.'

export function normalizarLinha(dados: LinhaFormValues): LinhaFormValues {
  return {
    ...dados,
    nome: dados.nome.trim(),
    latitude: normalizarDecimal(dados.latitude),
    longitude: normalizarDecimal(dados.longitude),
    tensaoOperacaoKv: normalizarDecimal(dados.tensaoOperacaoKv),
    extensaoKm: normalizarDecimal(dados.extensaoKm),
  }
}

// As coordenadas da linha são opcionais, mas só valem quando informadas juntas
function validarCoordenadaOpcional(valor: string, outra: string, limite: number) {
  if (!valor) return outra ? COORDENADAS_EM_CONJUNTO : undefined
  return validarCoordenada(valor, limite)
}

export function validarLinha(dados: LinhaFormValues) {
  const destinoIgual =
    dados.subestacaoDestinoId && dados.subestacaoDestinoId === dados.subestacaoOrigemId
      ? 'Origem e destino devem ser subestações diferentes.'
      : undefined

  return removerCamposValidos<ErrosLinha>({
    nome: validarNome(dados.nome),
    subestacaoOrigemId: validarObrigatorio(dados.subestacaoOrigemId),
    subestacaoDestinoId: validarObrigatorio(dados.subestacaoDestinoId) ?? destinoIgual,
    latitude: validarCoordenadaOpcional(dados.latitude, dados.longitude, 90),
    longitude: validarCoordenadaOpcional(dados.longitude, dados.latitude, 180),
    tensaoOperacaoKv: validarPositivo(dados.tensaoOperacaoKv),
    extensaoKm: validarPositivo(dados.extensaoKm),
    dataInstalacao: validarObrigatorio(dados.dataInstalacao),
  })
}

export function paraRequisicao(dados: LinhaFormValues): LinhaTransmissaoRequest {
  return {
    ...dados,
    latitude: dados.latitude || null,
    longitude: dados.longitude || null,
    subestacaoOrigemId: Number(dados.subestacaoOrigemId),
    subestacaoDestinoId: Number(dados.subestacaoDestinoId),
  }
}
