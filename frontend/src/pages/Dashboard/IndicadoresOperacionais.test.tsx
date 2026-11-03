import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../../hooks/useAuth'
import * as indicadoresApi from '../../services/indicadores'
import * as subestacoesApi from '../../services/subestacoes'
import type { IndicadoresOperacionais, PerfilAcesso, Subestacao } from '../../types'
import { formatarDuracao, inicioDoDiaLocal } from '../../utils/formatacao'
import Dashboard from '.'

vi.mock('../../services/indicadores')
vi.mock('../../services/subestacoes')
vi.mock('./MapaAtivos', () => ({ default: () => <p>Mapa</p> }))

const AGORA = new Date(2026, 10, 3, 15, 0)

const indicadores = (dados: Partial<IndicadoresOperacionais>): IndicadoresOperacionais => ({
  calculadoEm: '2026-11-03T18:00:00Z',
  dataInicio: '2026-10-28T03:00:00Z',
  dataFim: '2026-11-03T18:00:00Z',
  filtrosAplicados: { ativoId: null, tipos: ['SUBESTACAO'], subestacaoId: null },
  quantidadeAtivos: [
    { tipoAtivo: 'SUBESTACAO', estadoOperacional: 'OPERACIONAL', quantidade: 2 },
    { tipoAtivo: 'SUBESTACAO', estadoOperacional: 'FALHA', quantidade: 1 },
  ],
  totalAtivos: 3,
  quantidadeIniciadasNoPeriodo: 1,
  quantidadeSobrepostasAoPeriodo: 1,
  quantidadeSemTerminoFisico: 0,
  quantidadeRestabelecimentosNoPeriodo: 1,
  quantidadeRegistrosInconsistentes: 0,
  tempoInterrompidoSegundos: 7200,
  tempoMedioRestabelecimentoSegundos: 7200,
  tempoObservadoAtivosSegundos: 36000,
  disponibilidadeEstimadaPercentual: 80,
  situacaoDisponibilidade: 'COBERTURA_NAO_AFERIDA',
  ...dados,
})

function renderizar(perfil: PerfilAcesso = 'GERENTE_OPERACOES') {
  const auth: AuthContextValue = { usuario: { id: 2, nome: 'G', email: 'g@x', perfil }, carregando: false, login: vi.fn(), logout: vi.fn() }
  render(
    <AuthContext.Provider value={auth}>
      <Dashboard />
    </AuthContext.Provider>
  )
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.setSystemTime(AGORA)
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([{ id: 1, nome: 'SE Norte' } as Subestacao])
  vi.mocked(indicadoresApi.consultarIndicadores).mockResolvedValue(indicadores({}))
})

afterEach(() => {
  vi.useRealTimers()
})

describe('indicadores operacionais', () => {
  it('ficam disponiveis apenas ao gerente', async () => {
    renderizar('OPERADOR')

    expect(await screen.findByText(/disponíveis para o perfil Gerente de Operações/)).toBeInTheDocument()
    expect(indicadoresApi.consultarIndicadores).not.toHaveBeenCalled()
  })

  it('consulta os ultimos 7 dias ate agora com todos os tipos', async () => {
    renderizar()
    await screen.findByLabelText('Resultado dos indicadores')

    const [filtros] = vi.mocked(indicadoresApi.consultarIndicadores).mock.calls[0]
    expect(filtros.dataInicio).toBe(inicioDoDiaLocal('2026-10-28'))
    expect(new Date(filtros.dataFim).getTime()).toBeLessThanOrEqual(Date.now())
    expect(filtros.tipos).toEqual(['SUBESTACAO', 'ALIMENTADOR', 'TRANSFORMADOR', 'LINHA', 'USINA'])
  })

  it('exibe o cenario conhecido: 2 h interrompidas em 10 h resultam em 80%', async () => {
    renderizar()

    const painel = await screen.findByLabelText('Resultado dos indicadores')
    expect(painel).toHaveTextContent('80,0%')
    expect(painel).toHaveTextContent('3 ativo(s) observado(s) por 10 h')
    expect(painel).toHaveTextContent('Tempo interrompido2 h')
    expect(painel).toHaveTextContent('Estimativa: a cobertura do monitoramento não é aferida.')

    const subestacoes = within(screen.getByRole('table', { name: 'Inventário por estado' })).getByRole('row', { name: /Subestações/ })
    expect(subestacoes).toHaveTextContent('Subestações210')
  })

  it('distingue falta de base e dados inconsistentes de indisponibilidade', async () => {
    vi.mocked(indicadoresApi.consultarIndicadores).mockResolvedValue(
      indicadores({ disponibilidadeEstimadaPercentual: null, situacaoDisponibilidade: 'DADOS_INCONSISTENTES', quantidadeRegistrosInconsistentes: 2, tempoMedioRestabelecimentoSegundos: null })
    )

    renderizar()

    const painel = await screen.findByLabelText('Resultado dos indicadores')
    expect(painel).toHaveTextContent('Disponibilidade estimada—')
    expect(painel).toHaveTextContent('Há registros de interrupção inconsistentes: a disponibilidade não é calculada. (2 registro(s))')
    expect(painel).toHaveTextContent('Sem restabelecimentos no período')
  })

  it('aplica filtros de subestacao e tipos e valida o periodo', async () => {
    renderizar()
    await screen.findByLabelText('Resultado dos indicadores')

    fireEvent.change(screen.getByLabelText('Subestação'), { target: { value: '1' } })
    await userEvent.click(screen.getByLabelText('Usinas'))
    expect(vi.mocked(indicadoresApi.consultarIndicadores).mock.calls.at(-1)?.[0]).toEqual(
      expect.objectContaining({ subestacaoId: 1, tipos: ['SUBESTACAO', 'ALIMENTADOR', 'TRANSFORMADOR', 'LINHA'] })
    )

    const chamadas = vi.mocked(indicadoresApi.consultarIndicadores).mock.calls.length
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-11-04' } })
    expect(screen.getByText('A data final deve ser igual ou posterior à inicial.')).toBeInTheDocument()
    expect(vi.mocked(indicadoresApi.consultarIndicadores).mock.calls.length).toBe(chamadas)
  })

  it('formata duracoes em horas e minutos', () => {
    expect(formatarDuracao(7200)).toBe('2 h')
    expect(formatarDuracao(5400)).toBe('1 h 30 min')
    expect(formatarDuracao(90)).toBe('2 min')
  })
})
