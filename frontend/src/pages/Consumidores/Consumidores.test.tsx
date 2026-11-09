import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render } from '../../test/auth'
import * as alimentadoresApi from '../../services/alimentadores'
import * as consumidoresApi from '../../services/consumidores'
import * as subestacoesApi from '../../services/subestacoes'
import * as transformadoresApi from '../../services/transformadores'
import type { Alimentador, Consumidor, Medidor, Subestacao, Transformador } from '../../types'
import Consumidores from '.'

vi.mock('../../services/consumidores')
vi.mock('../../services/transformadores')
vi.mock('../../services/alimentadores')
vi.mock('../../services/subestacoes')

const maria: Consumidor = {
  id: 1,
  nomeRazaoSocial: 'Maria Fernandes',
  tipoPessoa: 'PF',
}

const mercado: Consumidor = {
  id: 2,
  nomeRazaoSocial: 'Mercado Central Ltda',
  tipoPessoa: 'PJ',
}

const medidor = (dados: Partial<Medidor>): Medidor => ({
  id: 50,
  nome: 'Medidor Casa',
  numeroSerie: 'SN-0001',
  estadoOperacional: 'OPERACIONAL',
  latitude: -25.44,
  longitude: -49.27,
  dataInstalacao: '2024-05-10',
  enderecoFornecimento: null,
  consumidorId: 1,
  transformadorId: 30,
  alimentadorId: 10,
  subestacaoId: 1,
  ...dados,
})

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

async function abrirNovo() {
  await userEvent.click(await screen.findByRole('button', { name: 'Novo consumidor' }))
}

const salvar = () => userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(consumidoresApi.listarConsumidores).mockResolvedValue([maria, mercado])
  vi.mocked(transformadoresApi.listarTransformadores).mockResolvedValue([{ id: 30, nome: 'T-01' } as Transformador])
  vi.mocked(alimentadoresApi.listarAlimentadores).mockResolvedValue([{ id: 10, nome: 'AL Centro' } as Alimentador])
  vi.mocked(subestacoesApi.listarSubestacoes).mockResolvedValue([{ id: 1, nome: 'SE Central' } as Subestacao])
})

describe('listagem', () => {
  it('lista os resumos sem colunas de documento ou endereco', async () => {
    render(<Consumidores />)

    const linhaMaria = (await screen.findByText('Maria Fernandes')).closest('tr')!
    expect(within(linhaMaria).getByText('Pessoa física')).toBeInTheDocument()

    const linhaMercado = screen.getByText('Mercado Central Ltda').closest('tr')!
    expect(within(linhaMercado).getByText('Pessoa jurídica')).toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Documento' })).not.toBeInTheDocument()
    expect(screen.queryByRole('columnheader', { name: 'Endereço de correspondência' })).not.toBeInTheDocument()
  })

  it('busca pelo nome do consumidor', async () => {
    render(<Consumidores />)
    await screen.findByText('Maria Fernandes')

    await userEvent.type(screen.getByPlaceholderText('Buscar por nome...'), 'Fernandes')

    expect(screen.getByText('Maria Fernandes')).toBeInTheDocument()
    expect(screen.queryByText('Mercado Central Ltda')).not.toBeInTheDocument()
  })

  it('filtra por tipo de pessoa', async () => {
    render(<Consumidores />)
    await screen.findByText('Maria Fernandes')

    await userEvent.click(screen.getByRole('button', { name: 'Pessoa jurídica' }))

    expect(screen.queryByText('Maria Fernandes')).not.toBeInTheDocument()
    expect(screen.getByText('Mercado Central Ltda')).toBeInTheDocument()
  })
})

