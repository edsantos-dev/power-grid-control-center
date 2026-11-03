import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as alimentadoresApi from '../../services/alimentadores'
import type { Alimentador, PerdaTecnica, PerfilAcesso } from '../../types'
import { inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'
import PerdasTecnicas from './PerdasTecnicas'

vi.mock('../../services/alimentadores')

const resultado = (dados: Partial<PerdaTecnica>): PerdaTecnica => ({
  alimentadorId: 10,
  dataInicio: '2026-11-01T03:00:00Z',
  dataFim: '2026-11-02T03:00:00Z',
  totalEnergiaInjetadaMwh: 120,
  totalPerdaEstimadaMwh: 5.4,
  percentualPonderado: 4.5,
  situacao: 'OK',
  percentualCobertura: 100,
  medicoesConsideradas: [
    {
      id: 1,
      alimentadorId: 10,
      fonteId: 3,
      origem: 'SIMULADA',
      inicioPeriodo: '2026-11-01T03:00:00Z',
      fimPeriodo: '2026-11-02T03:00:00Z',
      recebidoEm: '2026-11-02T03:00:05Z',
      energiaInjetadaMwh: 120,
      percentualPerdaReferencia: 4.5,
    },
  ],
  ...dados,
})

function renderizar(perfil: PerfilAcesso = 'OPERADOR') {
  const auth: AuthContextValue = { usuario: { id: 1, nome: 'U', email: 'u@x', perfil }, carregando: false, login: vi.fn(), logout: vi.fn() }
  render(
    <AuthContext.Provider value={auth}>
      <PerdasTecnicas />
    </AuthContext.Provider>
  )
}

async function preencherPeriodo() {
  await screen.findByRole('option', { name: 'AL Centro' })
  fireEvent.change(screen.getByLabelText('Alimentador'), { target: { value: '10' } })
  fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-11-01' } })
  fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2026-11-01' } })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([{ id: 10, nome: 'AL Centro' } as Alimentador])
})

describe('perdas tecnicas', () => {
  it('exige alimentador e periodo valido', async () => {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Calcular perdas' }))
    expect(screen.getByText('Selecione o alimentador e o período.')).toBeInTheDocument()

    await preencherPeriodo()
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-11-05' } })
    await userEvent.click(screen.getByRole('button', { name: 'Calcular perdas' }))
    expect(screen.getByText('A data final deve ser igual ou posterior à inicial.')).toBeInTheDocument()
    expect(alimentadoresApi.calcularPerdasTecnicas).not.toHaveBeenCalled()
  })

  it('mostra totais, percentual ponderado e as medicoes rastreadas', async () => {
    vi.mocked(alimentadoresApi.calcularPerdasTecnicas).mockResolvedValue(resultado({}))

    renderizar()
    await preencherPeriodo()
    await userEvent.click(screen.getByRole('button', { name: 'Calcular perdas' }))

    const painel = await screen.findByLabelText('Resultado das perdas técnicas')
    expect(painel).toHaveTextContent('Calculada')
    expect(painel).toHaveTextContent('120 MWh')
    expect(painel).toHaveTextContent('5,4 MWh')
    expect(painel).toHaveTextContent('4,5%')
    expect(within(painel).getByRole('row', { name: /#3 · Simulada/ })).toHaveTextContent('4,5%')
    expect(alimentadoresApi.calcularPerdasTecnicas).toHaveBeenCalledWith(10, inicioDoDiaLocal('2026-11-01'), inicioDoDiaSeguinteLocal('2026-11-01'))
  })

  it('indica cobertura insuficiente sem exibir totais', async () => {
    vi.mocked(alimentadoresApi.calcularPerdasTecnicas).mockResolvedValue(
      resultado({ situacao: 'DADOS_INSUFICIENTES', totalEnergiaInjetadaMwh: null, totalPerdaEstimadaMwh: null, percentualPonderado: null, percentualCobertura: 62.5 })
    )

    renderizar()
    await preencherPeriodo()
    await userEvent.click(screen.getByRole('button', { name: 'Calcular perdas' }))

    const painel = await screen.findByLabelText('Resultado das perdas técnicas')
    expect(painel).toHaveTextContent('Dados insuficientes')
    expect(painel).toHaveTextContent('as medições cobrem 62,5% do período')
    expect(within(painel).getAllByText('—')).toHaveLength(3)
  })

  it('trata energia zero sem percentual', async () => {
    vi.mocked(alimentadoresApi.calcularPerdasTecnicas).mockResolvedValue(
      resultado({ situacao: 'SEM_ENERGIA_INJETADA', totalEnergiaInjetadaMwh: 0, totalPerdaEstimadaMwh: 0, percentualPonderado: null })
    )

    renderizar()
    await preencherPeriodo()
    await userEvent.click(screen.getByRole('button', { name: 'Calcular perdas' }))

    const painel = await screen.findByLabelText('Resultado das perdas técnicas')
    expect(painel).toHaveTextContent('Sem energia injetada')
    expect(painel).toHaveTextContent('0 MWh')
  })

  it('oferece o parametro de estimativa apenas ao gerente', async () => {
    vi.mocked(alimentadoresApi.definirPercentualPerda).mockResolvedValue({ alimentadorId: 10, percentualPerdaTecnica: 4.5 })

    renderizar('OPERADOR')
    await preencherPeriodo()
    expect(screen.queryByLabelText('Percentual de estimativa do alimentador (%)')).not.toBeInTheDocument()
  })

  it('valida e envia o parametro de estimativa do gerente', async () => {
    vi.mocked(alimentadoresApi.definirPercentualPerda).mockResolvedValue({ alimentadorId: 10, percentualPerdaTecnica: 4.5 })

    renderizar('GERENTE_OPERACOES')
    await preencherPeriodo()
    fireEvent.change(screen.getByLabelText('Percentual de estimativa do alimentador (%)'), { target: { value: '100' } })
    await userEvent.click(screen.getByRole('button', { name: 'Definir' }))
    expect(screen.getByText('Informe um percentual de 0 a menos de 100, com até seis casas decimais.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Percentual de estimativa do alimentador (%)'), { target: { value: '4,5' } })
    await userEvent.click(screen.getByRole('button', { name: 'Definir' }))
    expect(await screen.findByRole('status')).toHaveTextContent('4,5%')
    expect(alimentadoresApi.definirPercentualPerda).toHaveBeenCalledWith(10, '4.5')
  })
})
