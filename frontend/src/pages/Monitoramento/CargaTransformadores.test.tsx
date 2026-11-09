import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as transformadoresApi from '../../services/transformadores'
import type { AmostraSnapshot, MedicaoTransformador, TransformadorSnapshot } from '../../types'
import CargaTransformadores from './CargaTransformadores'

vi.mock('../../services/transformadores')
vi.mock('./GraficoSerie', () => ({
  default: ({ titulo, pontos, limiar }: { titulo: string; pontos: unknown[]; limiar?: { rotulo: string } }) => (
    <p>{`${titulo}: ${pontos.length} · ${limiar?.rotulo}`}</p>
  ),
}))

const AGORA = new Date('2026-11-03T12:00:00Z').getTime()

function amostra(valor: number, dados: Partial<AmostraSnapshot> = {}): AmostraSnapshot {
  const medidoEm = new Date(AGORA - 60_000).toISOString()
  return { valor, medidoEm, recebidoEm: medidoEm, fonteId: 1, origemLeitura: 'SIMULADA', terminalReferenciaId: null, ...dados }
}

const transformador = (ativoId: number, nome: string, dados: Partial<TransformadorSnapshot> = {}): TransformadorSnapshot => ({
  ativoId,
  nome,
  tipoAtivo: 'TRANSFORMADOR',
  subestacaoId: 1,
  tensaoOperacao: 13.8,
  potenciaAparenteMva: null,
  capacidadeReferenciaMva: null,
  cargaPercentual: null,
  ...dados,
})

const transformadores = [
  transformador(1, 'T-01', { potenciaAparenteMva: amostra(48.6), capacidadeReferenciaMva: amostra(45), cargaPercentual: amostra(108) }),
  transformador(2, 'T-02', {
    potenciaAparenteMva: amostra(45, { medidoEm: new Date(AGORA - 600_000).toISOString(), origemLeitura: 'REAL' }),
    capacidadeReferenciaMva: amostra(45, { medidoEm: new Date(AGORA - 600_000).toISOString(), origemLeitura: 'REAL' }),
    cargaPercentual: amostra(100, { medidoEm: new Date(AGORA - 600_000).toISOString(), origemLeitura: 'REAL' }),
  }),
  transformador(3, 'T-03'),
]

const serie = (id: number): MedicaoTransformador => ({
  id: 1,
  transformadorId: id,
  fonteId: 1,
  origem: 'REAL',
  medidoEm: new Date(AGORA - 600_000).toISOString(),
  recebidoEm: new Date(AGORA - 600_000).toISOString(),
  potenciaAparenteMva: 45,
  capacidadeReferenciaMva: 45,
  cargaPercentual: 100,
})

function renderizar(extras: Partial<Parameters<typeof CargaTransformadores>[0]> = {}) {
  return render(<CargaTransformadores transformadores={transformadores} agora={AGORA} atualizadoEm={AGORA} carregando={false} erro="" {...extras} />)
}

async function linha(nome: string) {
  return (await screen.findByRole('button', { name: nome })).closest('tr')!
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(transformadoresApi.listarMedicoesTransformador).mockImplementation(async (id: number) => [serie(id)])
})

describe('carga dos transformadores', () => {
  it('exibe carga sem limite, potencia medida e capacidade de referencia da medicao', async () => {
    renderizar()

    const t1 = await linha('T-01')
    expect(within(t1).getByText('108,0%')).toBeInTheDocument()
    expect(within(t1).getByText('Sobrecarga')).toBeInTheDocument()
    expect(t1).toHaveTextContent('48,6 MVA de 45 MVA')
    expect(t1).toHaveTextContent('há 1 min · Simulada')
  })

  it('usa limiar estrito de 100% e marca atraso contra o relogio do servidor', async () => {
    renderizar()

    const t2 = await linha('T-02')
    expect(within(t2).getByText('Dentro da capacidade')).toBeInTheDocument()
    expect(within(t2).getByText('Atrasada')).toBeInTheDocument()
    expect(t2).toHaveTextContent('há 10 min · Real')
  })

  it('distingue transformador sem medicao de carga zero', async () => {
    renderizar()

    const t3 = await linha('T-03')
    expect(within(t3).getByText('Sem medição')).toBeInTheDocument()
    expect(within(t3).getByText('—')).toBeInTheDocument()
  })

  it('abre o grafico no primeiro transformador com medicao', async () => {
    renderizar({ transformadores: [transformador(5, 'T-sem-leitura'), ...transformadores] })

    expect(await screen.findByText('Carga de T-01: 1 · Limiar 100%')).toBeInTheDocument()
    expect(transformadoresApi.listarMedicoesTransformador).toHaveBeenCalledWith(1, 100)
  })

  it('so avisa que nao ha transformadores depois de carregar', async () => {
    const { rerender } = renderizar({ transformadores: [], carregando: true })
    expect(screen.queryByText('Nenhum transformador cadastrado.')).not.toBeInTheDocument()

    rerender(<CargaTransformadores transformadores={[]} agora={AGORA} atualizadoEm={AGORA} carregando={false} erro="" />)
    expect(screen.getByText('Nenhum transformador cadastrado.')).toBeInTheDocument()
  })

  it('exibe o erro quando nao ha leitura anterior', () => {
    renderizar({ transformadores: [], erro: 'Falha ao consultar.' })

    expect(screen.getByText('Falha ao consultar.')).toBeInTheDocument()
    expect(screen.queryByText('Nenhum transformador cadastrado.')).not.toBeInTheDocument()
  })

  it('mostra a serie com o limiar e consulta novamente a cada atualizacao', async () => {
    const { rerender } = renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'T-02' }))

    expect(await screen.findByText('Carga de T-02: 1 · Limiar 100%')).toBeInTheDocument()
    expect(transformadoresApi.listarMedicoesTransformador).toHaveBeenCalledWith(2, 100)

    const antes = vi.mocked(transformadoresApi.listarMedicoesTransformador).mock.calls.length
    rerender(<CargaTransformadores transformadores={transformadores} agora={AGORA} atualizadoEm={AGORA + 30_000} carregando={false} erro="" />)
    await screen.findByText('Carga de T-02: 1 · Limiar 100%')
    expect(vi.mocked(transformadoresApi.listarMedicoesTransformador).mock.calls.length).toBe(antes + 1)
  })
})
