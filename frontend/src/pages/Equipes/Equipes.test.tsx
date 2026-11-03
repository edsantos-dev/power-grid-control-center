import { act, fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as equipesApi from '../../services/equipes'
import type { Equipe, Integrante, PageResponse, PerfilAcesso } from '../../types'
import Equipes from '.'

vi.mock('../../services/equipes')

const equipe = (dados: Partial<Equipe>): Equipe => ({
  id: 1,
  nome: 'Equipe Norte',
  responsabilidades: 'Manutenção preventiva',
  especialidades: null,
  situacao: 'FORA_DE_SERVICO',
  ...dados,
})

const integrante: Integrante = {
  id: 10,
  equipeId: 1,
  usuarioId: 3,
  nome: 'Técnico Inicial',
  perfil: 'TECNICO_MANUTENCAO',
  inicioVigencia: '2026-11-03T12:00:00Z',
  fimVigencia: null,
}

function pagina(content: Equipe[]): PageResponse<Equipe> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true }
}

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, { config, status, data: { message }, statusText: '', headers: {} })
}

function renderizar(perfil: PerfilAcesso = 'TECNICO_MANUTENCAO') {
  const auth: AuthContextValue = { usuario: { id: 3, nome: 'T', email: 't@x', perfil }, carregando: false, login: vi.fn(), logout: vi.fn() }
  render(
    <AuthContext.Provider value={auth}>
      <Equipes />
    </AuthContext.Provider>
  )
}

const avancar = () => act(async () => vi.advanceTimersByTime(300))

async function abrirIntegrantes() {
  await avancar()
  await userEvent.click(await screen.findByRole('button', { name: 'Integrantes de Equipe Norte' }))
  return screen.getByRole('dialog', { name: 'Equipe Equipe Norte' })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.mocked(equipesApi.listarEquipes).mockResolvedValue(pagina([equipe({})]))
  vi.mocked(equipesApi.listarIntegrantes).mockResolvedValue([])
})

afterEach(() => {
  vi.useRealTimers()
})

describe('equipes', () => {
  it('lista responsabilidades, especialidades e situacao, filtrando no servidor', async () => {
    renderizar()
    await avancar()

    const linha = (await screen.findByText('Equipe Norte')).closest('tr')!
    expect(linha).toHaveTextContent('Manutenção preventiva')
    expect(linha).toHaveTextContent('Não informadas')
    expect(linha).toHaveTextContent('Fora de serviço')

    await userEvent.click(screen.getByRole('button', { name: 'Disponível' }))
    await avancar()
    expect(vi.mocked(equipesApi.listarEquipes).mock.calls.at(-1)).toEqual([0, { nome: undefined, situacao: 'DISPONIVEL' }])
  })

  it('oculta acoes de escrita para o operador', async () => {
    renderizar('OPERADOR')
    const dialogo = await abrirIntegrantes()

    expect(screen.queryByRole('button', { name: 'Nova equipe' })).not.toBeInTheDocument()
    expect(within(dialogo).queryByLabelText('Alterar situação')).not.toBeInTheDocument()
    expect(within(dialogo).queryByLabelText('Incluir usuário pelo ID')).not.toBeInTheDocument()
  })

  it('cria equipe com textos opcionais vazios como nulos', async () => {
    vi.mocked(equipesApi.criarEquipe).mockResolvedValue(equipe({ id: 2, nome: 'Equipe Sul' }))

    renderizar()
    await avancar()
    await userEvent.click(await screen.findByRole('button', { name: 'Nova equipe' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Campo obrigatório.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Nome'), { target: { value: ' Equipe Sul ' } })
    fireEvent.change(screen.getByLabelText('Especialidades (opcional)'), { target: { value: '   ' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(equipesApi.criarEquipe).toHaveBeenCalledWith({ nome: 'Equipe Sul', responsabilidades: null, especialidades: null })
  })

  it('inclui e remove integrantes pelo ID do usuario', async () => {
    vi.mocked(equipesApi.incluirIntegrante).mockResolvedValue(integrante)
    vi.mocked(equipesApi.removerIntegrante).mockResolvedValue()

    renderizar()
    const dialogo = await abrirIntegrantes()
    expect(await within(dialogo).findByText('Nenhum integrante ativo.')).toBeInTheDocument()

    fireEvent.change(within(dialogo).getByLabelText('Incluir usuário pelo ID'), { target: { value: 'abc' } })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Incluir' }))
    expect(within(dialogo).getByText('Informe o ID numérico do usuário.')).toBeInTheDocument()

    vi.mocked(equipesApi.listarIntegrantes).mockResolvedValue([integrante])
    fireEvent.change(within(dialogo).getByLabelText('Incluir usuário pelo ID'), { target: { value: '3' } })
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Incluir' }))

    const lista = await within(dialogo).findByRole('list', { name: 'Integrantes ativos' })
    expect(await within(lista).findByText(/Técnico Inicial/)).toBeInTheDocument()
    expect(lista).toHaveTextContent('Técnico de Manutenção')
    expect(equipesApi.incluirIntegrante).toHaveBeenCalledWith(1, 3)

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Remover Técnico Inicial' }))
    expect(equipesApi.removerIntegrante).toHaveBeenCalledWith(1, 3)
  })

  it('exibe a regra de disponibilidade recusada pelo servidor', async () => {
    vi.mocked(equipesApi.alterarSituacaoEquipe).mockRejectedValue(erroHttp(409, 'A equipe precisa de ao menos um integrante ativo para essa situação.'))

    renderizar()
    const dialogo = await abrirIntegrantes()
    fireEvent.change(within(dialogo).getByLabelText('Alterar situação'), { target: { value: 'DISPONIVEL' } })

    expect(await within(dialogo).findByText('A equipe precisa de ao menos um integrante ativo para essa situação.')).toBeInTheDocument()
    expect(equipesApi.alterarSituacaoEquipe).toHaveBeenCalledWith(1, 'DISPONIVEL')
  })

  it('atualiza a situacao e a lista quando aceita', async () => {
    vi.mocked(equipesApi.alterarSituacaoEquipe).mockResolvedValue(equipe({ situacao: 'DISPONIVEL' }))

    renderizar()
    const dialogo = await abrirIntegrantes()
    fireEvent.change(within(dialogo).getByLabelText('Alterar situação'), { target: { value: 'DISPONIVEL' } })

    expect(await within(dialogo).findByText('Disponível', { selector: 'span' })).toBeInTheDocument()
  })
})
