import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as ativosApi from '../../services/ativos'
import * as eventosApi from '../../services/eventos'
import type { EventoOperacional } from '../../types'
import { dataHoraLocalAtual, instanteDeDataHoraLocal } from '../../utils/formatacao'
import Eventos from '.'

vi.mock('../../services/eventos')
vi.mock('../../services/ativos')

const auth: AuthContextValue = {
  usuario: { id: 7, nome: 'Operador Inicial', email: 'operador@powergrid.local', perfil: 'OPERADOR' },
  carregando: false,
  login: vi.fn(),
  logout: vi.fn(),
}

const interrupcao: EventoOperacional = {
  id: 5,
  ativoId: 10,
  categoria: 'INTERRUPCAO_FORNECIMENTO',
  descricao: 'Queda após descarga atmosférica',
  criticidade: null,
  situacao: 'ABERTO',
  registradorUsuarioId: 7,
  responsavelUsuarioId: 7,
  inicioPrevisto: null,
  terminoPrevisto: null,
  inicioEfetivo: '2026-11-03T10:15:00Z',
  terminoEfetivo: null,
  equipeDesignadaId: null,
  versaoDesignacao: 0,
  estadoAposReparo: null,
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

async function abrir() {
  render(
    <AuthContext.Provider value={auth}>
      <Eventos />
    </AuthContext.Provider>
  )
  await userEvent.click(await screen.findByRole('button', { name: 'Registrar interrupção' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(eventosApi.listarEventos).mockResolvedValue([])
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 10, nome: 'AL Centro', tipo: 'Alimentador' }])
})

describe('registro de interrupcao', () => {
  it('exige ativo, descricao e inicio efetivo', async () => {
    await abrir()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(3)
    expect(eventosApi.registrarInterrupcao).not.toHaveBeenCalled()
  })

  it('rejeita inicio no futuro sem chamar a API', async () => {
    await abrir()
    preencher('Ativo envolvido', '10')
    preencher('Descrição', 'Queda')
    preencher('Início da interrupção', '2999-01-01T00:00')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getByText('O início da interrupção não pode estar no futuro.')).toBeInTheDocument()
    expect(eventosApi.registrarInterrupcao).not.toHaveBeenCalled()
  })

  it('registra pela rota de interrupcoes e lista com inicio efetivo', async () => {
    vi.mocked(eventosApi.registrarInterrupcao).mockResolvedValue(interrupcao)

    await abrir()
    preencher('Ativo envolvido', '10')
    preencher('Descrição', ' Queda após descarga atmosférica ')
    preencher('Início da interrupção', '2026-11-03T07:15')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Queda após descarga atmosférica')).toBeInTheDocument()
    const linha = screen.getByText('Queda após descarga atmosférica').closest('tr')!
    expect(linha).toHaveTextContent('Interrupção de fornecimento')
    expect(linha).toHaveTextContent('Início efetivo')
    expect(eventosApi.registrarInterrupcao).toHaveBeenCalledWith({
      ativoId: 10,
      descricao: 'Queda após descarga atmosférica',
      responsavelUsuarioId: 7,
      inicioEfetivo: instanteDeDataHoraLocal('2026-11-03T07:15'),
      criticidade: null,
    })
    expect(eventosApi.criarEvento).not.toHaveBeenCalled()
  })

  it('exibe recusa do servidor', async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    vi.mocked(eventosApi.registrarInterrupcao).mockRejectedValue(
      new AxiosError('erro', undefined, config, undefined, { config, status: 404, data: { message: 'Ativo não encontrado.' }, statusText: '', headers: {} })
    )

    await abrir()
    preencher('Ativo envolvido', '10')
    preencher('Descrição', 'Queda')
    preencher('Início da interrupção', dataHoraLocalAtual())
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Ativo não encontrado.')).toBeInTheDocument()
  })
})
