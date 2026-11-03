import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as linhasApi from '../../services/linhas'
import * as subestacoesApi from '../../services/subestacoes'
import type { LinhaTransmissao, MedicaoLinha, Subestacao } from '../../types'
import Monitoramento from '.'
import { avaliarTensao, CADENCIA_MS, estaAtrasada, ultimaLeitura } from './monitoramento'

vi.mock('../../services/linhas')
vi.mock('../../services/subestacoes')
vi.mock('./GraficoSerie', () => ({
  default: ({ titulo, pontos }: { titulo: string; pontos: unknown[] }) => <p>{`Gráfico ${titulo}: ${pontos.length}`}</p>,
}))

const AGORA = new Date('2026-11-03T12:00:00Z').getTime()
const minutosAtras = (min: number) => new Date(AGORA - min * 60_000).toISOString()

const linha = (id: number, nome: string): LinhaTransmissao => ({
  id,
  nome,
  latitude: null,
  longitude: null,
  tensaoOperacaoKv: 138,
  subestacaoOrigemId: 1,
  subestacaoDestinoId: 2,
  extensaoKm: 50,
  dataInstalacao: '2024-01-01',
  estadoOperacional: 'OPERACIONAL',
})

const medicao = (dados: Partial<MedicaoLinha>): MedicaoLinha => ({
  id: 1,
  linhaId: 1,
  fonteId: 1,
  origem: 'SIMULADA',
  medidoEm: minutosAtras(1),
  recebidoEm: minutosAtras(1),
  tensaoKv: null,
  correnteA: null,
  potenciaAtivaMw: null,
  potenciaReativaMvar: null,
  subestacaoReferenciaId: null,
  frequenciaHz: null,
  fatorPotencia: null,
  situacaoFatorPotencia: 'DADOS_INSUFICIENTES',
  ...dados,
})

describe('regras de leitura', () => {
  it('usa a leitura mais recente que contem a grandeza', () => {
    const lista = [medicao({ id: 3, correnteA: 400 }), medicao({ id: 2, tensaoKv: 137, medidoEm: minutosAtras(2) })]

    expect(ultimaLeitura(lista, 'tensaoKv')).toEqual({ valor: 137, medidoEm: minutosAtras(2), origem: 'SIMULADA', subestacaoReferenciaId: null })
    expect(ultimaLeitura(lista, 'correnteA')?.valor).toBe(400)
    expect(ultimaLeitura([medicao({ tensaoKv: 0 })], 'tensaoKv')?.valor).toBe(0)
    expect(ultimaLeitura(lista, 'frequenciaHz')).toBeNull()
  })

  it('marca atraso estritamente apos 300 s', () => {
    expect(estaAtrasada(new Date(AGORA - 300_000).toISOString(), AGORA)).toBe(false)
    expect(estaAtrasada(new Date(AGORA - 300_001).toISOString(), AGORA)).toBe(true)
  })

  it('avalia a faixa de tensao com fronteiras inclusivas', () => {
    expect(avaliarTensao(138, 138)).toBe('NORMAL')
    expect(avaliarTensao(131.1, 138)).toBe('SUBTENSAO')
    expect(avaliarTensao(144.9, 138)).toBe('SOBRETENSAO')
    expect(avaliarTensao(131.2, 138)).toBe('NORMAL')
  })
})

