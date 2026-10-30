import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as linhasApi from '../../services/linhas'
import * as subestacoesApi from '../../services/subestacoes'
import type { LinhaTransmissao, Subestacao } from '../../types'
import Linhas from '.'

vi.mock('../../services/linhas')
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

const linha: LinhaTransmissao = {
  id: 20,
  nome: 'LT Norte-Sul',
  latitude: null,
  longitude: null,
  tensaoOperacaoKv: 138,
  subestacaoOrigemId: 1,
  subestacaoDestinoId: 2,
  extensaoKm: 56.5,
  dataInstalacao: '2024-03-20',
  estadoOperacional: 'MANUTENCAO',
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

function preencherValidos() {
  preencher('Nome', ' LT Nova ')
  preencher('Subestação de origem', '1')
  preencher('Subestação de destino', '2')
  preencher('Tensão de operação (kV)', '69')
  preencher('Extensão (km)', '12,5')
  preencher('Data de instalação', '2024-02-01')
}

async function abrirNova() {
  await userEvent.click(await screen.findByRole('button', { name: 'Nova linha' }))
}

const salvar = () => userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(linhasApi.listarLinhas).mockResolvedValue([linha])
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([subestacao(1, 'SE Norte'), subestacao(2, 'SE Sul')])
})

describe('listagem', () => {
  it('exibe extremidades pelo nome e coordenada ausente sem inventar valor', async () => {
    render(<Linhas />)

    const row = (await screen.findByText('LT Norte-Sul')).closest('tr')!
    expect(within(row).getByText(/SE Norte/)).toBeInTheDocument()
    expect(within(row).getByText(/SE Sul/)).toBeInTheDocument()
    expect(within(row).getByText('Não informada')).toBeInTheDocument()
    expect(screen.queryByText('Corrente')).not.toBeInTheDocument()
  })

  it('soma a extensao das linhas', async () => {
    vi.mocked(linhasApi.listarLinhas).mockResolvedValue([linha, { ...linha, id: 21, extensaoKm: 10 }])

    render(<Linhas />)

    expect(await screen.findByText('66,5')).toBeInTheDocument()
  })
})

describe('cadastro', () => {
  it('exige os campos obrigatorios, mas nao as coordenadas', async () => {
    render(<Linhas />)
    await abrirNova()
    await salvar()

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(6)
    expect(linhasApi.criarLinha).not.toHaveBeenCalled()
  })

  it('rejeita origem igual ao destino', async () => {
    render(<Linhas />)
    await abrirNova()
    preencherValidos()
    preencher('Subestação de destino', '1')
    await salvar()

    expect(screen.getByText('Origem e destino devem ser subestações diferentes.')).toBeInTheDocument()
    expect(linhasApi.criarLinha).not.toHaveBeenCalled()
  })

  it('rejeita coordenada informada sozinha', async () => {
    render(<Linhas />)
    await abrirNova()
    preencherValidos()
    preencher('Latitude (opcional)', '-25.4')
    await salvar()

    expect(screen.getByText('Informe latitude e longitude em conjunto.')).toBeInTheDocument()
    expect(linhasApi.criarLinha).not.toHaveBeenCalled()
  })

  it('envia coordenadas vazias como nulas e ids numericos', async () => {
    vi.mocked(linhasApi.criarLinha).mockResolvedValue({ ...linha, id: 21, nome: 'LT Nova' })

    render(<Linhas />)
    await abrirNova()
    preencherValidos()
    await salvar()

    expect(await screen.findByText('LT Nova')).toBeInTheDocument()
    expect(linhasApi.criarLinha).toHaveBeenCalledWith({
      nome: 'LT Nova',
      latitude: null,
      longitude: null,
      tensaoOperacaoKv: '69',
      subestacaoOrigemId: 1,
      subestacaoDestinoId: 2,
      extensaoKm: '12.5',
      dataInstalacao: '2024-02-01',
      estadoOperacional: 'OPERACIONAL',
    })
  })
})

describe('edicao', () => {
  it('preenche coordenadas nulas como vazias e preserva o status', async () => {
    vi.mocked(linhasApi.atualizarLinha).mockResolvedValue({ ...linha, nome: 'LT Renomeada' })

    render(<Linhas />)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar LT Norte-Sul' }))

    expect(screen.getByLabelText('Latitude (opcional)')).toHaveValue('')
    preencher('Nome', 'LT Renomeada')
    await salvar()

    expect(await screen.findByText('LT Renomeada')).toBeInTheDocument()
    expect(linhasApi.atualizarLinha).toHaveBeenCalledWith(
      20,
      expect.objectContaining({ latitude: null, longitude: null, estadoOperacional: 'MANUTENCAO' })
    )
  })
})
