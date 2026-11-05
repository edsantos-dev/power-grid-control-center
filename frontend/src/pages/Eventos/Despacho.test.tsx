import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as ativosApi from '../../services/ativos'
import * as equipesApi from '../../services/equipes'
import * as eventosApi from '../../services/eventos'
import type { Equipe, EventoOperacional, PerfilAcesso } from '../../types'
import { podeDesignar } from './designacao'
import Eventos from '.'

vi.mock('../../services/eventos')
vi.mock('../../services/ativos')
vi.mock('../../services/equipes')

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
  equipeDesignadaId: null,
  versaoDesignacao: 0,
  estadoAposReparo: null,
  ...dados,
})

const equipe = (id: number, nome: string, situacao: Equipe['situacao']): Equipe => ({ id, nome, situacao, responsabilidades: null, especialidades: null })

const equipes = [equipe(1, 'Equipe Alfa', 'DISPONIVEL'), equipe(2, 'Equipe Beta', 'EM_ATENDIMENTO'), equipe(3, 'Equipe Gama', 'FORA_DE_SERVICO')]

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, { config, status, data: { message, fieldErrors: [] }, statusText: '', headers: {} })
}

const usuario = (perfil: PerfilAcesso, id = 7) => ({ id, nome: 'Usuário de teste', email: 'teste@powergrid.local', perfil })

function renderizar(perfil: PerfilAcesso = 'OPERADOR') {
  const auth: AuthContextValue = { usuario: usuario(perfil), carregando: false, login: vi.fn(), logout: vi.fn() }

  render(
    <AuthContext.Provider value={auth}>
      <Eventos />
    </AuthContext.Provider>
  )
}

const linha = async (descricao: string) => (await screen.findByText(descricao)).closest('tr') as HTMLElement

function preencherComentario(valor: string) {
  fireEvent.change(screen.getByLabelText('Comentário'), { target: { value: valor } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 10, nome: 'AL Centro', tipo: 'Alimentador' }])
  vi.mocked(equipesApi.listarTodasEquipes).mockResolvedValue(equipes)
  vi.mocked(eventosApi.listarEventos).mockResolvedValue([
    evento({}),
    evento({ id: 2, descricao: 'Inspeção em campo', situacao: 'EM_ANDAMENTO', responsavelUsuarioId: 3, equipeDesignadaId: 2, versaoDesignacao: 4 }),
    evento({ id: 3, descricao: 'Poda concluída', situacao: 'CONCLUIDO', equipeDesignadaId: 1, versaoDesignacao: 1 }),
  ])
  vi.mocked(eventosApi.listarTransicoes).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
  vi.mocked(eventosApi.listarDesignacoes).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
})

describe('regra de designacao', () => {
  it('libera gerente e operador responsavel em eventos nao concluidos', () => {
    expect(podeDesignar(evento({}), usuario('GERENTE_OPERACOES', 1))).toBe(true)
    expect(podeDesignar(evento({}), usuario('OPERADOR'))).toBe(true)
    expect(podeDesignar(evento({ responsavelUsuarioId: 3 }), usuario('OPERADOR'))).toBe(false)
    expect(podeDesignar(evento({}), usuario('TECNICO_MANUTENCAO'))).toBe(false)
    expect(podeDesignar(evento({ situacao: 'CONCLUIDO' }), usuario('GERENTE_OPERACOES'))).toBe(false)
  })
})

