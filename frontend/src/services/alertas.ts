import api from './api'
import type {
  Alerta,
  AlertaRequest,
  AlertaSobrecarga,
  AlertaTensao,
  PageResponse,
  StatusAlertaAutomatico,
  TipoAlertaTensao,
} from '../types'

const ROTA = '/api/alertas'

// O backend devolve só ABERTO e RECONHECIDO, da maior à menor criticidade e do mais recente ao mais antigo
export async function listarAlertasAtivos() {
  const { data } = await api.get<Alerta[]>(ROTA)
  return data
}

export async function criarAlerta(dados: AlertaRequest) {
  const { data } = await api.post<Alerta>(ROTA, dados)
  return data
}

const ROTA_SOBRECARGA = '/api/alertas-sobrecarga'

// Ordem fixa do backend: geradoEm e id decrescentes; size máximo de 100
export async function listarAlertasSobrecarga(page: number, status?: StatusAlertaAutomatico) {
  const { data } = await api.get<PageResponse<AlertaSobrecarga>>(ROTA_SOBRECARGA, {
    params: { page, size: 20, ...(status && { status }) },
  })
  return data
}

// O autor e o instante vêm da sessão; repetir o reconhecimento preserva o primeiro registro
export async function reconhecerAlertaSobrecarga(id: number) {
  const { data } = await api.patch<AlertaSobrecarga>(`${ROTA_SOBRECARGA}/${id}/reconhecimento`)
  return data
}

const ROTA_TENSAO = '/api/alertas-tensao'

export async function listarAlertasTensao(page: number, status?: StatusAlertaAutomatico, tipo?: TipoAlertaTensao) {
  const { data } = await api.get<PageResponse<AlertaTensao>>(ROTA_TENSAO, {
    params: { page, size: 20, ...(status && { status }), ...(tipo && { tipo }) },
  })
  return data
}

export async function reconhecerAlertaTensao(id: number) {
  const { data } = await api.patch<AlertaTensao>(`${ROTA_TENSAO}/${id}/reconhecimento`)
  return data
}