describe('monitoramento de linhas', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(AGORA)
    vi.mocked(linhasApi.listarLinhas).mockResolvedValue([linha(1, 'LT Norte-Sul'), linha(2, 'LT Leste')])
    vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([{ id: 1, nome: 'SE Norte' } as Subestacao])
    vi.mocked(linhasApi.listarMedicoesLinha).mockImplementation(async (id: number) =>
      id === 1
        ? [medicao({ tensaoKv: 120, correnteA: 0, potenciaAtivaMw: -35.5, potenciaReativaMvar: 4.2, subestacaoReferenciaId: 1 })]
        : [medicao({ linhaId: 2, tensaoKv: 138, medidoEm: minutosAtras(12), origem: 'REAL' })]
    )
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function linhaDaTabela(nome: string) {
    return (await screen.findByRole('button', { name: nome })).closest('tr')!
  }

  it('exibe tensao e corrente com unidade, idade, origem e situacao', async () => {
    render(<Monitoramento />)

    const norteSul = await linhaDaTabela('LT Norte-Sul')
    expect(await within(norteSul).findByText('Subtensão')).toBeInTheDocument()
    expect(norteSul).toHaveTextContent('120 kV')
    expect(norteSul).toHaveTextContent('0 A')
    expect(norteSul).toHaveTextContent('Simulada')
    expect(within(norteSul).queryByText('Atrasada')).not.toBeInTheDocument()

    const leste = await linhaDaTabela('LT Leste')
    expect(within(leste).getByText('Normal')).toBeInTheDocument()
    expect(within(leste).getByText('Atrasada')).toBeInTheDocument()
    expect(within(leste).getAllByText('Sem leitura')).toHaveLength(3)
    expect(leste).toHaveTextContent('há 12 min · Real')
  })

  it('exibe potencias com sinal, unidade e terminal de referencia', async () => {
    vi.mocked(linhasApi.listarMedicoesLinha).mockImplementation(async (id: number) =>
      id === 1
        ? [medicao({ potenciaAtivaMw: -35.5, potenciaReativaMvar: 4.2, subestacaoReferenciaId: 1 })]
        : [medicao({ potenciaAtivaMw: 0, subestacaoReferenciaId: null })]
    )

    render(<Monitoramento />)

    const norteSul = await linhaDaTabela('LT Norte-Sul')
    expect(await within(norteSul).findAllByText('Referência: SE Norte')).toHaveLength(2)
    expect(norteSul).toHaveTextContent('-35,5 MW')
    expect(norteSul).toHaveTextContent('4,2 MVAr')

    const leste = await linhaDaTabela('LT Leste')
    expect(leste).toHaveTextContent('0 MW')
    expect(within(leste).getByText('Sem terminal de referência')).toBeInTheDocument()
    expect(screen.getByText(/Potências positivas indicam fluxo entrando na linha/)).toBeInTheDocument()
  })

  it('mantem as demais linhas quando uma consulta falha', async () => {
    vi.mocked(linhasApi.listarMedicoesLinha).mockImplementation(async (id: number) => {
      if (id === 2) throw new Error('rede')
      return [medicao({ tensaoKv: 138 })]
    })

    render(<Monitoramento />)

    expect((await within(await linhaDaTabela('LT Leste')).findAllByText('Indisponível')).length).toBe(4)
    expect(within(await linhaDaTabela('LT Norte-Sul')).getByText('Normal')).toBeInTheDocument()
  })

  it('atualiza na cadencia e permite pausar', async () => {
    render(<Monitoramento />)
    await linhaDaTabela('LT Norte-Sul')
    const chamadas = () => vi.mocked(linhasApi.listarLinhas).mock.calls.length
    const antes = chamadas()

    await act(async () => {
      vi.advanceTimersByTime(CADENCIA_MS)
    })
    expect(chamadas()).toBe(antes + 1)

    await userEvent.click(screen.getByLabelText(/Atualizar a cada 30 s/))
    await act(async () => {
      vi.advanceTimersByTime(CADENCIA_MS * 2)
    })
    expect(chamadas()).toBe(antes + 1)
  })

  it('mostra a serie da linha selecionada, em ordem cronologica', async () => {
    render(<Monitoramento />)
    await userEvent.click(await screen.findByRole('button', { name: 'LT Leste' }))

    const serie = await screen.findByRole('region', { name: 'Série de LT Leste' })
    expect(serie).toHaveTextContent('Gráfico Tensão: 1')
    expect(serie).toHaveTextContent('Gráfico Potência ativa: 1')
    expect(serie).toHaveTextContent('Gráfico Potência reativa: 1')
    expect(linhasApi.listarMedicoesLinha).toHaveBeenCalledWith(2, 100)
  })
})
