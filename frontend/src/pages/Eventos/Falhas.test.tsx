import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as ativosApi from '../../services/ativos'
import * as equipesApi from '../../services/equipes'
import * as eventosApi from '../../services/eventos'
import type { EventoOperacional, FalhaEquipamento, PageResponse, PerfilAcesso } from '../../types'
import Eventos from '.'

vi.mock('../../services/eventos')
vi.mock('../../services/ativos')
vi.mock('../../services/equipes')

const evento = (dados: Partial<EventoOperacional>): EventoOperacional => ({
  id: 1,
  ativoId: 30,
  categoria: 'FALHA_EQUIPAMENTO',
  descricao: 'Disjuntor não abre',
  criticidade: 'ALTA',
  situacao: 'EM_ANDAMENTO',
  registradorUsuarioId: 7,
  responsavelUsuarioId: 7,
  inicioPrevisto: null,
  terminoPrevisto: null,
  inicioEfetivo: '2026-11-03T12:00:00Z',
  terminoEfetivo: null,
  equipeDesignadaId: null,
  versaoDesignacao: 0,
  estadoAposReparo: null,
  ...dados,
})

const falha = (dados: Partial<FalhaEquipamento> = {}, dadosEvento: Partial<EventoOperacional> = {}): FalhaEquipamento => ({
  id: 1,
  evento: evento(dadosEvento),
  estadoAnterior: 'OPERACIONAL',
  registradoEm: '2026-11-03T11:50:00Z',
  estadoAposReparo: null,
  estadoOperacionalAtual: 'FALHA',
  ...dados,
})

const pagina = <T,>(content: T[]): PageResponse<T> => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true })

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, { config, status, data: { message, fieldErrors: [] }, statusText: '', headers: {} })
}

function renderizar(perfil: PerfilAcesso = 'OPERADOR') {
  const auth: AuthContextValue = {
    usuario: { id: 7, nome: 'Usuário de teste', email: 'teste@powergrid.local', perfil },
    carregando: false,
    login: vi.fn(),
    logout: vi.fn(),
  }

  render(
    <AuthContext.Provider value={auth}>
      <Eventos />
    </AuthContext.Provider>
  )
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

const linha = async (descricao: string) => (await screen.findByText(descricao, { selector: 'td p' })).closest('tr') as HTMLElement

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 30, nome: 'T-01', tipo: 'Transformador' }])
  vi.mocked(equipesApi.listarTodasEquipes).mockResolvedValue([])
  vi.mocked(eventosApi.listarEventos).mockResolvedValue([evento({})])
  vi.mocked(eventosApi.listarTransicoes).mockResolvedValue(pagina([]))
  vi.mocked(eventosApi.listarDesignacoes).mockResolvedValue(pagina([]))
  vi.mocked(eventosApi.listarFalhas).mockResolvedValue(pagina([falha()]))
})

