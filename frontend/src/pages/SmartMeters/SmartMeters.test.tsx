import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as alimentadoresApi from '../../services/alimentadores'
import * as consumidoresApi from '../../services/consumidores'
import * as medidoresApi from '../../services/medidores'
import * as subestacoesApi from '../../services/subestacoes'
import * as transformadoresApi from '../../services/transformadores'
import type { Alimentador, Consumidor, Medidor, Subestacao, Transformador } from '../../types'
import Smartmeters from '.'
import { hojeLocal, validarInstalacao } from './validacao'

vi.mock('../../services/medidores')
vi.mock('../../services/consumidores')
vi.mock('../../services/transformadores')
vi.mock('../../services/alimentadores')
vi.mock('../../services/subestacoes')

const estoque: Medidor = {
  id: 50,
  nome: 'Medidor Novo',
  numeroSerie: 'sn-00A.1',
  estadoOperacional: 'OPERACIONAL',
  latitude: null,
  longitude: null,
  dataInstalacao: null,
  enderecoFornecimento: null,
  consumidorId: null,
  transformadorId: null,
  alimentadorId: null,
  subestacaoId: null,
}

const instalado: Medidor = {
  ...estoque,
  id: 51,
  nome: 'Medidor Casa',
  numeroSerie: 'SN-0001',
  latitude: -25.44,
  longitude: -49.27,
  dataInstalacao: '2024-05-10',
  enderecoFornecimento: 'Rua das Palmeiras, 120',
  consumidorId: 1,
  transformadorId: 30,
  alimentadorId: 10,
  subestacaoId: 1,
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

function preencher(rotulo: string, valor: string) {
  fireEvent.change(screen.getByLabelText(rotulo), { target: { value: valor } })
}

const salvar = () => userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

async function linhaDoMedidor(nome: string) {
  return (await screen.findByText(nome)).closest('tr')!
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(medidoresApi.listarMedidores).mockResolvedValue([estoque, instalado])
  vi.mocked(consumidoresApi.listarConsumidores).mockResolvedValue([
    { id: 1, nomeRazaoSocial: 'Maria Fernandes' } as Consumidor,
    { id: 2, nomeRazaoSocial: 'Mercado Central Ltda' } as Consumidor,
  ])
  vi.mocked(transformadoresApi.listarTransformadores).mockResolvedValue([
    { id: 30, nome: 'T-01', alimentadorId: 10, subestacaoId: 1 } as Transformador,
  ])
  vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([{ id: 10, nome: 'AL Centro' } as Alimentador])
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([{ id: 1, nome: 'SE Central' } as Subestacao])
})

