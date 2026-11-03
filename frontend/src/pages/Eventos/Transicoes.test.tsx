import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as ativosApi from '../../services/ativos'
import * as eventosApi from '../../services/eventos'
import type { EventoOperacional, PerfilAcesso, TransicaoEvento } from '../../types'
import { instanteDeDataHoraLocal } from '../../utils/formatacao'
import Eventos from '.'
import { podeTransicionar, proximaTransicao } from './transicoes'

vi.mock('../../services/eventos')
vi.mock('../../services/ativos')

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

const transicao: TransicaoEvento = {
  id: 90,
  eventoId: 1,
  usuarioId: 7,
  situacaoAnterior: 'ABERTO',
  situacaoNova: 'EM_ANDAMENTO',
  alteradoEm: '2026-11-03T12:05:00Z',
  comentario: 'Equipe em campo',
}

function renderizar(perfil: PerfilAcesso = 'OPERADOR', id = 7) {
  const auth: AuthContextValue = {
    usuario: { id, nome: 'Usuário', email: 'u@powergrid.local', perfil },
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

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, { config, status, data: { message, fieldErrors: [] }, statusText: '', headers: {} })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 10, nome: 'AL Centro', tipo: 'Alimentador' }])
  vi.mocked(eventosApi.listarEventos).mockResolvedValue([evento({})])
})

describe('regras de transicao', () => {
  it('avanca apenas ABERTO -> EM_ANDAMENTO -> CONCLUIDO', () => {
    expect(proximaTransicao(evento({}))?.destino).toBe('EM_ANDAMENTO')
    expect(proximaTransicao(evento({ situacao: 'EM_ANDAMENTO' }))?.destino).toBe('CONCLUIDO')
    expect(proximaTransicao(evento({ situacao: 'CONCLUIDO' }))).toBeNull()
  })

  it('autoriza o gerente e o responsavel', () => {
    const usuario = (perfil: PerfilAcesso, id: number) => ({ id, nome: '', email: '', perfil })
    expect(podeTransicionar(evento({}), usuario('GERENTE_OPERACOES', 99))).toBe(true)
    expect(podeTransicionar(evento({}), usuario('TECNICO_MANUTENCAO', 7))).toBe(true)
    expect(podeTransicionar(evento({}), usuario('OPERADOR', 8))).toBe(false)
  })
})

describe('transicao pela tela', () => {
  it('oculta a acao para quem nao e responsavel nem gerente', async () => {
    renderizar('OPERADOR', 8)
    await screen.findByText('Transferência de carga')

    expect(screen.queryByRole('button', { name: 'Iniciar Transferência de carga' })).not.toBeInTheDocument()
  })

  it('exige comentario e envia a situacao esperada', async () => {
    vi.mocked(eventosApi.transicionarEvento).mockResolvedValue(transicao)
    vi.mocked(eventosApi.buscarEvento).mockResolvedValue(evento({ situacao: 'EM_ANDAMENTO', inicioEfetivo: '2026-11-03T12:05:00Z' }))

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Iniciar Transferência de carga' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Campo obrigatório.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Comentário'), { target: { value: ' Equipe em campo ' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const linha = (await screen.findByText('Em andamento', { selector: 'td' })).closest('tr')!
    expect(within(linha).getByRole('button', { name: 'Concluir Transferência de carga' })).toBeInTheDocument()
    expect(eventosApi.transicionarEvento).toHaveBeenCalledWith(1, {
      situacaoEsperada: 'ABERTO',
      situacaoDestino: 'EM_ANDAMENTO',
      comentario: 'Equipe em campo',
      terminoEfetivo: null,
    })
  })

  it('pede o termino efetivo somente na conclusao de interrupcao', async () => {
    vi.mocked(eventosApi.listarEventos).mockResolvedValue([
      evento({ categoria: 'INTERRUPCAO_FORNECIMENTO', descricao: 'Queda', situacao: 'EM_ANDAMENTO', inicioPrevisto: null, inicioEfetivo: instanteDeDataHoraLocal('2026-11-03T07:00') }),
    ])
    vi.mocked(eventosApi.transicionarEvento).mockResolvedValue({ ...transicao, situacaoAnterior: 'EM_ANDAMENTO', situacaoNova: 'CONCLUIDO' })
    vi.mocked(eventosApi.buscarEvento).mockResolvedValue(evento({ situacao: 'CONCLUIDO' }))

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Concluir Queda' }))
    fireEvent.change(screen.getByLabelText('Comentário'), { target: { value: 'Fornecimento normalizado' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('Campo obrigatório.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Término da interrupção'), { target: { value: '2999-01-01T00:00' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('O término não pode estar no futuro.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Término da interrupção'), { target: { value: '2026-11-03T06:00' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))
    expect(screen.getByText('O término não pode ser anterior ao início da interrupção.')).toBeInTheDocument()
    expect(eventosApi.transicionarEvento).not.toHaveBeenCalled()

    const termino = '2026-11-03T08:30'
    fireEvent.change(screen.getByLabelText('Término da interrupção'), { target: { value: termino } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    await screen.findByText('Concluído', { selector: 'td' })
    expect(eventosApi.transicionarEvento).toHaveBeenCalledWith(1, expect.objectContaining({
      situacaoEsperada: 'EM_ANDAMENTO',
      situacaoDestino: 'CONCLUIDO',
      terminoEfetivo: instanteDeDataHoraLocal(termino),
    }))
  })

  it('recarrega a lista quando o servidor indica situacao divergente', async () => {
    vi.mocked(eventosApi.transicionarEvento).mockRejectedValue(
      erroHttp(409, 'A situação do evento diverge da situação esperada. Consulte o evento novamente.')
    )

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Iniciar Transferência de carga' }))
    fireEvent.change(screen.getByLabelText('Comentário'), { target: { value: 'Tentativa' } })
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('A situação do evento diverge da situação esperada. Consulte o evento novamente.')).toBeInTheDocument()
    expect(eventosApi.listarEventos).toHaveBeenCalledTimes(2)
  })
})

describe('historico de transicoes', () => {
  it('mostra estados, autor, instante e comentario', async () => {
    vi.mocked(eventosApi.listarTransicoes).mockResolvedValue({
      content: [transicao],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    })

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Histórico de Transferência de carga' }))

    const lista = await screen.findByRole('list', { name: 'Transições do evento' })
    expect(lista).toHaveTextContent('Aberto → Em andamento')
    expect(lista).toHaveTextContent('por você')
    expect(lista).toHaveTextContent('Equipe em campo')
  })

  it('indica evento sem alteracoes', async () => {
    vi.mocked(eventosApi.listarTransicoes).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })

    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Histórico de Transferência de carga' }))

    expect(await screen.findByText('Nenhuma alteração de situação registrada.')).toBeInTheDocument()
  })
})
