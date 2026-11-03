import api from './api'
import type { Alerta, AlertaRequest } from '../types'

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
