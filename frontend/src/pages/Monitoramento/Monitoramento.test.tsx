import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as linhasApi from '../../services/linhas'
import * as monitoramentoApi from '../../services/monitoramento'
import * as subestacoesApi from '../../services/subestacoes'
import type { AmostraSnapshot, LinhaSnapshot, MedicaoLinha, SnapshotMonitoramento, Subestacao } from '../../types'
import Monitoramento from '.'
import { avaliarTensao, CADENCIA_MS, estaAtrasada, fatorPotenciaDoSnapshot, leituraDaAmostra } from './monitoramento'

vi.mock('../../services/linhas')
vi.mock('../../services/monitoramento')
vi.mock('../../services/subestacoes')
vi.mock('./CargaTransformadores', () => ({
  default: ({ transformadores, agora, erro }: { transformadores: { nome: string }[]; agora: number; erro: string }) => (
    <p>{`Transformadores: ${transformadores.map((t) => t.nome).join(',')} · agora ${agora} · erro${erro}`}</p>
  ),
}))
vi.mock('./GraficoSerie', () => ({
  default: ({ titulo, pontos }: { titulo: string; pontos: unknown[] }) => <p>{`Gráfico ${titulo}: ${pontos.length}`}</p>,
}))

const AGORA = new Date('2026-11-03T12:00:00Z').getTime()
const minutosAtras = (min: number) => new Date(AGORA - min * 60_000).toISOString()

function amostra(valor: number, dados: Partial<AmostraSnapshot> = {}): AmostraSnapshot {
  return { valor, medidoEm: minutosAtras(1), recebidoEm: minutosAtras(1), fonteId: 1, origemLeitura: 'SIMULADA', terminalReferenciaId: null, ...dados }
}

const linha = (ativoId: number, nome: string, dados: Partial<LinhaSnapshot> = {}): LinhaSnapshot => ({
  ativoId,
  nome,
  tipoAtivo: 'LINHA',
  subestacaoOrigemId: 1,
  subestacaoDestinoId: 2,
  tensaoOperacao: 138,
  tensaoKv: null,
  correnteA: null,
  frequenciaHz: null,
  potenciaAtivaMw: null,
  potenciaReativaMvar: null,
  fatorPotencia: null,
  situacaoFatorPotencia: 'DADOS_INSUFICIENTES',
  ...dados,
})

