import api from './api'
import type { FiltrosUsina, PageResponse, Usina, UsinaRequest } from '../types'

const ROTA = '/api/usinas'

// Paginada no servidor, em ordem de ID; filtros vazios não são enviados
export async function listarUsinas(page: number, filtros: FiltrosUsina, size = 20) {
  const params = Object.fromEntries(Object.entries({ page, size, ...filtros }).filter(([, valor]) => valor !== undefined && valor !== ''))
  const { data } = await api.get<PageResponse<Usina>>(ROTA, { params })
  return data
}

// Percorre todas as páginas, para quem precisa de nomes de todas as usinas
export async function listarTodasUsinas() {
  const todas: Usina[] = []
  for (let page = 0; ; page++) {
    const resposta = await listarUsinas(page, {}, 100)
    todas.push(...resposta.content)
    if (resposta.last) return todas
  }
}

// Escritas exclusivas do gerente
export async function criarUsina(dados: UsinaRequest) {
  const { data } = await api.post<Usina>(ROTA, dados)
  return data
}

export async function atualizarUsina(id: number, dados: UsinaRequest) {
  const { data } = await api.put<Usina>(`${ROTA}/${id}`, dados)
  return data
}
