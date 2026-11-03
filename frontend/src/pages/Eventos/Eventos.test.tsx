import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as ativosApi from '../../services/ativos'
import * as eventosApi from '../../services/eventos'
import type { EventoOperacional } from '../../types'
import { instanteDeDataHoraLocal } from '../../utils/formatacao'
import Eventos from '.'

vi.mock('../../services/eventos')
vi.mock('../../services/ativos')

const auth: AuthContextValue = {
  usuario: { id: 7, nome: 'Operador Inicial', email: 'operador@powergrid.local', perfil: 'OPERADOR' },
  carregando: false,
  login: vi.fn(),
  logout: vi.fn(),
}

const evento = (dados: Partial<EventoOperacional>): EventoOperacional => ({
  id: 1,
  ativoId: 10,
  categoria: 'MANOBRA',
  descricao: 'Transferência de carga',
  criticidade: null,
  situacao: 'ABERTO',
  registradorUsuarioId: 7,
  responsavelUsuarioId: 7,
  inicioPrevisto: '2026-11-03T12:00:00Z',
  terminoPrevisto: null,
  inicioEfetivo: null,
  terminoEfetivo: null,
  ...dados,
})

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, { config, status, data: { message, fieldErrors: [] }, statusText: '', headers: {} })
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

function renderizar() {
  render(
    <AuthContext.Provider value={auth}>
      <Eventos />
    </AuthContext.Provider>
  )
}

const linhas = async () => (await screen.findAllByRole('row')).slice(1)

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([
    { id: 10, nome: 'AL Centro', tipo: 'Alimentador' },
    { id: 11, nome: 'SE Norte', tipo: 'Subestação' },
  ])
  vi.mocked(eventosApi.listarEventos).mockResolvedValue([
    evento({}),
    evento({ id: 2, categoria: 'DESLIGAMENTO_PROGRAMADO', descricao: 'Desligamento para poda', ativoId: 11, situacao: 'CONCLUIDO', inicioPrevisto: '2026-11-04T08:00:00Z', responsavelUsuarioId: 3, criticidade: 'Alta' }),
  ])
})

describe('listagem de eventos', () => {
  it('mostra data, ativo, categoria, situacao e responsavel, do mais recente ao mais antigo', async () => {
    renderizar()
    await screen.findByText('Desligamento para poda')

    const [primeiro, segundo] = await linhas()
    expect(primeiro).toHaveTextContent('Subestação · SE Norte')
    expect(primeiro).toHaveTextContent('Desligamento programado')
    expect(primeiro).toHaveTextContent('Concluído')
    expect(primeiro).toHaveTextContent('Usuário #3')
    expect(primeiro).toHaveTextContent('Criticidade: Alta')
    expect(segundo).toHaveTextContent('Manobra')
    expect(segundo).toHaveTextContent('Você')
    expect(within(segundo).getByText('Início previsto')).toBeInTheDocument()
  })

  it('filtra por situacao e categoria', async () => {
    renderizar()
    await screen.findByText('Desligamento para poda')

    await userEvent.click(screen.getByRole('button', { name: 'Aberto' }))
    expect(screen.queryByText('Desligamento para poda')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Todas' }))
    preencher('Filtrar por categoria', 'DESLIGAMENTO_PROGRAMADO')
    expect(screen.queryByText('Transferência de carga')).not.toBeInTheDocument()
  })
})

describe('registro de evento programado', () => {
  async function abrir() {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar evento programado' }))
  }

  it('oferece as categorias programadas aceitas pelo backend', async () => {
    await abrir()

    const opcoes = within(screen.getByLabelText('Categoria')).getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Selecione', 'Manobra', 'Desligamento programado', 'Manutenção'])
  })

  it('exige campos e rejeita termino antes do inicio sem chamar a API', async () => {
    await abrir()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(4)

    preencher('Início previsto', '2026-11-05T10:00')
    preencher('Término previsto (opcional)', '2026-11-05T09:00')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getByText('O término previsto não pode ser anterior ao início.')).toBeInTheDocument()
    expect(eventosApi.criarEvento).not.toHaveBeenCalled()
  })

  it('envia o usuario atual como responsavel e instantes em UTC', async () => {
    vi.mocked(eventosApi.criarEvento).mockResolvedValue(evento({ id: 3, descricao: 'Manobra nova' }))

    await abrir()
    preencher('Categoria', 'MANOBRA')
    preencher('Ativo', '10')
    preencher('Descrição', '  Manobra nova ')
    preencher('Início previsto', '2026-11-05T10:00')
    preencher('Término previsto (opcional)', '2026-11-05T10:00')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Manobra nova')).toBeInTheDocument()
    expect(eventosApi.criarEvento).toHaveBeenCalledWith({
      ativoId: 10,
      categoria: 'MANOBRA',
      descricao: 'Manobra nova',
      responsavelUsuarioId: 7,
      inicioPrevisto: instanteDeDataHoraLocal('2026-11-05T10:00'),
      terminoPrevisto: instanteDeDataHoraLocal('2026-11-05T10:00'),
      criticidade: null,
    })
  })

  it('aceita outro responsavel pelo ID e mostra recusa do servidor', async () => {
    vi.mocked(eventosApi.criarEvento).mockRejectedValue(erroHttp(404, 'Responsável não encontrado.'))

    await abrir()
    preencher('Categoria', 'DESLIGAMENTO_PROGRAMADO')
    preencher('Ativo', '11')
    preencher('Descrição', 'Poda')
    preencher('Início previsto', '2026-11-05T10:00')
    preencher('Responsável', 'outro')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Informe o ID numérico do usuário responsável.')).toBeInTheDocument()

    preencher('ID do responsável', '999')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Responsável não encontrado.')).toBeInTheDocument()
    expect(eventosApi.criarEvento).toHaveBeenCalledWith(expect.objectContaining({ responsavelUsuarioId: 999 }))
  })
})