describe('registro de falha', () => {
  it('fica disponivel apenas a gerente e operador', async () => {
    renderizar('TECNICO_MANUTENCAO')
    await linha('Disjuntor não abre')

    expect(screen.queryByRole('button', { name: 'Registrar falha' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Falhas de equipamento' })).toBeInTheDocument()
  })

  it('exige ativo e descricao e envia a criticidade do dominio', async () => {
    vi.mocked(eventosApi.registrarFalha).mockResolvedValue(falha({}, { id: 2, descricao: 'Transformador aquecido', situacao: 'ABERTO', inicioEfetivo: null }))
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar falha' }))

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(2)
    expect(eventosApi.registrarFalha).not.toHaveBeenCalled()

    await vi.waitFor(() => expect(screen.getByRole('option', { name: 'Transformador · T-01' })).toBeInTheDocument())
    preencher('Ativo com falha', '30')
    preencher('Descrição', '  Transformador aquecido ')
    preencher('Criticidade (opcional)', 'CRITICA')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(eventosApi.registrarFalha).toHaveBeenCalledWith({ ativoId: 30, responsavelUsuarioId: 7, descricao: 'Transformador aquecido', criticidade: 'CRITICA' })
    const nova = await linha('Transformador aquecido')
    expect(nova).toHaveTextContent('Falha de equipamento')
    expect(nova).toHaveTextContent('Aberto')
  })

  it('exibe a recusa de falha duplicada no formulario', async () => {
    vi.mocked(eventosApi.registrarFalha).mockRejectedValue(erroHttp(409, 'Já existe falha não concluída para este ativo.'))
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar falha' }))
    await vi.waitFor(() => expect(screen.getByRole('option', { name: 'Transformador · T-01' })).toBeInTheDocument())
    preencher('Ativo com falha', '30')
    preencher('Descrição', 'Nova falha')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Já existe falha não concluída para este ativo.')).toBeInTheDocument()
    expect(eventosApi.registrarFalha).toHaveBeenCalledWith(expect.objectContaining({ criticidade: null }))
  })
})

describe('conclusao de falha', () => {
  it('exige o estado do ativo apos o reparo e o envia na transicao', async () => {
    vi.mocked(eventosApi.transicionarEvento).mockResolvedValue({} as never)
    vi.mocked(eventosApi.buscarEvento).mockResolvedValue(evento({ situacao: 'CONCLUIDO', terminoEfetivo: '2026-11-03T13:00:00Z', estadoAposReparo: 'MANUTENCAO' }))
    renderizar()
    await userEvent.click(within(await linha('Disjuntor não abre')).getByRole('button', { name: 'Concluir Disjuntor não abre' }))

    preencher('Comentário', 'Disjuntor substituído')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Campo obrigatório.')).toBeInTheDocument()
    expect(eventosApi.transicionarEvento).not.toHaveBeenCalled()

    const opcoes = within(screen.getByLabelText('Estado do ativo após o reparo')).getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Selecione', 'Operacional', 'Em manutenção'])
    preencher('Estado do ativo após o reparo', 'MANUTENCAO')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(eventosApi.transicionarEvento).toHaveBeenCalledWith(1, {
      situacaoEsperada: 'EM_ANDAMENTO',
      situacaoDestino: 'CONCLUIDO',
      comentario: 'Disjuntor substituído',
      terminoEfetivo: null,
      estadoAposReparo: 'MANUTENCAO',
    })
    expect(await linha('Disjuntor não abre')).toHaveTextContent('Ativo após o reparo: Manutenção')
  })

  it('nao pede estado apos reparo ao iniciar a falha', async () => {
    vi.mocked(eventosApi.listarEventos).mockResolvedValue([evento({ situacao: 'ABERTO', inicioEfetivo: null })])
    renderizar()
    await userEvent.click(within(await linha('Disjuntor não abre')).getByRole('button', { name: 'Iniciar Disjuntor não abre' }))

    expect(screen.queryByLabelText('Estado do ativo após o reparo')).not.toBeInTheDocument()
  })
})

describe('consulta de falhas', () => {
  it('mostra estados anterior, apos o reparo e atual e filtra no servidor', async () => {
    vi.mocked(eventosApi.listarFalhas).mockResolvedValue(
      pagina([
        falha(),
        falha({ id: 5, estadoAnterior: 'MANUTENCAO', estadoAposReparo: 'OPERACIONAL', estadoOperacionalAtual: 'OPERACIONAL' }, { id: 5, descricao: 'Religador travado', situacao: 'CONCLUIDO' }),
      ])
    )
    renderizar('TECNICO_MANUTENCAO')
    await userEvent.click(await screen.findByRole('button', { name: 'Falhas de equipamento' }))

    const dialogo = screen.getByRole('dialog', { name: 'Falhas de equipamento' })
    const [, aberta, concluida] = await within(dialogo).findAllByRole('row')
    expect(aberta).toHaveTextContent('Transformador · T-01')
    expect(aberta).toHaveTextContent('Em andamento')
    expect(within(aberta).getAllByText(/Operacional|Falha/).map((c) => c.textContent)).toEqual(['Operacional', 'Falha'])
    expect(concluida).toHaveTextContent('Religador travado')
    expect(within(concluida).getAllByText(/Operacional|Manutenção/).map((c) => c.textContent)).toEqual(['Manutenção', 'Operacional', 'Operacional'])

    fireEvent.change(within(dialogo).getByLabelText('Situação'), { target: { value: 'CONCLUIDO' } })
    fireEvent.change(within(dialogo).getByLabelText('Ativo'), { target: { value: '30' } })
    await vi.waitFor(() => expect(eventosApi.listarFalhas).toHaveBeenLastCalledWith(0, { ativoId: 30, situacao: 'CONCLUIDO', dataInicio: undefined, dataFim: undefined }))
  })

  it('abre as transicoes da falha sobre a consulta', async () => {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Falhas de equipamento' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Transições de Disjuntor não abre' }))

    expect(await screen.findByRole('dialog', { name: 'Histórico: Disjuntor não abre' })).toBeInTheDocument()
    expect(eventosApi.listarTransicoes).toHaveBeenCalledWith(1, 0)
  })
})
