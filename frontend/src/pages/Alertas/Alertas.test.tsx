import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alertasApi from '../../services/alertas'
import * as ativosApi from '../../services/ativos'
import type { Alerta, PerfilAcesso } from '../../types'
import Alertas from '.'

vi.mock('../../services/alertas')
vi.mock('../../services/ativos')
vi.mock('./AlertasSobrecarga', () => ({ default: () => null }))
vi.mock('./AlertasTensao', () => ({ default: () => null }))
vi.mock('./AlertasComunicacao', () => ({ default: () => null }))
vi.mock('./HistoricoAlertas', () => ({ default: () => <p>Histórico simulado</p> }))

const alerta = (dados: Partial<Alerta>): Alerta => ({
  id: 1,
  tipo: 'Sobrecarga no T-01',
  criticidade: 'CRITICA',
  escopo: 'ATIVO',
  situacao: 'ABERTO',
  geradoEm: '2026-11-03T12:00:00Z',
  ativoId: 30,
  reconhecidoEm: null,
  reconhecidoPorUsuarioId: null,
  resolvidoEm: null,
  resolvidoPorUsuarioId: null,
  ...dados,
})

function renderizar(perfil: PerfilAcesso = 'OPERADOR') {
  const auth: AuthContextValue = {
    usuario: { id: 7, nome: 'Usuário de teste', email: 'teste@powergrid.local', perfil },
    carregando: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  render(
    <AuthContext.Provider value={auth}>
      <Alertas />
    </AuthContext.Provider>
  )
}

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, {
    config,
    status,
    data: { message, fieldErrors: [] },
    statusText: '',
    headers: {},
  })
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

async function itens() {
  return within(await screen.findByRole('list', { name: 'Alertas ativos' })).findAllByRole('listitem')
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alertasApi.listarAlertasAtivos).mockResolvedValue([
    alerta({}),
    alerta({ id: 2, tipo: 'Oscilação de frequência', criticidade: 'ALTA', escopo: 'REDE_MONITORADA', ativoId: null, situacao: 'RECONHECIDO' }),
    alerta({ id: 3, tipo: 'Inspeção pendente', criticidade: 'BAIXA', ativoId: 999 }),
  ])
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 30, nome: 'T-01', tipo: 'Transformador' }])
})

describe('alertas ativos', () => {
  it('mantem a ordem do backend e indica criticidade e situacao', async () => {
    renderizar()

    const [primeiro, segundo, terceiro] = await itens()
    expect(primeiro).toHaveTextContent('Sobrecarga no T-01')
    expect(within(primeiro).getByText('Crítica')).toHaveClass('text-red-400')
    expect(within(primeiro).getByText('Aberto')).toBeInTheDocument()
    expect(segundo).toHaveTextContent('Oscilação de frequência')
    expect(within(segundo).getByText('Reconhecido')).toBeInTheDocument()
    expect(terceiro).toHaveTextContent('Inspeção pendente')
  })

  it('mostra o ativo pelo catalogo, o escopo de rede e um fallback sem inventar nome', async () => {
    renderizar()

    const [primeiro, segundo, terceiro] = await itens()
    expect(primeiro).toHaveTextContent('Transformador · T-01')
    expect(segundo).toHaveTextContent('Rede monitorada')
    expect(terceiro).toHaveTextContent('Ativo #999')
  })

  it('conta por criticidade e filtra sem reordenar', async () => {
    renderizar()
    await itens()

    await userEvent.click(screen.getByRole('button', { name: 'Alta' }))

    const lista = await itens()
    expect(lista).toHaveLength(1)
    expect(lista[0]).toHaveTextContent('Oscilação de frequência')
  })

  it('exibe mensagem apropriada sem alertas ativos', async () => {
    vi.mocked(alertasApi.listarAlertasAtivos).mockResolvedValue([])

    renderizar()

    expect(await screen.findByText('Nenhum alerta ativo no momento.')).toBeInTheDocument()
  })

  it('diferencia filtro vazio de ausencia de alertas', async () => {
    renderizar()
    await itens()

    await userEvent.click(screen.getByRole('button', { name: 'Média' }))

    expect(screen.getByText('Nenhum alerta corresponde ao filtro.')).toBeInTheDocument()
    expect(screen.queryByText('Nenhum alerta ativo no momento.')).not.toBeInTheDocument()
  })
})

describe('registro de alerta', () => {
  it('exige ativo no escopo ATIVO', async () => {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar alerta' }))
    preencher('Tipo', 'Teste')
    preencher('Criticidade', 'MEDIA')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getByText('Selecione o ativo afetado.')).toBeInTheDocument()
    expect(alertasApi.criarAlerta).not.toHaveBeenCalled()
  })

  it('envia ativoId nulo para a rede monitorada e recarrega a lista', async () => {
    vi.mocked(alertasApi.criarAlerta).mockResolvedValue(alerta({ id: 4 }))

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar alerta' }))
    preencher('Tipo', '  Queda de frequência ')
    preencher('Criticidade', 'CRITICA')
    preencher('Escopo', 'REDE_MONITORADA')
    expect(screen.queryByLabelText('Ativo afetado')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(alertasApi.criarAlerta).toHaveBeenCalledWith({
      tipo: 'Queda de frequência',
      criticidade: 'CRITICA',
      escopo: 'REDE_MONITORADA',
      ativoId: null,
    })
    await screen.findAllByRole('listitem')
    expect(alertasApi.listarAlertasAtivos).toHaveBeenCalledTimes(2)
  })

  it('envia o ativo selecionado e exibe recusa do servidor', async () => {
    vi.mocked(alertasApi.criarAlerta).mockRejectedValue(erroHttp(404, 'Ativo não encontrado.'))

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar alerta' }))
    preencher('Tipo', 'Sobrecarga')
    preencher('Criticidade', 'ALTA')
    preencher('Ativo afetado', '30')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Ativo não encontrado.')).toBeInTheDocument()
    expect(alertasApi.criarAlerta).toHaveBeenCalledWith({ tipo: 'Sobrecarga', criticidade: 'ALTA', escopo: 'ATIVO', ativoId: 30 })
  })
})

