import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as alimentadoresApi from '../../services/alimentadores'
import * as subestacoesApi from '../../services/subestacoes'
import type { Alimentador, Subestacao } from '../../types'
import Alimentadores from '.'

vi.mock('../../services/alimentadores')
vi.mock('../../services/subestacoes')

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

const alimentador: Alimentador = {
  id: 10,
  nome: 'AL Central',
  latitude: -25.4,
  longitude: -49.2,
  tensaoNominalKv: 13.8,
  subestacaoId: 1,
  dataInstalacao: '2024-03-20',
  estadoOperacional: 'FALHA',
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

function erroHttp(status: number, message: string) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, {
    config,
    status,
    data: { message, fieldErrors: [] },
    statusText: '',
    headers: {},
  })
}

function preencherValidos() {
  preencher('Nome', ' AL Norte ')
  preencher('Subestação', '2')
  preencher('Latitude', '-25,5')
  preencher('Longitude', '-49.5')
  preencher('Tensão nominal (kV)', '13.8')
  preencher('Data de instalação', '2024-02-01')
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([alimentador])
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([subestacao(1, 'SE Central'), subestacao(2, 'SE Norte')])
})

describe('listagem', () => {
  it('resolve o nome da subestacao vinculada', async () => {
    render(<Alimentadores />)

    const linha = (await screen.findByText('AL Central')).closest('tr')!
    expect(within(linha).getByText('SE Central')).toBeInTheDocument()
    expect(within(linha).getByText('20/03/2024')).toBeInTheDocument()
    expect(within(linha).getByText('Falha')).toBeInTheDocument()
  })

  it('busca pelo nome da subestacao', async () => {
    render(<Alimentadores />)
    await screen.findByText('AL Central')

    await userEvent.type(screen.getByPlaceholderText('Buscar alimentador ou subestação...'), 'norte')

    expect(screen.queryByText('AL Central')).not.toBeInTheDocument()
    expect(screen.getByText('Nenhum alimentador encontrado.')).toBeInTheDocument()
  })

  it('exibe erro quando as subestacoes nao carregam', async () => {
    vi.mocked(subestacoesApi.listarSubestacoes).mockRejectedValue(new Error('rede'))

    render(<Alimentadores />)

    expect(await screen.findByRole('button', { name: 'Tentar novamente' })).toBeInTheDocument()
  })
})

describe('cadastro', () => {
  it('exige subestacao e demais campos', async () => {
    render(<Alimentadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo alimentador' }))
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(6)
    expect(alimentadoresApi.criarAlimentador).not.toHaveBeenCalled()
  })

  it('orienta quando nao ha subestacoes cadastradas', async () => {
    vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([])
    vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([])

    render(<Alimentadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo alimentador' }))

    expect(screen.getByRole('option', { name: 'Cadastre uma subestação antes' })).toBeInTheDocument()
  })

  it('envia subestacaoId numerico e dados normalizados', async () => {
    vi.mocked(alimentadoresApi.criarAlimentador).mockResolvedValue({ ...alimentador, id: 11, nome: 'AL Norte', subestacaoId: 2 })

    render(<Alimentadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo alimentador' }))
    preencherValidos()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('AL Norte')).toBeInTheDocument()
    expect(alimentadoresApi.criarAlimentador).toHaveBeenCalledWith({
      nome: 'AL Norte',
      subestacaoId: 2,
      latitude: '-25.5',
      longitude: '-49.5',
      tensaoNominalKv: '13.8',
      dataInstalacao: '2024-02-01',
      estadoOperacional: 'OPERACIONAL',
    })
  })

  it('mostra a recusa do servidor para subestacao inexistente', async () => {
    vi.mocked(alimentadoresApi.criarAlimentador).mockRejectedValue(erroHttp(404, 'Subestação não encontrada'))

    render(<Alimentadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo alimentador' }))
    preencherValidos()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Subestação não encontrada')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})

describe('edicao', () => {
  it('permite trocar a subestacao preservando o status', async () => {
    vi.mocked(alimentadoresApi.atualizarAlimentador).mockResolvedValue({ ...alimentador, subestacaoId: 2 })

    render(<Alimentadores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar AL Central' }))

    expect(screen.getByLabelText('Subestação')).toHaveValue('1')
    preencher('Subestação', '2')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    const linha = (await screen.findByText('AL Central')).closest('tr')!
    expect(await within(linha).findByText('SE Norte')).toBeInTheDocument()
    expect(alimentadoresApi.atualizarAlimentador).toHaveBeenCalledWith(
      10,
      expect.objectContaining({ subestacaoId: 2, estadoOperacional: 'FALHA' })
    )
  })
})
