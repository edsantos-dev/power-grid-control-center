import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alertasApi from '../../services/alertas'
import type { AlertaComunicacao, PageResponse, PerfilAcesso } from '../../types'
import AlertasComunicacao from './AlertasComunicacao'

vi.mock('../../services/alertas')

const alerta = (dados: Partial<AlertaComunicacao>): AlertaComunicacao => ({
  id: 1,
  fonteId: 5,
  ativoId: 40,
  monitoradaDesde: '2026-11-03T10:00:00Z',
  ultimaComunicacaoEm: '2026-11-03T11:00:00Z',
  toleranciaSegundos: 300,
  detectadoEm: '2026-11-03T11:06:00Z',
  status: 'ATIVO',
  restabelecidoEm: null,
  ...dados,
})

function pagina(content: AlertaComunicacao[]): PageResponse<AlertaComunicacao> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true }
}

function renderizar(perfil: PerfilAcesso = 'OPERADOR') {
  const auth: AuthContextValue = {
    usuario: { id: 7, nome: 'Usuário', email: 'u@powergrid.local', perfil },
    carregando: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  render(
    <AuthContext.Provider value={auth}>
      <AlertasComunicacao ativos={[{ id: 40, nome: 'LT Norte-Sul', tipo: 'Linha' }]} />
    </AuthContext.Provider>
  )
}

async function itens() {
  return within(await screen.findByRole('list', { name: 'Alertas de comunicação' })).findAllByRole('listitem')
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alertasApi.listarAlertasComunicacao).mockResolvedValue(
    pagina([
      alerta({}),
      alerta({ id: 2, ultimaComunicacaoEm: null, status: 'RESTABELECIDO', restabelecidoEm: '2026-11-03T11:10:00Z', ativoId: 99 }),
    ])
  )
})

describe('alertas de comunicacao', () => {
  it('mostra ativo, fonte, ultimo contato e tolerancia, sem acao de reconhecer', async () => {
    renderizar()

    const [ativo, restabelecido] = await itens()
    expect(ativo).toHaveTextContent('Perda de comunicação em Linha · LT Norte-Sul (fonte #5)')
    expect(ativo).toHaveTextContent('tolerância de 300 s')
    expect(within(ativo).getByText('Sem comunicação')).toHaveClass('text-red-400')
    expect(screen.queryByRole('button', { name: /Reconhecer/ })).not.toBeInTheDocument()

    expect(restabelecido).toHaveTextContent('Ativo #99')
    expect(restabelecido).toHaveTextContent('Sem contato desde o início do monitoramento')
    expect(restabelecido).toHaveTextContent('Restabelecida em')
    expect(within(restabelecido).getByText('Restabelecido')).toHaveClass('text-green-400')
  })

  it('filtra pelos status proprios da comunicacao', async () => {
    renderizar()
    await itens()

    await userEvent.click(screen.getByRole('button', { name: 'Restabelecidos' }))

    expect(alertasApi.listarAlertasComunicacao).toHaveBeenLastCalledWith(0, 'RESTABELECIDO')
  })

  it('avalia sob demanda, mostra o resumo e recarrega a lista', async () => {
    vi.mocked(alertasApi.avaliarComunicacao).mockResolvedValue({
      avaliadoEm: '2026-11-03T12:00:00Z',
      toleranciaSegundos: 300,
      fontesAvaliadas: 3,
      alertasCriados: 1,
      alertasAtivosExistentes: 1,
    })

    renderizar('GERENTE_OPERACOES')
    await itens()
    await userEvent.click(screen.getByRole('button', { name: 'Avaliar comunicação agora' }))

    expect(await screen.findByRole('status')).toHaveTextContent('3 fonte(s) avaliada(s), 1 nova(s) perda(s) e 1 já em andamento.')
    expect(alertasApi.listarAlertasComunicacao).toHaveBeenCalledTimes(2)
  })

  it('nao oferece a avaliacao ao tecnico, que o backend nao autoriza', async () => {
    renderizar('TECNICO_MANUTENCAO')
    await itens()

    expect(screen.queryByRole('button', { name: 'Avaliar comunicação agora' })).not.toBeInTheDocument()
  })
})