describe('cadastro', () => {
  it('exige tipo, documento e nome sem chamar a API', async () => {
    render(<Consumidores />)
    await abrirNovo()
    await salvar()

    expect(screen.getAllByText('Campo obrigatório.')).toHaveLength(3)
    expect(consumidoresApi.criarConsumidor).not.toHaveBeenCalled()
  })

  it.each([
    ['PF', '123.456.789', 'Informe um CPF com 11 dígitos.'],
    ['PJ', '11.222.333/0001', 'Informe um CNPJ com 14 posições.'],
  ])('rejeita documento %s incompleto', async (tipo, documento, mensagem) => {
    render(<Consumidores />)
    await abrirNovo()
    preencher('Tipo de pessoa', tipo)
    preencher(tipo === 'PF' ? 'CPF' : 'CNPJ', documento)
    await salvar()

    expect(screen.getByText(mensagem)).toBeInTheDocument()
    expect(consumidoresApi.criarConsumidor).not.toHaveBeenCalled()
  })

  it('envia documento sem mascara, em maiusculas, e endereco vazio como nulo', async () => {
    vi.mocked(consumidoresApi.criarConsumidor).mockResolvedValue({ ...mercado, id: 3, nomeRazaoSocial: 'Nova Empresa' })

    render(<Consumidores />)
    await abrirNovo()
    preencher('Tipo de pessoa', 'PJ')
    preencher('CNPJ', '12.abc.345/01de-35')
    preencher('Razão social', '  Nova Empresa ')
    preencher('Endereço de correspondência (opcional)', '   ')
    await salvar()

    expect(await screen.findByText('Nova Empresa')).toBeInTheDocument()
    expect(consumidoresApi.criarConsumidor).toHaveBeenCalledWith({
      nomeRazaoSocial: 'Nova Empresa',
      tipoPessoa: 'PJ',
      documento: '12ABC34501DE35',
      enderecoCorrespondencia: null,
    })
  })

  it('mostra duplicidade recusada pelo servidor e mantem o formulario', async () => {
    vi.mocked(consumidoresApi.criarConsumidor).mockRejectedValue(erroHttp(409, 'Documento já cadastrado'))

    render(<Consumidores />)
    await abrirNovo()
    preencher('Tipo de pessoa', 'PF')
    preencher('CPF', '529.982.247-25')
    preencher('Nome', 'Outra Maria')
    await salvar()

    expect(await screen.findByText('Documento já cadastrado')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Salvar' })).toBeEnabled()
  })
})

describe('medidores do consumidor', () => {
  it('mostra a cadeia transformador, alimentador e subestacao e a localizacao do fornecimento', async () => {
    vi.mocked(consumidoresApi.listarMedidoresDoConsumidor).mockResolvedValue([
      medidor({}),
      medidor({ id: 51, nome: 'Medidor Galpão', numeroSerie: 'SN-0002', enderecoFornecimento: 'Rua A, 10' }),
    ])

    render(<Consumidores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver medidores de Maria Fernandes' }))

    const dialogo = screen.getByRole('dialog', { name: 'Medidores de Maria Fernandes' })
    const casa = (await within(dialogo).findByText('Medidor Casa')).closest('tr')!
    expect(within(casa).getByText(/T-01/)).toBeInTheDocument()
    expect(within(casa).getByText(/AL Centro/)).toBeInTheDocument()
    expect(within(casa).getByText(/SE Central/)).toBeInTheDocument()
    expect(within(casa).getByText('-25.44, -49.27')).toBeInTheDocument()
    expect(within(dialogo).getByText('Rua A, 10')).toBeInTheDocument()
    expect(consumidoresApi.listarMedidoresDoConsumidor).toHaveBeenCalledWith(1)
  })

  it('indica medidor sem transformador sem inventar vinculo', async () => {
    vi.mocked(consumidoresApi.listarMedidoresDoConsumidor).mockResolvedValue([
      medidor({ transformadorId: null, alimentadorId: null, subestacaoId: null, latitude: null, longitude: null, dataInstalacao: null }),
    ])

    render(<Consumidores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver medidores de Maria Fernandes' }))

    expect(await screen.findByText('Sem transformador')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toHaveTextContent('Não informado')
  })

  it('permite tentar novamente quando a consulta falha', async () => {
    vi.mocked(consumidoresApi.listarMedidoresDoConsumidor).mockRejectedValueOnce(new Error('rede')).mockResolvedValue([medidor({})])

    render(<Consumidores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver medidores de Maria Fernandes' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Tentar novamente' }))

    expect(await screen.findByText('Medidor Casa')).toBeInTheDocument()
  })

  it('exibe estado vazio para titular sem medidores', async () => {
    vi.mocked(consumidoresApi.listarMedidoresDoConsumidor).mockResolvedValue([])

    render(<Consumidores />)
    await userEvent.click(await screen.findByRole('button', { name: 'Ver medidores de Mercado Central Ltda' }))

    expect(await screen.findByText('Nenhum medidor vinculado a este consumidor.')).toBeInTheDocument()
  })
})

describe('perfil tecnico', () => {
  it('consulta sem as acoes de cadastro, edicao ou instalacao', async () => {
    render(<Consumidores />, { perfil: 'TECNICO_MANUTENCAO' })
    await vi.waitFor(() => expect(screen.queryByText(/^Carregando/)).not.toBeInTheDocument())

    expect(screen.queryAllByRole('button', { name: /^(Nova|Novo|Editar|Instalar)/ })).toHaveLength(0)
  })

  it('mantem as acoes de cadastro para o gerente', async () => {
    render(<Consumidores />, { perfil: 'GERENTE_OPERACOES' })
    await vi.waitFor(() => expect(screen.queryByText(/^Carregando/)).not.toBeInTheDocument())

    expect(screen.queryAllByRole('button', { name: /^(Nova|Novo)/ }).length).toBeGreaterThan(0)
  })
})
