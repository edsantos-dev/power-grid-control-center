import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alertasApi from '../../services/alertas'
import type { AlertaSobrecarga, PageResponse } from '../../types'
import AlertasSobrecarga from './AlertasSobrecarga'

vi.mock('../../services/alertas')

const auth: AuthContextValue = {
  usuario: { id: 7, nome: 'Operador Inicial', email: 'operador@powergrid.local', perfil: 'OPERADOR' },
  carregando: false,
  login: vi.fn(),
  logout: vi.fn(),
}

const alerta = (dados: Partial<AlertaSobrecarga>): AlertaSobrecarga => ({
  id: 1,
  ativoId: 30,
  medicaoId: 900,
  cargaPercentual: 108,
  limiarPercentual: 100,
  status: 'ATIVO',
  geradoEm: '2026-11-03T12:00:00Z',
  reconhecidoEm: null,
  reconhecidoPorUsuarioId: null,
  ...dados,
})

function pagina(content: AlertaSobrecarga[], extras: Partial<PageResponse<AlertaSobrecarga>> = {}): PageResponse<AlertaSobrecarga> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true, ...extras }
}

function renderizar() {
  render(
    <AuthContext.Provider value={auth}>
      <AlertasSobrecarga ativos={[{ id: 30, nome: 'T-01', tipo: 'Transformador' }]} />
    </AuthContext.Provider>
  )
}

async function itens() {
  return within(await screen.findByRole('list', { name: 'Alertas de sobrecarga' })).findAllByRole('listitem')
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alertasApi.listarAlertasSobrecarga).mockResolvedValue(
    pagina([
      alerta({}),
      alerta({ id: 2, ativoId: 31, cargaPercentual: 100.000001, status: 'RECONHECIDO', reconhecidoPorUsuarioId: 9, reconhecidoEm: '2026-11-03T12:30:00Z' }),
    ])
  )
})

describe('alertas de sobrecarga', () => {
  it('mostra transformador, carga sem limite, limiar e medicao de origem', async () => {
    renderizar()

    const [primeiro, segundo] = await itens()
    expect(primeiro).toHaveTextContent('T-01 com carga de 108,0%')
    expect(primeiro).toHaveTextContent('Limiar 100,0%')
    expect(primeiro).toHaveTextContent('Medição #900')
    expect(segundo).toHaveTextContent('Transformador #31 com carga de 100,0%')
    expect(segundo).toHaveTextContent('Reconhecido por usuário #9')
    expect(alertasApi.listarAlertasSobrecarga).toHaveBeenCalledWith(0, undefined)
  })

  it('reconhece com a sessao atual e remove o botao', async () => {
    vi.mocked(alertasApi.reconhecerAlertaSobrecarga).mockResolvedValue(
      alerta({ status: 'RECONHECIDO', reconhecidoPorUsuarioId: 7, reconhecidoEm: '2026-11-03T13:00:00Z' })
    )

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Reconhecer alerta de T-01' }))

    const [primeiro] = await itens()
    expect(await within(primeiro).findByText(/Reconhecido por você/)).toBeInTheDocument()
    expect(within(primeiro).queryByRole('button')).not.toBeInTheDocument()
    expect(alertasApi.reconhecerAlertaSobrecarga).toHaveBeenCalledWith(1)
  })

  it('exibe falha do reconhecimento sem perder a lista', async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    vi.mocked(alertasApi.reconhecerAlertaSobrecarga).mockRejectedValue(
      new AxiosError('erro', undefined, config, undefined, {
        config,
        status: 404,
        data: { message: 'Alerta de sobrecarga não encontrado.' },
        statusText: '',
        headers: {},
      })
    )

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Reconhecer alerta de T-01' }))

    expect(await screen.findByText('Alerta de sobrecarga não encontrado.')).toBeInTheDocument()
    expect(await itens()).toHaveLength(2)
  })

  it('filtra pelo status no servidor e reinicia a pagina', async () => {
    renderizar()
    await itens()

    vi.mocked(alertasApi.listarAlertasSobrecarga).mockResolvedValue(pagina([]))
    await userEvent.click(screen.getByRole('button', { name: 'Não reconhecidos' }))

    expect(await screen.findByText('Nenhum alerta neste filtro.')).toBeInTheDocument()
    expect(alertasApi.listarAlertasSobrecarga).toHaveBeenLastCalledWith(0, 'ATIVO')
  })

  it('acrescenta a proxima pagina ao carregar mais', async () => {
    vi.mocked(alertasApi.listarAlertasSobrecarga)
      .mockResolvedValueOnce(pagina([alerta({})], { totalElements: 2, totalPages: 2, last: false }))
      .mockResolvedValueOnce(pagina([alerta({ id: 2, cargaPercentual: 120 })], { page: 1, first: false }))

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Carregar mais' }))

    await screen.findByText(/120,0%/)
    expect(await itens()).toHaveLength(2)
    expect(alertasApi.listarAlertasSobrecarga).toHaveBeenLastCalledWith(1, undefined)
    expect(screen.queryByRole('button', { name: 'Carregar mais' })).not.toBeInTheDocument()
  })
})
