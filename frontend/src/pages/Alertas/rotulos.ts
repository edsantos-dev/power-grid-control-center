import type { StatusOrigemAlerta, TipoAlertaHistorico, TipoAtivoAlerta } from '../../types'

export const TIPO_LABEL: Record<TipoAlertaHistorico, string> = {
  GENERICO: 'Registrado',
  SOBRECARGA: 'Sobrecarga',
  TENSAO: 'Tensão',
  COMUNICACAO: 'Comunicação',
}

export const STATUS_LABEL: Record<StatusOrigemAlerta, string> = {
  ABERTO: 'Aberto',
  ATIVO: 'Ativo',
  RECONHECIDO: 'Reconhecido',
  RESOLVIDO: 'Resolvido',
  RESTABELECIDO: 'Restabelecido',
}

export const TIPO_ATIVO_LABEL: Record<TipoAtivoAlerta, string> = {
  SUBESTACAO: 'Subestação',
  ALIMENTADOR: 'Alimentador',
  TRANSFORMADOR: 'Transformador',
  LINHA: 'Linha',
  USINA: 'Usina',
  MEDIDOR: 'Medidor',
}

export const opcoes = <K extends string>(rotulos: Record<K, string>) =>
  (Object.keys(rotulos) as K[]).map((valor) => ({ valor, label: rotulos[valor] }))