describe('listagem', () => {
  it('distingue estoque de instalado sem usar estado operacional', async () => {
    render(<Smartmeters />)

    const linhaEstoque = await linhaDoMedidor('Medidor Novo')
    expect(within(linhaEstoque).getByText('Em estoque')).toBeInTheDocument()
    expect(within(linhaEstoque).getByText('Sem transformador')).toBeInTheDocument()
    expect(within(linhaEstoque).getByText('Operacional')).toBeInTheDocument()
    expect(within(linhaEstoque).getByRole('button', { name: 'Instalar Medidor Novo' })).toBeInTheDocument()

    const linhaInstalado = await linhaDoMedidor('Medidor Casa')
    expect(within(linhaInstalado).getByText('Maria Fernandes')).toBeInTheDocument()
    expect(within(linhaInstalado).getByText('Rua das Palmeiras, 120')).toBeInTheDocument()
    expect(within(linhaInstalado).getByText(/T-01/)).toHaveTextContent(/T-01.*AL Centro.*SE Central/)
    expect(within(linhaInstalado).queryByRole('button', { name: /Instalar/ })).not.toBeInTheDocument()
    expect(screen.queryByText('Online')).not.toBeInTheDocument()
  })

  it('filtra por situacao e busca pela serie', async () => {
    render(<Smartmeters />)
    await screen.findByText('Medidor Novo')

    await userEvent.click(screen.getByRole('button', { name: 'Instalados' }))
    expect(screen.queryByText('Medidor Novo')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Todos' }))
    await userEvent.type(screen.getByPlaceholderText('Buscar medidor, série ou consumidor...'), 'SN-00a')
    expect(screen.getByText('Medidor Novo')).toBeInTheDocument()
    expect(screen.queryByText('Medidor Casa')).not.toBeInTheDocument()
  })
})

describe('cadastro em estoque', () => {
  it('exige nome e serie', async () => {
    render(<Smartmeters />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo medidor' }))
    await salvar()

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(2)
    expect(medidoresApi.criarMedidor).not.toHaveBeenCalled()
  })

  it('preserva a capitalizacao e a pontuacao interna da serie', async () => {
    vi.mocked(medidoresApi.criarMedidor).mockResolvedValue({ ...estoque, id: 52, nome: 'Medidor B', numeroSerie: 'aB-0.01' })

    render(<Smartmeters />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo medidor' }))
    preencher('Nome', ' Medidor B ')
    preencher('Número de série', '  aB-0.01 ')
    await salvar()

    expect(await screen.findByText('Medidor B')).toBeInTheDocument()
    expect(medidoresApi.criarMedidor).toHaveBeenCalledWith({
      nome: 'Medidor B',
      numeroSerie: 'aB-0.01',
      estadoOperacional: 'OPERACIONAL',
    })
  })

  it('mostra a duplicidade recusada pelo servidor', async () => {
    vi.mocked(medidoresApi.criarMedidor).mockRejectedValue(erroHttp(409, 'Número de série já cadastrado.'))

    render(<Smartmeters />)
    await userEvent.click(await screen.findByRole('button', { name: 'Novo medidor' }))
    preencher('Nome', 'Repetido')
    preencher('Número de série', 'SN-0001')
    await salvar()

    expect(await screen.findByText('Número de série já cadastrado.')).toBeInTheDocument()
  })
})

describe('instalacao', () => {
  it('rejeita data futura sem chamar a API', () => {
    const erros = validarInstalacao(
      { consumidorId: '1', transformadorId: '30', latitude: '-25', longitude: '-49', dataInstalacao: '2026-11-04', enderecoFornecimento: '' },
      '2026-11-03'
    )

    expect(erros.dataInstalacao).toBe('A data de instalação não pode ser futura.')
    expect(validarInstalacao({ consumidorId: '1', transformadorId: '30', latitude: '-25', longitude: '-49', dataInstalacao: '2026-11-03', enderecoFornecimento: '' }, '2026-11-03')).toEqual({})
  })

  it('usa a data local para o limite', () => {
    expect(hojeLocal(new Date(2026, 0, 5, 23, 30))).toBe('2026-01-05')
  })

  it('exige titular, transformador, coordenadas e data', async () => {
    render(<Smartmeters />)
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Medidor Novo' }))
    await salvar()

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(5)
    expect(medidoresApi.instalarMedidor).not.toHaveBeenCalled()
  })

  it('envia o vinculo e mostra a cadeia derivada pelo transformador', async () => {
    vi.mocked(medidoresApi.instalarMedidor).mockResolvedValue({
      ...estoque,
      latitude: -25.5,
      longitude: -49.5,
      dataInstalacao: '2024-06-01',
      consumidorId: 2,
      transformadorId: 30,
      alimentadorId: 10,
      subestacaoId: 1,
    })

    render(<Smartmeters />)
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Medidor Novo' }))
    preencher('Consumidor titular', '2')
    preencher('Transformador', '30')
    expect(screen.getByText('Alimentador: AL Centro · Subestação: SE Central')).toBeInTheDocument()
    preencher('Latitude', '-25,5')
    preencher('Longitude', '-49.5')
    preencher('Data de instalação', '2024-06-01')
    await salvar()

    const linha = await linhaDoMedidor('Medidor Novo')
    expect(await within(linha).findByText('Mercado Central Ltda')).toBeInTheDocument()
    expect(within(linha).getByText('-25.5, -49.5')).toBeInTheDocument()
    expect(within(linha).queryByRole('button', { name: /Instalar/ })).not.toBeInTheDocument()
    expect(medidoresApi.instalarMedidor).toHaveBeenCalledWith(50, {
      consumidorId: 2,
      transformadorId: 30,
      latitude: '-25.5',
      longitude: '-49.5',
      dataInstalacao: '2024-06-01',
      enderecoFornecimento: null,
    })
  })

  it('mostra conflito quando o medidor ja foi instalado em outra sessao', async () => {
    vi.mocked(medidoresApi.instalarMedidor).mockRejectedValue(erroHttp(409, 'Medidor já possui vínculo de instalação.'))

    render(<Smartmeters />)
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Medidor Novo' }))
    preencher('Consumidor titular', '1')
    preencher('Transformador', '30')
    preencher('Latitude', '-25')
    preencher('Longitude', '-49')
    preencher('Data de instalação', '2024-06-01')
    await salvar()

    expect(await screen.findByText('Medidor já possui vínculo de instalação.')).toBeInTheDocument()
  })
})