const snapshot = (linhas: LinhaSnapshot[]): SnapshotMonitoramento => ({
  consultadoEm: new Date(AGORA).toISOString(),
  linhas,
  transformadores: [{ ativoId: 9, nome: 'T-01', tipoAtivo: 'TRANSFORMADOR', subestacaoId: 1, tensaoOperacao: 13.8, potenciaAparenteMva: null, capacidadeReferenciaMva: null, cargaPercentual: null }],
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
  it('converte a amostra do snapshot na leitura com instante, origem e terminal proprios', () => {
    expect(leituraDaAmostra(amostra(137, { medidoEm: minutosAtras(2), origemLeitura: 'REAL', terminalReferenciaId: 3 }))).toEqual({
      valor: 137,
      medidoEm: minutosAtras(2),
      origem: 'REAL',
      subestacaoReferenciaId: 3,
    })
    expect(leituraDaAmostra(amostra(0))?.valor).toBe(0)
    expect(leituraDaAmostra(null)).toBeNull()
  })

  it('marca atraso estritamente apos 300 s', () => {
    expect(estaAtrasada(new Date(AGORA - 300_000).toISOString(), AGORA)).toBe(false)
    expect(estaAtrasada(new Date(AGORA - 300_001).toISOString(), AGORA)).toBe(true)
  })

  it('usa o fator de potencia calculado pelo servidor e nao inventa valor', () => {
    const calculado = linha(1, 'LT', { fatorPotencia: amostra(0.992278, { medidoEm: minutosAtras(3) }), situacaoFatorPotencia: 'CALCULADO' })
    const nulo = linha(2, 'LT', { potenciaAtivaMw: amostra(0, { medidoEm: minutosAtras(4) }), potenciaReativaMvar: amostra(0), situacaoFatorPotencia: 'POTENCIA_APARENTE_NULA' })

    expect(fatorPotenciaDoSnapshot(calculado)).toEqual({
      tipo: 'calculado',
      leitura: { valor: 0.992278, medidoEm: minutosAtras(3), origem: 'SIMULADA', subestacaoReferenciaId: null },
    })
    expect(fatorPotenciaDoSnapshot(nulo)).toEqual({ tipo: 'indefinido', medidoEm: minutosAtras(4) })
    expect(fatorPotenciaDoSnapshot(linha(3, 'LT'))).toBeNull()
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
    vi.mocked(monitoramentoApi.consultarSnapshot).mockResolvedValue(
      snapshot([
        linha(1, 'LT Norte-Sul', {
          tensaoKv: amostra(120, { terminalReferenciaId: 1 }),
          correnteA: amostra(0),
          potenciaAtivaMw: amostra(-35.5, { terminalReferenciaId: 1 }),
          potenciaReativaMvar: amostra(4.2, { terminalReferenciaId: 1 }),
        }),
        linha(2, 'LT Leste', { tensaoKv: amostra(138, { medidoEm: minutosAtras(12), origemLeitura: 'REAL' }) }),
      ])
    )
    vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([{ id: 1, nome: 'SE Norte' } as Subestacao])
    vi.mocked(linhasApi.listarMedicoesLinha).mockImplementation(async (id: number) =>
      id === 1 ? [medicao({ tensaoKv: 120 })] : [medicao({ linhaId: 2, tensaoKv: 138, medidoEm: minutosAtras(12), origem: 'REAL' })]
    )
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function linhaDaTabela(nome: string) {
    return (await screen.findByRole('button', { name: nome })).closest('tr')!
  }

  it('consulta uma unica vez o snapshot e entrega os transformadores a secao de carga', async () => {
    render(<Monitoramento />)

    await linhaDaTabela('LT Norte-Sul')
    expect(monitoramentoApi.consultarSnapshot).toHaveBeenCalledTimes(1)
    expect(await screen.findByText(`Transformadores: T-01 · agora ${AGORA} · erro`)).toBeInTheDocument()
  })

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
    expect(within(leste).getAllByText('Sem leitura')).toHaveLength(5)
    expect(leste).toHaveTextContent('há 12 min · Real')
  })

  it('exibe potencias com sinal, unidade e o terminal de referencia de cada grandeza', async () => {
    vi.mocked(monitoramentoApi.consultarSnapshot).mockResolvedValue(
      snapshot([
        linha(1, 'LT Norte-Sul', { potenciaAtivaMw: amostra(-35.5, { terminalReferenciaId: 1 }), potenciaReativaMvar: amostra(4.2, { terminalReferenciaId: 1 }) }),
        linha(2, 'LT Leste', { potenciaAtivaMw: amostra(0, { terminalReferenciaId: null }) }),
      ])
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

  it('cada grandeza mostra a propria idade quando as leituras chegam em instantes diferentes', async () => {
    vi.mocked(monitoramentoApi.consultarSnapshot).mockResolvedValue(
      snapshot([linha(1, 'LT Norte-Sul', { tensaoKv: amostra(138, { medidoEm: minutosAtras(1) }), correnteA: amostra(350, { medidoEm: minutosAtras(20), origemLeitura: 'REAL' }) })])
    )

    render(<Monitoramento />)

    const norteSul = await linhaDaTabela('LT Norte-Sul')
    expect(await within(norteSul).findByText('há 1 min · Simulada')).toBeInTheDocument()
    expect(within(norteSul).getByText('há 20 min · Real')).toBeInTheDocument()
    expect(within(norteSul).getAllByText('Atrasada')).toHaveLength(1)
  })

  it('exibe frequencia com referencia nominal e fator de potencia calculado ou indefinido', async () => {
    vi.mocked(monitoramentoApi.consultarSnapshot).mockResolvedValue(
      snapshot([
        linha(1, 'LT Norte-Sul', { frequenciaHz: amostra(59.94), fatorPotencia: amostra(0.992278), situacaoFatorPotencia: 'CALCULADO' }),
        linha(2, 'LT Leste', { potenciaAtivaMw: amostra(0), potenciaReativaMvar: amostra(0), situacaoFatorPotencia: 'POTENCIA_APARENTE_NULA' }),
      ])
    )

    render(<Monitoramento />)

    const norteSul = await linhaDaTabela('LT Norte-Sul')
    expect(await within(norteSul).findByText('Nominal 60 Hz')).toBeInTheDocument()
    expect(norteSul).toHaveTextContent('59,94 Hz')
    expect(norteSul).toHaveTextContent('0,992278')
    expect(within(norteSul).getByText('Calculado de P e Q')).toBeInTheDocument()

    const leste = await linhaDaTabela('LT Leste')
    expect(within(leste).getByText('Indefinido')).toBeInTheDocument()
    expect(within(leste).getByText('P e Q iguais a zero')).toBeInTheDocument()
  })

  it('mantem as leituras anteriores com o aviso quando uma atualizacao falha', async () => {
    render(<Monitoramento />)
    await linhaDaTabela('LT Norte-Sul')

    vi.mocked(monitoramentoApi.consultarSnapshot).mockRejectedValue(new Error('rede'))
    await userEvent.click(screen.getByRole('button', { name: 'Atualizar agora' }))

    expect(await screen.findByText('Não foi possível conectar ao servidor. Tente novamente.')).toBeInTheDocument()
    expect(within(await linhaDaTabela('LT Norte-Sul')).getByText('Subtensão')).toBeInTheDocument()
  })

  it('informa a falha e permite tentar novamente quando nao ha leitura anterior', async () => {
    vi.mocked(monitoramentoApi.consultarSnapshot).mockRejectedValueOnce(new Error('rede'))

    render(<Monitoramento />)

    expect(await screen.findByText('Não foi possível conectar ao servidor. Tente novamente.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }))
    expect(await linhaDaTabela('LT Norte-Sul')).toBeInTheDocument()
  })

  it('atualiza na cadencia e permite pausar', async () => {
    render(<Monitoramento />)
    await linhaDaTabela('LT Norte-Sul')
    const chamadas = () => vi.mocked(monitoramentoApi.consultarSnapshot).mock.calls.length
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

  it('abre o grafico na primeira linha com leitura, sem priorizar ativos sem dados', async () => {
    vi.mocked(monitoramentoApi.consultarSnapshot).mockResolvedValue(snapshot([linha(0, 'LT Demo'), linha(2, 'LT Leste', { tensaoKv: amostra(138) })]))

    render(<Monitoramento />)

    expect(await screen.findByRole('region', { name: 'Série de LT Leste' })).toBeInTheDocument()
    await vi.waitFor(() => expect(linhasApi.listarMedicoesLinha).toHaveBeenCalledWith(2, 100))
    expect(linhasApi.listarMedicoesLinha).not.toHaveBeenCalledWith(0, 100)
  })

  it('mostra a serie da linha selecionada, em ordem cronologica', async () => {
    render(<Monitoramento />)
    await userEvent.click(await screen.findByRole('button', { name: 'LT Leste' }))

    const serie = await screen.findByRole('region', { name: 'Série de LT Leste' })
    expect(serie).toHaveTextContent('Gráfico Tensão: 1')
    expect(serie).not.toHaveTextContent('Gráfico Corrente')
    expect(serie).toHaveTextContent('Sem leituras no período: corrente, potência ativa, potência reativa, frequência, fator de potência.')
    expect(linhasApi.listarMedicoesLinha).toHaveBeenCalledWith(2, 100)
  })
})
