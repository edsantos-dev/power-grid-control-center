import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alimentadoresApi from '../../services/alimentadores'
import type { Alimentador, HistoricoConsumoMedido, HistoricoEnergia, PerfilAcesso } from '../../types'
import Relatorios from '.'

vi.mock('../../services/alimentadores')
vi.mock('./GraficoBarras', () => ({
  default: ({ titulo, barras }: { titulo: string; barras: { valor: number | null }[] }) => (
    <p>{`${titulo}: ${barras.map((b) => b.valor ?? 'nulo').join(', ')}`}</p>
  ),
}))

const AGORA = new Date('2026-11-03T15:00:00Z').getTime()

const energia: HistoricoEnergia = {
  alimentadorId: 10,
  dataInicio: '2026-11-01T00:00:00Z',
  dataFim: '2026-11-03T15:00:00Z',
  granularidade: 'DIA',
  fusoHorario: 'UTC',
  possuiDados: true,
  pontos: [
    { inicioPeriodo: '2026-11-01T00:00:00Z', fimPeriodo: '2026-11-02T00:00:00Z', energiaInjetadaMwh: 120, perdaEstimadaMwh: 5.1, percentualPerdaPonderado: 4.25, quantidadeLeituras: 2, situacao: 'OK' },
    { inicioPeriodo: '2026-11-02T00:00:00Z', fimPeriodo: '2026-11-03T00:00:00Z', energiaInjetadaMwh: null, perdaEstimadaMwh: null, percentualPerdaPonderado: null, quantidadeLeituras: 1, situacao: 'DADOS_INSUFICIENTES' },
  ],
}

const consumo: HistoricoConsumoMedido = {
  alimentadorId: 10,
  dataInicio: energia.dataInicio,
  dataFim: energia.dataFim,
  granularidade: 'DIA',
  fusoHorario: 'UTC',
  possuiDados: true,
  quantidadeMedidoresConsiderados: 2,
  coberturaCadastral: 'NAO_AFERIDA',
  pontos: [
    { inicioPeriodo: '2026-11-01T00:00:00Z', fimPeriodo: '2026-11-02T00:00:00Z', consumoMedidoMwh: 0.15, quantidadeLeituras: 4, quantidadeMedidoresComCoberturaCompleta: 2, situacao: 'OK' },
    { inicioPeriodo: '2026-11-02T00:00:00Z', fimPeriodo: '2026-11-03T00:00:00Z', consumoMedidoMwh: null, quantidadeLeituras: 1, quantidadeMedidoresComCoberturaCompleta: 1, situacao: 'DADOS_INSUFICIENTES' },
  ],
}

function renderizar(perfil: PerfilAcesso = 'GERENTE_OPERACOES') {
  const auth: AuthContextValue = { usuario: { id: 2, nome: 'G', email: 'g@x', perfil }, carregando: false, login: vi.fn(), logout: vi.fn() }
  render(
    <AuthContext.Provider value={auth}>
      <Relatorios />
    </AuthContext.Provider>
  )
}

async function consultar(de = '2026-11-01', ate = '2026-11-03') {
  await screen.findAllByRole('option', { name: 'AL Centro' })
  fireEvent.change(screen.getByLabelText('Alimentador', { selector: '#historico-alimentador' }), { target: { value: '10' } })
  fireEvent.change(screen.getByLabelText('Início (UTC)'), { target: { value: de } })
  fireEvent.change(screen.getByLabelText('Fim (UTC)'), { target: { value: ate } })
  await userEvent.click(screen.getByRole('button', { name: 'Consultar histórico' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(AGORA)
  vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([{ id: 10, nome: 'AL Centro' } as Alimentador])
  vi.mocked(alimentadoresApi.consultarHistoricoEnergia).mockResolvedValue(energia)
  vi.mocked(alimentadoresApi.consultarHistoricoConsumo).mockResolvedValue(consumo)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('historico de energia, perdas e consumo', () => {
  it('fica disponivel apenas ao gerente', async () => {
    renderizar('OPERADOR')

    expect(await screen.findByText(/disponível para o perfil Gerente de Operações/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Consultar histórico' })).not.toBeInTheDocument()
  })

  it('envia limites em UTC e limita o fim ao instante atual', async () => {
    renderizar()
    await consultar()

    await screen.findByLabelText('Resultado do histórico')
    expect(alimentadoresApi.consultarHistoricoEnergia).toHaveBeenCalledWith(10, '2026-11-01T00:00:00.000Z', expect.stringMatching(/^2026-11-03T15:00:0/), 'DIA')
    expect(alimentadoresApi.consultarHistoricoConsumo).toHaveBeenCalledWith(10, '2026-11-01T00:00:00.000Z', expect.stringMatching(/^2026-11-03T15:00:0/), 'DIA')
  })

  it('agrupa por mes do inicio ao fim do ultimo mes', async () => {
    renderizar()
    await screen.findAllByRole('option', { name: 'AL Centro' })
    fireEvent.change(screen.getByLabelText('Agrupar por'), { target: { value: 'MES' } })
    await consultar('2026-08', '2026-09')

    await screen.findByLabelText('Resultado do histórico')
    expect(alimentadoresApi.consultarHistoricoEnergia).toHaveBeenCalledWith(10, '2026-08-01T00:00:00.000Z', '2026-10-01T00:00:00.000Z', 'MES')
  })

  it('mostra lacunas como nulas, sem rateio, e separa energia de consumo', async () => {
    renderizar()
    await consultar()

    const resultado = await screen.findByLabelText('Resultado do histórico')
    expect(resultado).toHaveTextContent('Energia injetada: 120, nulo')
    expect(resultado).toHaveTextContent('Perda estimada: 5.1, nulo')
    expect(resultado).toHaveTextContent('Consumo medido: 0.15, nulo')
    expect(resultado).toHaveTextContent('2 medidor(es) considerado(s) · cobertura cadastral não aferida')

    const [, primeiro, segundo] = within(screen.getByRole('table', { name: 'Tabela do histórico' })).getAllByRole('row')
    expect(primeiro).toHaveTextContent('01/11')
    expect(primeiro).toHaveTextContent('4,25%')
    expect(primeiro).toHaveTextContent('0,15 MWh')
    expect(within(segundo).getAllByText('Dados insuficientes')).toHaveLength(2)
  })

  it('exibe estado vazio quando nao ha dados', async () => {
    vi.mocked(alimentadoresApi.consultarHistoricoEnergia).mockResolvedValue({ ...energia, possuiDados: false })
    vi.mocked(alimentadoresApi.consultarHistoricoConsumo).mockResolvedValue({ ...consumo, possuiDados: false })

    renderizar()
    await consultar()

    expect(await screen.findByText('Nenhuma medição de energia ou consumo no período.')).toBeInTheDocument()
  })

  it('mantem o consumo quando a consulta de energia falha', async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    vi.mocked(alimentadoresApi.consultarHistoricoEnergia).mockRejectedValue(
      new AxiosError('erro', undefined, config, undefined, { config, status: 400, data: { message: 'A consulta excede o limite de 366 pontos temporais.' }, statusText: '', headers: {} })
    )

    renderizar()
    await consultar()

    expect(await screen.findByText('Energia e perdas: A consulta excede o limite de 366 pontos temporais.')).toBeInTheDocument()
    expect(screen.getByText('Consumo medido: 0.15, nulo')).toBeInTheDocument()
  })
})
