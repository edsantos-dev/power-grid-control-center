import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alertasApi from '../../services/alertas'
import type { AlertaTensao, PageResponse } from '../../types'
import AlertasTensao from './AlertasTensao'

vi.mock('../../services/alertas')

const auth: AuthContextValue = {
  usuario: { id: 7, nome: 'Operador Inicial', email: 'operador@powergrid.local', perfil: 'OPERADOR' },
  carregando: false,
  login: vi.fn(),
  logout: vi.fn(),
}

const alerta = (dados: Partial<AlertaTensao>): AlertaTensao => ({
  id: 1,
  ativoId: 40,
  medicaoId: 700,
  tipo: 'SUBTENSAO',
  tensaoMedidaKv: 131.1,
  tensaoReferenciaKv: 138,
  limiteInferiorKv: 131.1,
  limiteSuperiorKv: 144.9,
  status: 'ATIVO',
  geradoEm: '2026-11-03T12:00:00Z',
  reconhecidoEm: null,
  reconhecidoPorUsuarioId: null,
  ...dados,
})

function pagina(content: AlertaTensao[]): PageResponse<AlertaTensao> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true }
}

function renderizar() {
  render(
    <AuthContext.Provider value={auth}>
      <AlertasTensao ativos={[{ id: 40, nome: 'LT Norte-Sul', tipo: 'Linha' }]} />
    </AuthContext.Provider>
  )
}

async function itens() {
  return within(await screen.findByRole('list', { name: 'Alertas de tensão' })).findAllByRole('listitem')
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alertasApi.listarAlertasTensao).mockResolvedValue(
    pagina([alerta({}), alerta({ id: 2, tipo: 'SOBRETENSAO', tensaoMedidaKv: 150, ativoId: 41 })])
  )
})

describe('alertas de tensao', () => {
  it('mostra tipo, linha, tensao medida e a faixa registrada no alerta', async () => {
    renderizar()

    const [sub, sobre] = await itens()
    expect(sub).toHaveTextContent('Subtensão em LT Norte-Sul: 131,1 kV')
    expect(sub).toHaveTextContent('Referência 138 kV')
    expect(sub).toHaveTextContent('acima de 131,1 kV e abaixo de 144,9 kV')
    expect(sub).toHaveTextContent('Medição #700')
    expect(sobre).toHaveTextContent('Sobretensão em Linha #41: 150 kV')
  })

  it('filtra por tipo e status no servidor', async () => {
    renderizar()
    await itens()

    await userEvent.click(screen.getByRole('button', { name: 'Sobretensão' }))
    expect(alertasApi.listarAlertasTensao).toHaveBeenLastCalledWith(0, undefined, 'SOBRETENSAO')

    await userEvent.click(screen.getByRole('button', { name: 'Reconhecidos' }))
    expect(alertasApi.listarAlertasTensao).toHaveBeenLastCalledWith(0, 'RECONHECIDO', 'SOBRETENSAO')

    await userEvent.click(screen.getByRole('button', { name: 'Todos os tipos' }))
    expect(alertasApi.listarAlertasTensao).toHaveBeenLastCalledWith(0, 'RECONHECIDO', undefined)
  })

  it('reconhece com a sessao atual', async () => {
    vi.mocked(alertasApi.reconhecerAlertaTensao).mockResolvedValue(
      alerta({ status: 'RECONHECIDO', reconhecidoPorUsuarioId: 7, reconhecidoEm: '2026-11-03T13:00:00Z' })
    )

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Reconhecer alerta de LT Norte-Sul' }))

    const [primeiro] = await itens()
    expect(await within(primeiro).findByText(/Reconhecido por você/)).toBeInTheDocument()
    expect(alertasApi.reconhecerAlertaTensao).toHaveBeenCalledWith(1)
  })

  it('remove o alerta reconhecido quando o filtro mostra apenas nao reconhecidos', async () => {
    vi.mocked(alertasApi.reconhecerAlertaTensao).mockResolvedValue(alerta({ status: 'RECONHECIDO', reconhecidoPorUsuarioId: 7 }))

    renderizar()
    await itens()
    await userEvent.click(screen.getByRole('button', { name: 'Não reconhecidos' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Reconhecer alerta de LT Norte-Sul' }))

    const restantes = await itens()
    expect(restantes).toHaveLength(1)
    expect(restantes[0]).toHaveTextContent('Sobretensão')
    expect(screen.getByText('1 alerta(s) neste filtro')).toBeInTheDocument()
  })
})