describe('despacho de equipe', () => {
  it('mostra a equipe atual de cada evento', async () => {
    renderizar()

    expect(await linha('Transferência de carga')).toHaveTextContent('Sem equipe')
    expect(await linha('Inspeção em campo')).toHaveTextContent('Equipe Beta')
    expect(await linha('Poda concluída')).toHaveTextContent('Equipe Alfa')
  })

  it('oferece o despacho conforme o perfil e a situacao', async () => {
    renderizar()
    expect(within(await linha('Transferência de carga')).getByRole('button', { name: 'Despachar equipe para Transferência de carga' })).toBeInTheDocument()
    expect(within(await linha('Inspeção em campo')).queryByRole('button', { name: /equipe/ })).not.toBeInTheDocument()
    expect(within(await linha('Poda concluída')).queryByRole('button', { name: /equipe/ })).not.toBeInTheDocument()
  })

  it('lista apenas equipes que podem receber a designacao e exige escolha e comentario', async () => {
    renderizar()
    await userEvent.click(within(await linha('Transferência de carga')).getByRole('button', { name: /Despachar/ }))

    const opcoes = within(screen.getByLabelText('Equipe')).getAllByRole('option').map((o) => o.textContent)
    expect(opcoes).toEqual(['Selecione', 'Equipe Alfa (Disponível)', 'Equipe Beta (Em atendimento)'])

    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(2)
    expect(eventosApi.designarEquipe).not.toHaveBeenCalled()
  })

  it('envia equipe, versao esperada e comentario e atualiza a linha', async () => {
    vi.mocked(eventosApi.designarEquipe).mockResolvedValue(evento({ equipeDesignadaId: 1, versaoDesignacao: 1 }))
    renderizar()
    await userEvent.click(within(await linha('Transferência de carga')).getByRole('button', { name: /Despachar/ }))

    fireEvent.change(screen.getByLabelText('Equipe'), { target: { value: '1' } })
    preencherComentario('  Equipe mais próxima ')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(eventosApi.designarEquipe).toHaveBeenCalledWith(1, { equipeId: 1, versaoDesignacaoEsperada: 0, comentario: 'Equipe mais próxima' })
    expect(await linha('Transferência de carga')).toHaveTextContent('Equipe Alfa')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('permite ao gerente retirar a equipe atual enviando equipeId nulo', async () => {
    vi.mocked(eventosApi.designarEquipe).mockResolvedValue(evento({ id: 2, descricao: 'Inspeção em campo', situacao: 'EM_ANDAMENTO', responsavelUsuarioId: 3, versaoDesignacao: 5 }))
    renderizar('GERENTE_OPERACOES')
    await userEvent.click(within(await linha('Inspeção em campo')).getByRole('button', { name: 'Alterar equipe de Inspeção em campo' }))

    expect(screen.getByRole('dialog', { name: 'Alterar equipe designada' })).toHaveTextContent('Equipe atual: Equipe Beta')
    expect(within(screen.getByLabelText('Equipe')).queryByRole('option', { name: /Equipe Beta/ })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Equipe'), { target: { value: 'retirar' } })
    preencherComentario('Atendimento encerrado em campo')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(eventosApi.designarEquipe).toHaveBeenCalledWith(2, { equipeId: null, versaoDesignacaoEsperada: 4, comentario: 'Atendimento encerrado em campo' })
    expect(await linha('Inspeção em campo')).toHaveTextContent('Sem equipe')
  })

  it('no conflito de versao mantem o formulario com a versao atual do evento', async () => {
    vi.mocked(eventosApi.designarEquipe).mockRejectedValueOnce(erroHttp(409, 'A versão da designação diverge da versão esperada. Consulte o evento novamente.'))
    renderizar()
    await userEvent.click(within(await linha('Transferência de carga')).getByRole('button', { name: /Despachar/ }))
    fireEvent.change(screen.getByLabelText('Equipe'), { target: { value: '1' } })
    preencherComentario('Primeira tentativa')

    vi.mocked(eventosApi.listarEventos).mockResolvedValue([evento({ equipeDesignadaId: 2, versaoDesignacao: 1 })])
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('A versão da designação diverge da versão esperada. Consulte o evento novamente.')).toBeInTheDocument()
    await within(dialogo).findByText('Equipe atual: Equipe Beta')

    vi.mocked(eventosApi.designarEquipe).mockResolvedValue(evento({ equipeDesignadaId: 1, versaoDesignacao: 2 }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(eventosApi.designarEquipe).toHaveBeenLastCalledWith(1, { equipeId: 1, versaoDesignacaoEsperada: 1, comentario: 'Primeira tentativa' })
  })

  it('mostra no historico as designacoes com equipes, autor e versao', async () => {
    vi.mocked(eventosApi.listarDesignacoes).mockResolvedValue({
      content: [
        { id: 12, eventoOperacionalId: 2, equipeAnteriorId: 2, equipeNovaId: null, usuarioId: 7, registradoEm: '2026-11-03T14:00:00Z', comentario: 'Liberada', versaoDesignacao: 3 },
        { id: 11, eventoOperacionalId: 2, equipeAnteriorId: 1, equipeNovaId: 2, usuarioId: 3, registradoEm: '2026-11-03T13:00:00Z', comentario: 'Troca de turno', versaoDesignacao: 2 },
        { id: 10, eventoOperacionalId: 2, equipeAnteriorId: null, equipeNovaId: 1, usuarioId: 3, registradoEm: '2026-11-03T12:00:00Z', comentario: 'Despacho inicial', versaoDesignacao: 1 },
      ],
      page: 0,
      size: 20,
      totalElements: 3,
      totalPages: 1,
      first: true,
      last: true,
    })
    renderizar('TECNICO_MANUTENCAO')
    await userEvent.click(within(await linha('Inspeção em campo')).getByRole('button', { name: 'Histórico de Inspeção em campo' }))

    const lista = await screen.findByRole('list', { name: 'Designações de equipe' })
    const [retirada, troca, inicial] = await within(lista).findAllByRole('listitem')
    expect(retirada).toHaveTextContent('Equipe Beta retirada')
    expect(retirada).toHaveTextContent('por você • versão 3')
    expect(troca).toHaveTextContent('Equipe Alfa → Equipe Beta')
    expect(troca).toHaveTextContent('por usuário #3')
    expect(inicial).toHaveTextContent('Equipe Alfa designada')
    expect(eventosApi.listarDesignacoes).toHaveBeenCalledWith(2, 0)
  })
})
