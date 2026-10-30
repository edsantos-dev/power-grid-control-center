import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as subestacoesApi from '../../services/subestacoes'
import type { Subestacao } from '../../types'
import Subestacoes from '.'

vi.mock('../../services/subestacoes')

const central: Subestacao = {
  id: 1,
  nome: 'Subestação Central',
  latitude: -25.4284001,
  longitude: -49.2733001,
  tensaoNominalKv: 138,
  capacidadeMva: 100.5,
  dataInstalacao: '2024-01-15',
  estadoOperacional: 'MANUTENCAO',
}

function erroHttp(status: number, data: unknown) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, {
    config,
    status,
    data,
    statusText: '',
    headers: {},
  })
}

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

function preencherValidos() {
  preencher('Nome', '  Subestação Norte ')
  preencher('Latitude', '-25,5')
  preencher('Longitude', '-49.5')
  preencher('Tensão nominal (kV)', '69')
  preencher('Capacidade (MVA)', '1234567890123.123456')
  preencher('Data de instalação', '2024-02-01')
}

async function abrirNovo() {
  await userEvent.click(await screen.findByRole('button', { name: 'Nova subestação' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([central])
})

describe('listagem', () => {
  it('exibe os dados da API, sem campos simulados', async () => {
    render(<Subestacoes />)

    const linha = (await screen.findByText('Subestação Central')).closest('tr')!
    expect(within(linha).getByText('15/01/2024')).toBeInTheDocument()
    expect(within(linha).getByText('Manutenção')).toBeInTheDocument()
    expect(within(linha).getByText('-25.4284001, -49.2733001')).toBeInTheDocument()
    expect(screen.queryByText('Disponibilidade')).not.toBeInTheDocument()
  })

  it('exibe estado vazio', async () => {
    vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([])

    render(<Subestacoes />)

    expect(await screen.findByText('Nenhuma subestação encontrada.')).toBeInTheDocument()
  })

  it('permite tentar novamente depois de erro na consulta', async () => {
    vi.mocked(subestacoesApi.listarSubestacoes).mockRejectedValueOnce(new Error('rede'))

    render(<Subestacoes />)
    await userEvent.click(await screen.findByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Subestação Central')).toBeInTheDocument()
  })
})

describe('cadastro', () => {
  it('rejeita campos vazios sem chamar a API', async () => {
    render(<Subestacoes />)
    await abrirNovo()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(6)
    expect(subestacoesApi.criarSubestacao).not.toHaveBeenCalled()
  })

  it.each([
    ['Latitude', '90.1', 'Informe um valor entre -90 e 90.'],
    ['Longitude', '-180.5', 'Informe um valor entre -180 e 180.'],
    ['Latitude', '10.12345678', 'Informe um número com até sete casas decimais.'],
    ['Capacidade (MVA)', '0', 'Informe um valor maior que zero.'],
    ['Tensão nominal (kV)', '-1', 'Informe um número com até 13 dígitos inteiros e seis casas decimais.'],
  ])('rejeita %s igual a %s', async (rotulo, valor, mensagem) => {
    render(<Subestacoes />)
    await abrirNovo()
    preencherValidos()
    preencher(rotulo, valor)
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getByText(mensagem)).toBeInTheDocument()
    expect(subestacoesApi.criarSubestacao).not.toHaveBeenCalled()
  })

  it('envia dados normalizados e mostra a subestacao salva na lista', async () => {
    vi.mocked(subestacoesApi.criarSubestacao).mockResolvedValue({
      ...central,
      id: 2,
      nome: 'Subestação Norte',
    })

    render(<Subestacoes />)
    await abrirNovo()
    preencherValidos()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Subestação Norte')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(subestacoesApi.criarSubestacao).toHaveBeenCalledWith({
      nome: 'Subestação Norte',
      latitude: '-25.5',
      longitude: '-49.5',
      tensaoNominalKv: '69',
      capacidadeMva: '1234567890123.123456',
      dataInstalacao: '2024-02-01',
      estadoOperacional: 'OPERACIONAL',
    })
  })

  it('exibe erros de campo devolvidos pelo servidor e mantem o formulario', async () => {
    vi.mocked(subestacoesApi.criarSubestacao).mockRejectedValue(
      erroHttp(400, {
        message: 'Dados inválidos',
        fieldErrors: [{ field: 'nome', message: 'nome inválido' }],
      })
    )

    render(<Subestacoes />)
    await abrirNovo()
    preencherValidos()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('nome inválido')).toBeInTheDocument()
    expect(screen.getByText('Dados inválidos')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeEnabled()
  })
})

describe('edicao', () => {
  it('preenche o formulario, preserva o status e atualiza a linha', async () => {
    vi.mocked(subestacoesApi.atualizarSubestacao).mockResolvedValue({ ...central, nome: 'Subestação Renomeada' })

    render(<Subestacoes />)
    await userEvent.click(await screen.findByRole('button', { name: 'Editar Subestação Central' }))

    expect(screen.getByLabelText('Latitude')).toHaveValue('-25.4284001')
    expect(screen.getByLabelText('Status')).toHaveValue('MANUTENCAO')

    preencher('Nome', 'Subestação Renomeada')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Subestação Renomeada')).toBeInTheDocument()
    expect(screen.queryByText('Subestação Central')).not.toBeInTheDocument()
    expect(subestacoesApi.atualizarSubestacao).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ nome: 'Subestação Renomeada', estadoOperacional: 'MANUTENCAO' })
    )
  })
})
