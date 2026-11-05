import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alertasApi from '../../services/alertas'
import type { Alerta, AlertaFila, FiltrosFilaAlertas, PageResponse, PerfilAcesso } from '../../types'
import FilaAlertasAtivos from './FilaAlertasAtivos'

vi.mock('../../services/alertas')

const item = (dados: Partial<AlertaFila>): AlertaFila => ({
  idOrigem: 1,
  tipoAlerta: 'GENERICO',
  criticidade: 'CRITICA',
  escopo: 'ATIVO',
  ativoId: 30,
  fonteId: null,
  medicaoId: null,
  ocorridoEm: '2026-11-03T12:00:00Z',
  statusOrigem: 'ABERTO',
  subtipo: null,
  mensagem: 'Inspeção pendente',
  reconhecidoEm: null,
  reconhecidoPorUsuarioId: null,
  restabelecidoEm: null,
  descricao: 'Inspeção pendente',
  resolvidoEm: null,
  resolvidoPorUsuarioId: null,
  tipoAtivo: 'TRANSFORMADOR',
  ...dados,
})

const fila = [
  item({ idOrigem: 7, tipoAlerta: 'SOBRECARGA', mensagem: null, descricao: 'Sobrecarga de 112% (limiar: 100%)', statusOrigem: 'ATIVO' }),
  item({}),
  item({ idOrigem: 3, tipoAlerta: 'TENSAO', criticidade: 'ALTA', ativoId: 55, tipoAtivo: 'LINHA', mensagem: null, descricao: 'Subtensão: 120 kV', statusOrigem: 'ATIVO' }),
  item({
    idOrigem: 2,
    criticidade: 'MEDIA',
    escopo: 'REDE_MONITORADA',
    ativoId: null,
    tipoAtivo: null,
    mensagem: 'Oscilação de frequência',
    descricao: 'Oscilação de frequência',
    statusOrigem: 'RECONHECIDO',
    reconhecidoEm: '2026-11-03T12:10:00Z',
    reconhecidoPorUsuarioId: 7,
  }),
  item({ idOrigem: 1, tipoAlerta: 'COMUNICACAO', criticidade: null, ativoId: 999, tipoAtivo: null, fonteId: 4, mensagem: null, descricao: 'Perda de comunicação na fonte 4 (tolerância: 300 s)', statusOrigem: 'ATIVO' }),
]

function pagina(content: AlertaFila[], extras: Partial<PageResponse<AlertaFila>> = {}): PageResponse<AlertaFila> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true, ...extras }
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

const ativos = [
  { id: 30, nome: 'T-01', tipo: 'Transformador' as const },
  { id: 10, nome: 'SE Norte', tipo: 'Subestação' as const },
]

let conteudo: AlertaFila[]

function auth(perfil: PerfilAcesso): AuthContextValue {
  return {
    usuario: { id: 7, nome: 'Usuário de teste', email: 'teste@powergrid.local', perfil },
    carregando: false,
    login: vi.fn(),
    logout: vi.fn(),
  }
}

function renderizar(perfil: PerfilAcesso = 'OPERADOR') {
  return render(
    <AuthContext.Provider value={auth(perfil)}>
      <FilaAlertasAtivos ativos={ativos} versao={0} />
    </AuthContext.Provider>
  )
}

async function itens() {
  return within(await screen.findByRole('list', { name: 'Alertas ativos' })).findAllByRole('listitem')
}

// As chamadas com size 1 são as contagens por criticidade
const chamadasPrincipais = () => vi.mocked(alertasApi.listarFilaAlertas).mock.calls.filter((c) => c[2] === undefined)

