import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as alertasApi from '../../services/alertas'
import type { AlertaHistorico, FiltrosHistoricoAlertas, PageResponse } from '../../types'
import { inicioDoDiaLocal } from '../../utils/formatacao'
import HistoricoAlertas from './HistoricoAlertas'

vi.mock('../../services/alertas')

const alerta = (dados: Partial<AlertaHistorico>): AlertaHistorico => ({
  idOrigem: 1,
  tipoAlerta: 'SOBRECARGA',
  criticidade: 'ALTA',
  escopo: 'ATIVO',
  ativoId: 30,
  fonteId: 2,
  medicaoId: 9,
  ocorridoEm: '2026-11-03T12:00:00Z',
  statusOrigem: 'RECONHECIDO',
  subtipo: null,
  mensagem: null,
  reconhecidoEm: '2026-11-03T12:10:00Z',
  reconhecidoPorUsuarioId: 1,
  restabelecidoEm: null,
  descricao: 'Sobrecarga de 108,00% (limiar: 100,00%)',
  ...dados,
})

function pagina(content: AlertaHistorico[], extras: Partial<PageResponse<AlertaHistorico>> = {}): PageResponse<AlertaHistorico> {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, first: true, last: true, ...extras }
}

const totais = { CRITICA: 1, ALTA: 5, MEDIA: 2, BAIXA: 0 }

function simularBackend(conteudo = pagina([
  alerta({}),
  // Mesmo idOrigem em outra origem: a identidade é composta
  alerta({ tipoAlerta: 'GENERICO', criticidade: 'CRITICA', escopo: 'REDE_MONITORADA', ativoId: null, statusOrigem: 'RESOLVIDO', reconhecidoEm: null, descricao: 'Queda geral' }),
  alerta({ idOrigem: 3, tipoAlerta: 'COMUNICACAO', criticidade: 'MEDIA', statusOrigem: 'RESTABELECIDO', ocorridoEm: null, reconhecidoEm: null, restabelecidoEm: '2026-11-03T13:00:00Z', ativoId: 77, descricao: null }),
])) {
  vi.mocked(alertasApi.listarHistoricoAlertas).mockImplementation(async (_page: number, filtros: FiltrosHistoricoAlertas, size?: number) =>
    size === 1 ? pagina([], { totalElements: totais[filtros.criticidade!] }) : conteudo
  )
}

const chamadasDeConteudo = () => vi.mocked(alertasApi.listarHistoricoAlertas).mock.calls.filter(([, , size]) => size !== 1)

beforeEach(() => {
  vi.resetAllMocks()
  simularBackend()
})

function renderizar() {
  render(<HistoricoAlertas ativos={[{ id: 30, nome: 'T-01', tipo: 'Transformador' }]} />)
}

describe('historico de alertas', () => {
  it('lista todas as origens e estados, inclusive resolvidos e sem data', async () => {
    renderizar()
    await screen.findByText('Queda geral')

    const linhas = screen.getAllByRole('row').slice(1)
    expect(linhas).toHaveLength(3)
    expect(linhas[0]).toHaveTextContent('Sobrecarga')
    expect(linhas[0]).toHaveTextContent('Transformador · T-01')
    expect(linhas[0]).toHaveTextContent('Sobrecarga de 108,00% (limiar: 100,00%)')
    expect(linhas[0]).toHaveTextContent('Reconhecido')
    expect(linhas[1]).toHaveTextContent('Rede monitorada')
    expect(linhas[1]).toHaveTextContent('Resolvido')
    expect(linhas[2]).toHaveTextContent('Sem data')
    expect(linhas[2]).toHaveTextContent('Ativo #77')
    expect(linhas[2]).toHaveTextContent('Restabelecido')
  })

  it('mostra a contagem por criticidade vinda do total do backend', async () => {
    renderizar()

    const alta = await screen.findByRole('button', { name: /Alta/ })
    expect(await within(alta).findByText('5')).toBeInTheDocument()
    expect(within(screen.getByRole('button', { name: /Baixa/ })).getByText('0')).toBeInTheDocument()
  })

  it('filtra por criticidade ao clicar no cartao e reinicia a pagina', async () => {
    renderizar()
    await screen.findAllByRole('row')

    await userEvent.click(screen.getByRole('button', { name: /Crítica/ }))

    expect(chamadasDeConteudo().at(-1)).toEqual([0, expect.objectContaining({ criticidade: 'CRITICA' })])
    expect(screen.getByLabelText('Criticidade')).toHaveValue('CRITICA')
  })

  it('envia o periodo por dia local com fim exclusivo', async () => {
    renderizar()
    await screen.findAllByRole('row')

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-11-01' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-11-03' } })

    expect(chamadasDeConteudo().at(-1)?.[1]).toEqual({
      criticidade: undefined,
      tipoAlerta: undefined,
      escopo: undefined,
      statusOrigem: undefined,
      dataInicio: inicioDoDiaLocal('2026-11-01'),
      dataFim: inicioDoDiaLocal('2026-11-04'),
    })
  })

  it('bloqueia periodo invertido sem consultar', async () => {
    renderizar()
    await screen.findAllByRole('row')
    const antes = chamadasDeConteudo().length

    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-11-05' } })
    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-11-01' } })

    expect(screen.getByText('A data final deve ser igual ou posterior à inicial.')).toBeInTheDocument()
    expect(chamadasDeConteudo().length).toBe(antes + 1)
  })

  it('pagina pelo backend e exibe estado vazio', async () => {
    simularBackend(pagina([alerta({})], { totalElements: 25, totalPages: 2, last: false }))
    renderizar()
    await screen.findByText('Página 1 de 2')

    await userEvent.click(screen.getByRole('button', { name: 'Próxima página' }))
    expect(chamadasDeConteudo().at(-1)?.[0]).toBe(1)

    simularBackend(pagina([]))
    fireEvent.change(screen.getByLabelText('Origem'), { target: { value: 'TENSAO' } })
    expect(await screen.findByText('Nenhum alerta no histórico para estes filtros.')).toBeInTheDocument()
  })
})
