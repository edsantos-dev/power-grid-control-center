import api from './api'
import type {
  Alerta,
  AlertaRequest,
  AlertaSobrecarga,
  AlertaTensao,
  AlertaComunicacao,
  AvaliacaoComunicacao,
  StatusComunicacao,
  AlertaHistorico,
  FiltrosHistoricoAlertas,
  PageResponse,
  StatusAlertaAutomatico,
  TipoAlertaTensao,
  TratativaAlerta,
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

// O detalhe continua disponível depois da resolução
export async function buscarAlerta(id: number) {
  const { data } = await api.get<Alerta>(`${ROTA}/${id}`)
  return data
}

// ABERTO → RECONHECIDO → RESOLVIDO; pular etapa ou voltar retorna 409 e repetir a ação preserva o primeiro registro
export async function reconhecerAlerta(id: number, comentario: string) {
  const { data } = await api.patch<Alerta>(`${ROTA}/${id}/reconhecimento`, { comentario })
  return data
}

export async function resolverAlerta(id: number, comentario: string) {
  const { data } = await api.patch<Alerta>(`${ROTA}/${id}/resolucao`, { comentario })
  return data
}

// Ordem fixa do backend: alteradoEm e id decrescentes
export async function listarTratativasAlerta(id: number, page = 0) {
  const { data } = await api.get<PageResponse<TratativaAlerta>>(`${ROTA}/${id}/historico`, { params: { page, size: 20 } })
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

// Ordem fixa do backend: detectadoEm e id decrescentes
export async function listarAlertasComunicacao(page: number, status?: StatusComunicacao) {
  const { data } = await api.get<PageResponse<AlertaComunicacao>>('/api/alertas-comunicacao', {
    params: { page, size: 20, ...(status && { status }) },
  })
  return data
}

// Une as quatro origens com paginação global e ordem fixa: ocorridoEm, tipo e id decrescentes
export async function listarHistoricoAlertas(page: number, filtros: FiltrosHistoricoAlertas, size = 20) {
  const params = Object.fromEntries(Object.entries({ page, size, ...filtros }).filter(([, valor]) => valor !== undefined))
  const { data } = await api.get<PageResponse<AlertaHistorico>>('/api/alertas-dashboard', { params })
  return data
}

// A detecção não é agendada: roda sob demanda, para OPERADOR e GERENTE_OPERACOES
export async function avaliarComunicacao() {
  const { data } = await api.post<AvaliacaoComunicacao>('/api/jobs/avaliar-comunicacao')
  return data
}
