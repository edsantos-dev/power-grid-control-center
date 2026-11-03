import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as transformadoresApi from '../../services/transformadores'
import type { MedicaoTransformador, Transformador } from '../../types'
import CargaTransformadores from './CargaTransformadores'

vi.mock('../../services/transformadores')
vi.mock('./GraficoSerie', () => ({
  default: ({ titulo, pontos, limiar }: { titulo: string; pontos: unknown[]; limiar?: { rotulo: string } }) => (
    <p>{`${titulo}: ${pontos.length} · ${limiar?.rotulo}`}</p>
  ),
}))

const AGORA = new Date('2026-11-03T12:00:00Z').getTime()

const transformador = (id: number, nome: string): Transformador => ({
  id,
  nome,
  latitude: -25,
  longitude: -49,
  capacidadeMva: 45,
  alimentadorId: 1,
  subestacaoId: 1,
  dataInstalacao: '2024-01-01',
  estadoOperacional: 'OPERACIONAL',
})

const medicao = (dados: Partial<MedicaoTransformador>): MedicaoTransformador => ({
  id: 1,
  transformadorId: 1,
  fonteId: 1,
  origem: 'SIMULADA',
  medidoEm: new Date(AGORA - 60_000).toISOString(),
  recebidoEm: new Date(AGORA - 60_000).toISOString(),
  potenciaAparenteMva: 48.6,
  capacidadeReferenciaMva: 45,
  cargaPercentual: 108,
  ...dados,
})

async function linha(nome: string) {
  return (await screen.findByRole('button', { name: nome })).closest('tr')!
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(transformadoresApi.listarTransformadores).mockResolvedValue([transformador(1, 'T-01'), transformador(2, 'T-02'), transformador(3, 'T-03')])
  vi.mocked(transformadoresApi.listarMedicoesTransformador).mockImplementation(async (id: number) => {
    if (id === 1) return [medicao({})]
    if (id === 2) return [medicao({ transformadorId: 2, cargaPercentual: 100, potenciaAparenteMva: 45, medidoEm: new Date(AGORA - 600_000).toISOString(), origem: 'REAL' })]
    return []
  })
})

describe('carga dos transformadores', () => {
  it('exibe carga sem limite, potencia medida e capacidade de referencia da medicao', async () => {
    render(<CargaTransformadores atualizadoEm={AGORA} />)

    const t1 = await linha('T-01')
    expect(await within(t1).findByText('108,0%')).toBeInTheDocument()
    expect(within(t1).getByText('Sobrecarga')).toBeInTheDocument()
    expect(t1).toHaveTextContent('48,6 MVA de 45 MVA')
    expect(t1).toHaveTextContent('há 1 min · Simulada')
  })

  it('usa limiar estrito de 100% e marca atraso', async () => {
    render(<CargaTransformadores atualizadoEm={AGORA} />)

    const t2 = await linha('T-02')
    expect(await within(t2).findByText('Dentro da capacidade')).toBeInTheDocument()
    expect(within(t2).getByText('Atrasada')).toBeInTheDocument()
  })

  it('distingue transformador sem medicao de carga zero', async () => {
    render(<CargaTransformadores atualizadoEm={AGORA} />)

    expect(await within(await linha('T-03')).findByText('Sem medição')).toBeInTheDocument()
  })

  it('mostra a serie com o limiar e consulta novamente a cada atualizacao', async () => {
    const { rerender } = render(<CargaTransformadores atualizadoEm={AGORA} />)
    await userEvent.click(await screen.findByRole('button', { name: 'T-02' }))

    expect(await screen.findByText('Carga de T-02: 1 · Limiar 100%')).toBeInTheDocument()
    expect(transformadoresApi.listarMedicoesTransformador).toHaveBeenCalledWith(2, 100)

    const antes = vi.mocked(transformadoresApi.listarTransformadores).mock.calls.length
    rerender(<CargaTransformadores atualizadoEm={AGORA + 30_000} />)
    await screen.findByText('Carga de T-02: 1 · Limiar 100%')
    expect(vi.mocked(transformadoresApi.listarTransformadores).mock.calls.length).toBe(antes + 1)
  })
})
