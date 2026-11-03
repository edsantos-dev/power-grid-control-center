import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as subestacoesApi from '../../services/subestacoes'
import * as usinasApi from '../../services/usinas'
import type { PageResponse, PerfilAcesso, Subestacao, Usina } from '../../types'
import Usinas from '.'

vi.mock('../../services/usinas')
vi.mock('../../services/subestacoes')

const usina = (dados: Partial<Usina>): Usina => ({
  id: 7,
  nome: 'UHE Rio Verde',
  latitude: -25.1,
  longitude: -49.9,
  tipoGeracao: 'HIDRELETRICA',
  capacidadeMaximaMw: 120.5,
  subestacaoId: 1,
  dataInstalacao: '2020-05-10',
  estadoOperacional: 'OPERACIONAL',
  ...dados,
})

function pagina(content: Usina[], extras: Partial<PageResponse<Usina>> = {}): PageResponse<Usina> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true, ...extras }
}

function renderizar(perfil: PerfilAcesso = 'GERENTE_OPERACOES') {
  const auth: AuthContextValue = { usuario: { id: 2, nome: 'G', email: 'g@x', perfil }, carregando: false, login: vi.fn(), logout: vi.fn() }
  render(
    <AuthContext.Provider value={auth}>
      <Usinas />
    </AuthContext.Provider>
  )
}

const avancar = () => act(async () => vi.advanceTimersByTime(300))

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([{ id: 1, nome: 'SE Norte' } as Subestacao])
  vi.mocked(usinasApi.listarUsinas).mockResolvedValue(pagina([usina({}), usina({ id: 8, nome: 'Parque Solar', tipoGeracao: 'SOLAR', dataInstalacao: null })]))
})

afterEach(() => {
  vi.useRealTimers()
})

describe('usinas', () => {
  it('lista capacidade nominal separada de geracao observada', async () => {
    renderizar()
    await avancar()

    const linha = (await screen.findByText('UHE Rio Verde')).closest('tr')!
    expect(linha).toHaveTextContent('Hidrelétrica')
    expect(linha).toHaveTextContent('120,5 MW')
    expect(within(linha).getByText('Sem medição')).toBeInTheDocument()
    expect(linha).toHaveTextContent('SE Norte')
    expect(linha).toHaveTextContent('instalada em 10/05/2020')
    expect(screen.getByText(/2 usina\(s\) encontrada\(s\)/)).toBeInTheDocument()
  })

  it('filtra no servidor por nome, tipo e status', async () => {
    renderizar()
    await avancar()
    await screen.findByText('UHE Rio Verde')

    await userEvent.type(screen.getByPlaceholderText('Buscar usina pelo nome...'), 'rio')
    preencher('Filtrar por tipo de geração', 'HIDRELETRICA')
    preencher('Filtrar por status', 'FALHA')
    await avancar()

    expect(vi.mocked(usinasApi.listarUsinas).mock.calls.at(-1)).toEqual([0, { nome: 'rio', tipoGeracao: 'HIDRELETRICA', estadoOperacional: 'FALHA' }])
  })

  it('oculta cadastro e edicao para quem nao e gerente', async () => {
    renderizar('OPERADOR')
    await avancar()
    await screen.findByText('UHE Rio Verde')

    expect(screen.queryByRole('button', { name: 'Nova usina' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Editar UHE Rio Verde' })).not.toBeInTheDocument()
  })

  it('valida tipo, capacidade positiva e subestacao antes de enviar', async () => {
    renderizar()
    await avancar()
    await userEvent.click(await screen.findByRole('button', { name: 'Nova usina' }))
    preencher('Capacidade máxima (MW)', '0')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getByText('Informe um valor maior que zero.')).toBeInTheDocument()
    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(5)
    expect(usinasApi.criarUsina).not.toHaveBeenCalled()
  })

  it('cria com data opcional nula e recarrega a lista', async () => {
    vi.mocked(usinasApi.criarUsina).mockResolvedValue(usina({ id: 9, nome: 'EOL Serra Alta', tipoGeracao: 'EOLICA' }))

    renderizar()
    await avancar()
    await userEvent.click(await screen.findByRole('button', { name: 'Nova usina' }))
    preencher('Nome', ' EOL Serra Alta ')
    preencher('Tipo de geração', 'EOLICA')
    preencher('Capacidade máxima (MW)', '45,5')
    preencher('Subestação de conexão', '1')
    preencher('Latitude', '-25.3')
    preencher('Longitude', '-49.1')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    await avancar()

    expect(usinasApi.criarUsina).toHaveBeenCalledWith({
      nome: 'EOL Serra Alta',
      tipoGeracao: 'EOLICA',
      capacidadeMaximaMw: '45.5',
      subestacaoId: 1,
      latitude: '-25.3',
      longitude: '-49.1',
      dataInstalacao: null,
      estadoOperacional: 'OPERACIONAL',
    })
    expect(vi.mocked(usinasApi.listarUsinas).mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('edita preservando o status e mostra recusa do servidor', async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    vi.mocked(usinasApi.atualizarUsina).mockRejectedValue(
      new AxiosError('erro', undefined, config, undefined, { config, status: 404, data: { message: 'Subestação não encontrada.' }, statusText: '', headers: {} })
    )

    renderizar()
    await avancar()
    await userEvent.click(await screen.findByRole('button', { name: 'Editar UHE Rio Verde' }))
    expect(screen.getByLabelText('Tipo de geração')).toHaveValue('HIDRELETRICA')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Subestação não encontrada.')).toBeInTheDocument()
    expect(usinasApi.atualizarUsina).toHaveBeenCalledWith(7, expect.objectContaining({ estadoOperacional: 'OPERACIONAL', dataInstalacao: '2020-05-10' }))
  })

  it('pagina pelo servidor', async () => {
    vi.mocked(usinasApi.listarUsinas).mockResolvedValue(pagina([usina({})], { totalElements: 25, totalPages: 2, last: false }))

    renderizar()
    await avancar()
    await userEvent.click(await screen.findByRole('button', { name: 'Próxima página' }))
    await avancar()

    expect(vi.mocked(usinasApi.listarUsinas).mock.calls.at(-1)?.[0]).toBe(1)
  })
})