describe('tratativa de alertas', () => {
  it('oferece a proxima etapa ao operador', async () => {
    renderizar()
    const [aberto, reconhecido] = await itens()
    expect(within(aberto).getByRole('button', { name: 'Reconhecer Sobrecarga no T-01' })).toBeInTheDocument()
    expect(within(reconhecido).getByRole('button', { name: 'Resolver Oscilação de frequência' })).toBeInTheDocument()
  })

  it('nao oferece reconhecimento nem resolucao ao tecnico', async () => {
    renderizar('TECNICO_MANUTENCAO')
    const [aberto] = await itens()

    expect(within(aberto).queryByRole('button', { name: /Reconhecer/ })).not.toBeInTheDocument()
    expect(within(aberto).getByRole('button', { name: 'Tratativas de Sobrecarga no T-01' })).toBeInTheDocument()
  })

  it('exige comentario e reconhece mantendo o alerta ativo com a autoria', async () => {
    vi.mocked(alertasApi.reconhecerAlerta).mockResolvedValue(
      alerta({ situacao: 'RECONHECIDO', reconhecidoEm: '2026-11-03T12:30:00Z', reconhecidoPorUsuarioId: 7 })
    )

    renderizar()
    const [aberto] = await itens()
    await userEvent.click(within(aberto).getByRole('button', { name: 'Reconhecer Sobrecarga no T-01' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Campo obrigatório.')).toBeInTheDocument()
    expect(alertasApi.reconhecerAlerta).not.toHaveBeenCalled()

    preencher('Comentário', '  Equipe acionada ')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(alertasApi.reconhecerAlerta).toHaveBeenCalledWith(1, 'Equipe acionada')
    const [atualizado] = await itens()
    expect(atualizado).toHaveTextContent(/Reconhecido por você em/)
    expect(within(atualizado).getByRole('button', { name: 'Resolver Sobrecarga no T-01' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('remove o alerta resolvido da lista de ativos', async () => {
    vi.mocked(alertasApi.resolverAlerta).mockResolvedValue(alerta({ id: 2, situacao: 'RESOLVIDO' }))

    renderizar()
    const [, reconhecido] = await itens()
    await userEvent.click(within(reconhecido).getByRole('button', { name: 'Resolver Oscilação de frequência' }))
    preencher('Comentário', 'Frequência normalizada')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(alertasApi.resolverAlerta).toHaveBeenCalledWith(2, 'Frequência normalizada')
    await vi.waitFor(async () => expect(await itens()).toHaveLength(2))
    expect(screen.queryByText('Oscilação de frequência')).not.toBeInTheDocument()
  })

  it('mantem a recusa de estado visivel enquanto a lista recarrega sem o alerta', async () => {
    vi.mocked(alertasApi.resolverAlerta).mockRejectedValue(erroHttp(409, 'O alerta precisa estar RECONHECIDO para ser resolvido.'))

    renderizar()
    const [, reconhecido] = await itens()
    await userEvent.click(within(reconhecido).getByRole('button', { name: 'Resolver Oscilação de frequência' }))
    preencher('Comentário', 'Tentativa')
    vi.mocked(alertasApi.listarAlertasAtivos).mockResolvedValue([alerta({})])
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('O alerta precisa estar RECONHECIDO para ser resolvido.')).toBeInTheDocument()
    await vi.waitFor(async () => expect(await itens()).toHaveLength(1))
    expect(screen.getByRole('dialog', { name: 'Resolver alerta' })).toHaveTextContent('O alerta precisa estar RECONHECIDO para ser resolvido.')
    expect(alertasApi.listarAlertasAtivos).toHaveBeenCalledTimes(2)
  })

  it('mostra a autoria consolidada e as tratativas em ordem do backend', async () => {
    vi.mocked(alertasApi.buscarAlerta).mockResolvedValue(
      alerta({ id: 2, situacao: 'RECONHECIDO', reconhecidoEm: '2026-11-03T12:30:00Z', reconhecidoPorUsuarioId: 4 })
    )
    vi.mocked(alertasApi.listarTratativasAlerta).mockResolvedValue({
      content: [
        { id: 10, alertaId: 2, usuarioId: 4, situacaoAnterior: 'ABERTO', situacaoNova: 'RECONHECIDO', alteradoEm: '2026-11-03T12:30:00Z', comentario: 'Em análise' },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    })

    renderizar('TECNICO_MANUTENCAO')
    const [, reconhecido] = await itens()
    await userEvent.click(within(reconhecido).getByRole('button', { name: 'Tratativas de Oscilação de frequência' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Tratativa: Oscilação de frequência' })
    expect(await within(dialogo).findByText(/Reconhecido por usuário #4 em/)).toBeInTheDocument()
    const [registro] = within(within(dialogo).getByRole('list', { name: 'Tratativas do alerta' })).getAllByRole('listitem')
    expect(registro).toHaveTextContent('Aberto → Reconhecido')
    expect(registro).toHaveTextContent('Em análise')
    expect(alertasApi.listarTratativasAlerta).toHaveBeenCalledWith(2, 0)
  })
})
