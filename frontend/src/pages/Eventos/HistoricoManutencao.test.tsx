import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as ativosApi from '../../services/ativos'
import * as equipesApi from '../../services/equipes'
import * as eventosApi from '../../services/eventos'
import type { EventoOperacional, ManutencaoHistorico, PageResponse } from '../../types'
import { inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'
import Eventos from '.'

vi.mock('../../services/eventos')
vi.mock('../../services/ativos')
vi.mock('../../services/equipes')

const auth: AuthContextValue = {
  usuario: { id: 7, nome: 'Técnico', email: 't@powergrid.local', perfil: 'TECNICO_MANUTENCAO' },
  carregando: false,
  login: vi.fn(),
  logout: vi.fn(),
}

const manutencao = (dados: Partial<EventoOperacional>, ultimaTransicao: ManutencaoHistorico['ultimaTransicao'] = null): ManutencaoHistorico => ({
  evento: {
    id: 1,
    ativoId: 10,
    categoria: 'MANUTENCAO',
    descricao: 'Troca de isoladores',
    criticidade: 'Média',
    situacao: 'CONCLUIDO',
    registradorUsuarioId: 7,
    responsavelUsuarioId: 7,
    inicioPrevisto: '2026-11-01T08:00:00Z',
    terminoPrevisto: null,
    inicioEfetivo: '2026-11-01T08:10:00Z',
    terminoEfetivo: '2026-11-01T10:00:00Z',
    equipeDesignadaId: null,
    versaoDesignacao: 0,
    estadoAposReparo: null,
    ...dados,
  },
  ultimaTransicao,
})

function pagina(content: ManutencaoHistorico[], extras: Partial<PageResponse<ManutencaoHistorico>> = {}): PageResponse<ManutencaoHistorico> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true, ...extras }
}

async function abrir() {
  render(
    <AuthContext.Provider value={auth}>
      <Eventos />
    </AuthContext.Provider>
  )
  await userEvent.click(await screen.findByRole('button', { name: 'Manutenções por ativo' }))
}

const dialogo = () => screen.getByRole('dialog', { name: 'Histórico de manutenção por ativo' })

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(equipesApi.listarTodasEquipes).mockResolvedValue([])
  vi.mocked(eventosApi.listarDesignacoes).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })
  vi.mocked(eventosApi.listarEventos).mockResolvedValue([])
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 10, nome: 'LT Norte-Sul', tipo: 'Linha' }])
  vi.mocked(eventosApi.listarManutencoesDoAtivo).mockResolvedValue(
    pagina([
      manutencao({}, {
        id: 5,
        eventoId: 1,
        usuarioId: 7,
        situacaoAnterior: 'EM_ANDAMENTO',
        situacaoNova: 'CONCLUIDO',
        alteradoEm: '2026-11-01T10:00:00Z',
        comentario: 'Isoladores substituídos',
      }),
      manutencao({ id: 2, descricao: 'Inspeção termográfica', situacao: 'ABERTO', inicioEfetivo: null, terminoEfetivo: null, responsavelUsuarioId: 3 }),
    ])
  )
})

describe('historico de manutencao por ativo', () => {
  it('so consulta depois de escolher o ativo', async () => {
    await abrir()

    expect(within(dialogo()).getByText('Selecione um ativo para consultar as manutenções.')).toBeInTheDocument()
    expect(eventosApi.listarManutencoesDoAtivo).not.toHaveBeenCalled()
  })

  it('mostra datas, situacao, responsavel e ultima transicao, com nao iniciada ao final', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Ativo', { selector: '#manutencao-ativo' }), { target: { value: '10' } })

    await within(dialogo()).findByText('Troca de isoladores')
    const [concluida, aberta] = within(dialogo()).getAllByRole('row').slice(1)
    expect(concluida).toHaveTextContent('Concluído')
    expect(concluida).toHaveTextContent('Em andamento → Concluído')
    expect(concluida).toHaveTextContent('Isoladores substituídos')
    expect(concluida).toHaveTextContent('Criticidade: Média')
    expect(aberta).toHaveTextContent('Não iniciada')
    expect(aberta).toHaveTextContent('usuário #3')
    expect(aberta).toHaveTextContent('Sem transições')
    expect(eventosApi.listarManutencoesDoAtivo).toHaveBeenCalledWith(10, 0, {
      situacao: undefined,
      responsavelUsuarioId: undefined,
      dataInicio: undefined,
      dataFim: undefined,
    })
  })

  it('envia filtros de situacao, responsavel e periodo sobre o inicio efetivo', async () => {
    await abrir()
    fireEvent.change(screen.getByLabelText('Ativo', { selector: '#manutencao-ativo' }), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Situação'), { target: { value: 'CONCLUIDO' } })
    await userEvent.click(screen.getByLabelText('Somente as que estão sob minha responsabilidade'))
    fireEvent.change(screen.getByLabelText('Início efetivo de'), { target: { value: '2026-11-01' } })
    fireEvent.change(screen.getByLabelText('até'), { target: { value: '2026-11-02' } })

    expect(vi.mocked(eventosApi.listarManutencoesDoAtivo).mock.calls.at(-1)).toEqual([10, 0, {
      situacao: 'CONCLUIDO',
      responsavelUsuarioId: 7,
      dataInicio: inicioDoDiaLocal('2026-11-01'),
      dataFim: inicioDoDiaSeguinteLocal('2026-11-02'),
    }])
  })

  it('exibe estado vazio e abre as transicoes completas', async () => {
    vi.mocked(eventosApi.listarTransicoes).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true })

    await abrir()
    fireEvent.change(screen.getByLabelText('Ativo', { selector: '#manutencao-ativo' }), { target: { value: '10' } })
    await userEvent.click((await within(dialogo()).findAllByRole('button', { name: 'Transições' }))[0])
    expect(await screen.findByRole('dialog', { name: 'Histórico: Troca de isoladores' })).toBeInTheDocument()
    expect(eventosApi.listarTransicoes).toHaveBeenCalledWith(1, 0)

    vi.mocked(eventosApi.listarManutencoesDoAtivo).mockResolvedValue(pagina([]))
    fireEvent.change(screen.getByLabelText('Situação'), { target: { value: 'EM_ANDAMENTO' } })
    expect(await within(dialogo()).findByText('Nenhuma manutenção registrada para este ativo com estes filtros.')).toBeInTheDocument()
  })
})