beforeEach(() => {
  vi.resetAllMocks()
  conteudo = [...fila]
  vi.mocked(alertasApi.listarFilaAlertas).mockImplementation(async (_page: number, filtros: FiltrosFilaAlertas, size?: number) => {
    if (size === 1) return pagina([], { totalElements: conteudo.filter((a) => a.criticidade === filtros.criticidade).length })
    return pagina(conteudo)
  })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('fila global de alertas ativos', () => {
  it('une as quatro origens na ordem do backend com origem, afetado e criticidade', async () => {
    renderizar()
    const [sobrecarga, generico, tensao, rede, comunicacao] = await itens()

    expect(sobrecarga).toHaveTextContent('Sobrecarga de 112% (limiar: 100%)')
    expect(sobrecarga).toHaveTextContent('Sobrecarga • Transformador · T-01')
    expect(generico).toHaveTextContent('Registrado • Transformador · T-01')
    expect(tensao).toHaveTextContent('Linha #55')
    expect(within(tensao).getByText('Alta')).toHaveClass('text-orange-400')
    expect(rede).toHaveTextContent('Rede monitorada')
    expect(rede).toHaveTextContent('Reconhecido por você em')
    expect(comunicacao).toHaveTextContent('Sem criticidade')
    expect(comunicacao).toHaveTextContent('Ativo #999')
  })

  it('conta por criticidade com os totais do backend', async () => {
    renderizar()
    await itens()

    const critica = screen.getByRole('button', { name: /Crítica/ })
    await vi.waitFor(() => expect(critica).toHaveTextContent('2'))
    expect(screen.getByRole('button', { name: /Baixa/ })).toHaveTextContent('0')
  })

  it('envia os filtros ao servidor', async () => {
    renderizar()
    await itens()

    fireEvent.change(screen.getByLabelText('Origem'), { target: { value: 'TENSAO' } })
    fireEvent.change(screen.getByLabelText('Subestação'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Tipo de ativo'), { target: { value: 'LINHA' } })
    await userEvent.click(screen.getByRole('button', { name: /Alta/ }))

    const esperado = { tipoAlerta: 'TENSAO', subestacaoId: 10, tipoAtivo: 'LINHA', criticidade: 'ALTA' }
    await vi.waitFor(() => expect(chamadasPrincipais().at(-1)).toEqual([0, expect.objectContaining(esperado)]))
  })

  it('recusa periodo invertido sem consultar', async () => {
    renderizar()
    await itens()
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-11-05' } })
    await vi.waitFor(() => expect(chamadasPrincipais()).toHaveLength(2))
    const antes = chamadasPrincipais().length

    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-11-01' } })

    expect(screen.getByText('A data final deve ser igual ou posterior à inicial.')).toBeInTheDocument()
    expect(chamadasPrincipais()).toHaveLength(antes)
  })

  it('diferencia fila vazia de filtro sem resultado', async () => {
    conteudo = []
    renderizar()
    expect(await screen.findByText('Nenhum alerta ativo no momento.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Origem'), { target: { value: 'TENSAO' } })
    expect(await screen.findByText('Nenhum alerta corresponde ao filtro.')).toBeInTheDocument()
  })

  it('atualiza a cada 30 segundos sem esconder a lista', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    renderizar()
    await itens()
    const antes = chamadasPrincipais().length

    conteudo = fila.slice(1)
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })

    expect(chamadasPrincipais()).toHaveLength(antes + 1)
    expect(screen.queryByText('Carregando alertas...')).not.toBeInTheDocument()
    await vi.waitFor(async () => expect(await itens()).toHaveLength(4))
  })

  it('consulta novamente quando a pagina registra um alerta', async () => {
    const { rerender } = renderizar()
    await itens()
    const antes = chamadasPrincipais().length

    rerender(
      <AuthContext.Provider value={auth('OPERADOR')}>
        <FilaAlertasAtivos ativos={ativos} versao={1} />
      </AuthContext.Provider>
    )

    await vi.waitFor(() => expect(chamadasPrincipais()).toHaveLength(antes + 1))
  })
})

describe('acoes por origem', () => {
  it('reconhece sobrecarga sem comentario e atualiza a fila', async () => {
    vi.mocked(alertasApi.reconhecerAlertaSobrecarga).mockResolvedValue({} as never)
    renderizar()
    const [sobrecarga] = await itens()
    const antes = chamadasPrincipais().length

    await userEvent.click(within(sobrecarga).getByRole('button', { name: 'Reconhecer Sobrecarga de 112% (limiar: 100%)' }))

    expect(alertasApi.reconhecerAlertaSobrecarga).toHaveBeenCalledWith(7)
    await vi.waitFor(() => expect(chamadasPrincipais()).toHaveLength(antes + 1))
  })

  it('exibe a recusa do reconhecimento automatico no item', async () => {
    vi.mocked(alertasApi.reconhecerAlertaTensao).mockRejectedValue(erroHttp(403, 'Acesso negado.'))
    renderizar()
    const [, , tensao] = await itens()

    await userEvent.click(within(tensao).getByRole('button', { name: 'Reconhecer Subtensão: 120 kV' }))

    expect(await within(tensao).findByText('Acesso negado.')).toBeInTheDocument()
    expect(alertasApi.reconhecerAlertaTensao).toHaveBeenCalledWith(3)
  })

  it('comunicacao nao tem reconhecimento e explica a saida da fila', async () => {
    renderizar()
    const [, , , , comunicacao] = await itens()

    expect(within(comunicacao).queryByRole('button')).not.toBeInTheDocument()
    expect(comunicacao).toHaveTextContent('Sai da fila quando a comunicação voltar')
  })

  it('oferece a tratativa do registrado conforme a situacao', async () => {
    renderizar()
    const [, generico, , rede] = await itens()

    expect(within(generico).getByRole('button', { name: 'Reconhecer Inspeção pendente' })).toBeInTheDocument()
    expect(within(rede).getByRole('button', { name: 'Resolver Oscilação de frequência' })).toBeInTheDocument()
  })

  it('tecnico apenas consulta as tratativas do registrado', async () => {
    renderizar('TECNICO_MANUTENCAO')
    const [, generico] = await itens()

    expect(within(generico).queryByRole('button', { name: /Reconhecer|Resolver/ })).not.toBeInTheDocument()
    expect(within(generico).getByRole('button', { name: 'Tratativas de Inspeção pendente' })).toBeInTheDocument()
  })

  it('exige comentario, reconhece o registrado e consulta a fila novamente', async () => {
    vi.mocked(alertasApi.reconhecerAlerta).mockResolvedValue({ id: 1, situacao: 'RECONHECIDO' } as Alerta)
    renderizar()
    const [, generico] = await itens()
    const antes = chamadasPrincipais().length

    await userEvent.click(within(generico).getByRole('button', { name: 'Reconhecer Inspeção pendente' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Campo obrigatório.')).toBeInTheDocument()
    expect(alertasApi.reconhecerAlerta).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Comentário'), { target: { value: '  Equipe acionada ' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(alertasApi.reconhecerAlerta).toHaveBeenCalledWith(1, 'Equipe acionada')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    await vi.waitFor(() => expect(chamadasPrincipais()).toHaveLength(antes + 1))
  })

  it('mantem a recusa de estado visivel enquanto a fila recarrega sem o alerta', async () => {
    vi.mocked(alertasApi.resolverAlerta).mockRejectedValue(erroHttp(409, 'Transição de alerta não permitida.'))
    renderizar()
    const [, , , rede] = await itens()

    await userEvent.click(within(rede).getByRole('button', { name: 'Resolver Oscilação de frequência' }))
    fireEvent.change(screen.getByLabelText('Comentário'), { target: { value: 'Tentativa' } })
    conteudo = fila.filter((a) => a.descricao !== 'Oscilação de frequência')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Transição de alerta não permitida.')).toBeInTheDocument()
    await vi.waitFor(async () => expect(await itens()).toHaveLength(4))
    expect(screen.getByRole('dialog', { name: 'Resolver alerta' })).toBeInTheDocument()
  })

  it('abre o historico de tratativas pelo id de origem', async () => {
    vi.mocked(alertasApi.buscarAlerta).mockResolvedValue({
      id: 2,
      situacao: 'RECONHECIDO',
      criticidade: 'MEDIA',
      geradoEm: '2026-11-03T12:00:00Z',
      reconhecidoEm: '2026-11-03T12:10:00Z',
      reconhecidoPorUsuarioId: 4,
      resolvidoEm: null,
      resolvidoPorUsuarioId: null,
    } as Alerta)
    vi.mocked(alertasApi.listarTratativasAlerta).mockResolvedValue({
      content: [{ id: 10, alertaId: 2, usuarioId: 4, situacaoAnterior: 'ABERTO', situacaoNova: 'RECONHECIDO', alteradoEm: '2026-11-03T12:10:00Z', comentario: 'Em análise' }],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    })
    renderizar('TECNICO_MANUTENCAO')
    const [, , , rede] = await itens()

    await userEvent.click(within(rede).getByRole('button', { name: 'Tratativas de Oscilação de frequência' }))

    const dialogo = await screen.findByRole('dialog', { name: 'Tratativa: Oscilação de frequência' })
    expect(await within(dialogo).findByText(/Reconhecido por usuário #4 em/)).toBeInTheDocument()
    expect(within(dialogo).getByRole('list', { name: 'Tratativas do alerta' })).toHaveTextContent('Em análise')
    expect(alertasApi.listarTratativasAlerta).toHaveBeenCalledWith(2, 0)
  })
})
