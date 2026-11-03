import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as alimentadoresApi from '../../services/alimentadores'
import * as subestacoesApi from '../../services/subestacoes'
import * as transformadoresApi from '../../services/transformadores'
import type { Alimentador, MedicaoTransformador, Subestacao, Transformador } from '../../types'
import Transformadores from '.'

vi.mock('../../services/alimentadores')
vi.mock('../../services/subestacoes')
vi.mock('../../services/transformadores')

const subestacao = (id: number, nome: string): Subestacao => ({
  id,
  nome,
  latitude: -25,
  longitude: -49,
  tensaoNominalKv: 138,
  capacidadeMva: 100,
  dataInstalacao: '2024-01-01',
  estadoOperacional: 'OPERACIONAL',
})

const alimentador = (id: number, nome: string, subestacaoId: number): Alimentador => ({
  id,
  nome,
  latitude: -25,
  longitude: -49,
  tensaoNominalKv: 13.8,
  subestacaoId,
  dataInstalacao: '2024-01-01',
  estadoOperacional: 'OPERACIONAL',
})

const transformador: Transformador = {
  id: 30,
  nome: 'T-01',
  latitude: -25.4,
  longitude: -49.2,
  capacidadeMva: 45,
  alimentadorId: 10,
  subestacaoId: 1,
  dataInstalacao: '2024-03-20',
  estadoOperacional: 'MANUTENCAO',
}

const medicao = (cargaPercentual: number): MedicaoTransformador => ({
  id: 1,
  transformadorId: 30,
  fonteId: 1,
  origem: 'SIMULADA',
  medidoEm: '2026-11-01T12:00:00Z',
  recebidoEm: '2026-11-01T12:00:01Z',
  potenciaAparenteMva: 48.6,
  capacidadeReferenciaMva: 45,
  cargaPercentual,
})

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

async function linhaDoTransformador(nome: string) {
  return (await screen.findByText(nome)).closest('tr')!
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(transformadoresApi.listarTransformadores).mockResolvedValue([transformador])
  vi.mocked(transformadoresApi.consultarUltimaMedicao).mockResolvedValue(medicao(108))
  vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([
    alimentador(10, 'AL Centro', 1),
    alimentador(11, 'AL Norte', 2),
  ])
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([subestacao(1, 'SE Central'), subestacao(2, 'SE Norte')])
})

describe('listagem', () => {
  it('exibe alimentador e subestacao derivada pelo nome', async () => {
    render(<Transformadores />)

    const linha = await linhaDoTransformador('T-01')
    expect(within(linha).getByText('AL Centro')).toBeInTheDocument()
    expect(within(linha).getByText('SE Central')).toBeInTheDocument()
    expect(within(linha).getByText('Manutenção')).toBeInTheDocument()
    expect(screen.queryByText('Temperatura')).not.toBeInTheDocument()
  })

  it('exibe carga acima de 100% sem limitar o valor, com origem', async () => {
    render(<Transformadores />)

    const linha = await linhaDoTransformador('T-01')
    expect(await within(linha).findByText('108,0%')).toHaveClass('text-red-400')
    expect(within(linha).getByText(/Simulada/)).toBeInTheDocument()
    expect(transformadoresApi.consultarUltimaMedicao).toHaveBeenCalledWith(30)
  })

  it('distingue transformador sem medicao de carga zero', async () => {
    vi.mocked(transformadoresApi.listarTransformadores).mockResolvedValue([transformador, { ...transformador, id: 31, nome: 'T-02' }])
    vi.mocked(transformadoresApi.consultarUltimaMedicao).mockImplementation(async (id) => (id === 30 ? null : medicao(0)))

    render(<Transformadores />)

    expect(await within(await linhaDoTransformador('T-01')).findByText('Sem medição')).toBeInTheDocument()
    expect(await within(await linhaDoTransformador('T-02')).findByText('0,0%')).toBeInTheDocument()
  })

  it('mantem o cadastro visivel quando a medicao falha', async () => {
    vi.mocked(transformadoresApi.consultarUltimaMedicao).mockRejectedValue(new Error('rede'))

    render(<Transformadores />)

    expect(await within(await linhaDoTransformador('T-01')).findByText('Indisponível')).toBeInTheDocument()
  })
})

describe('cadastro', () => {
  it('exige alimentador, aceita somente capacidade positiva e nao chama a API', async () => {
    render(<Transformadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo transformador' }))
    preencher('Capacidade (MVA)', '0')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(5)
    expect(screen.getByText('Informe um valor maior que zero.')).toBeInTheDocument()
    expect(transformadoresApi.criarTransformador).not.toHaveBeenCalled()
  })

  it('mostra a subestacao derivada e nao envia subestacaoId', async () => {
    vi.mocked(transformadoresApi.criarTransformador).mockResolvedValue({
      ...transformador,
      id: 32,
      nome: 'T-03',
      alimentadorId: 11,
      subestacaoId: 2,
    })
    vi.mocked(transformadoresApi.consultarUltimaMedicao).mockResolvedValue(null)

    render(<Transformadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo transformador' }))
    preencher('Nome', ' T-03 ')
    preencher('Alimentador', '11')
    expect(screen.getByText('Subestação: SE Norte')).toBeInTheDocument()
    preencher('Latitude', '-25.5')
    preencher('Longitude', '-49,5')
    preencher('Capacidade (MVA)', '30')
    preencher('Data de instalação', '2024-02-01')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const linha = await linhaDoTransformador('T-03')
    expect(within(linha).getByText('SE Norte')).toBeInTheDocument()
    expect(transformadoresApi.criarTransformador).toHaveBeenCalledWith({
      nome: 'T-03',
      alimentadorId: 11,
      latitude: '-25.5',
      longitude: '-49.5',
      capacidadeMva: '30',
      dataInstalacao: '2024-02-01',
      estadoOperacional: 'OPERACIONAL',
    })
    expect(await within(linha).findByText('Sem medição')).toBeInTheDocument()
  })
})

describe('edicao', () => {
  it('troca o alimentador, atualiza a subestacao derivada e preserva o status', async () => {
    vi.mocked(transformadoresApi.atualizarTransformador).mockResolvedValue({ ...transformador, alimentadorId: 11, subestacaoId: 2 })

    render(<Transformadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar T-01' }))
    expect(screen.getByLabelText('Alimentador')).toHaveValue('10')
    preencher('Alimentador', '11')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const linha = await linhaDoTransformador('T-01')
    expect(await within(linha).findByText('SE Norte')).toBeInTheDocument()
    expect(transformadoresApi.atualizarTransformador).toHaveBeenCalledWith(
      30,
      expect.objectContaining({ alimentadorId: 11, estadoOperacional: 'MANUTENCAO' })
    )
  })
})
