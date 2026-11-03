import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as alertasApi from '../../services/alertas'
import * as ativosApi from '../../services/ativos'
import type { Alerta } from '../../types'
import Alertas from '.'

vi.mock('../../services/alertas')
vi.mock('../../services/ativos')

const alerta = (dados: Partial<Alerta>): Alerta => ({
  id: 1,
  tipo: 'Sobrecarga no T-01',
  criticidade: 'CRITICA',
  escopo: 'ATIVO',
  situacao: 'ABERTO',
  geradoEm: '2026-11-03T12:00:00Z',
  ativoId: 30,
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

async function itens() {
  return within(await screen.findByRole('list', { name: 'Alertas ativos' })).findAllByRole('listitem')
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(alertasApi.listarAlertasAtivos).mockResolvedValue([
    alerta({}),
    alerta({ id: 2, tipo: 'Oscilação de frequência', criticidade: 'ALTA', escopo: 'REDE_MONITORADA', ativoId: null, situacao: 'RECONHECIDO' }),
    alerta({ id: 3, tipo: 'Inspeção pendente', criticidade: 'BAIXA', ativoId: 999 }),
  ])
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 30, nome: 'T-01', tipo: 'Transformador' }])
})

describe('alertas ativos', () => {
  it('mantem a ordem do backend e indica criticidade e situacao', async () => {
    render(<Alertas />)

    const [primeiro, segundo, terceiro] = await itens()
    expect(primeiro).toHaveTextContent('Sobrecarga no T-01')
    expect(within(primeiro).getByText('Crítica')).toHaveClass('text-red-400')
    expect(within(primeiro).getByText('Aberto')).toBeInTheDocument()
    expect(segundo).toHaveTextContent('Oscilação de frequência')
    expect(within(segundo).getByText('Reconhecido')).toBeInTheDocument()
    expect(terceiro).toHaveTextContent('Inspeção pendente')
  })

  it('mostra o ativo pelo catalogo, o escopo de rede e um fallback sem inventar nome', async () => {
    render(<Alertas />)

    const [primeiro, segundo, terceiro] = await itens()
    expect(primeiro).toHaveTextContent('Transformador · T-01')
    expect(segundo).toHaveTextContent('Rede monitorada')
    expect(terceiro).toHaveTextContent('Ativo #999')
  })

  it('conta por criticidade e filtra sem reordenar', async () => {
    render(<Alertas />)
    await itens()

    await userEvent.click(screen.getByRole('button', { name: 'Alta' }))

    const lista = await itens()
    expect(lista).toHaveLength(1)
    expect(lista[0]).toHaveTextContent('Oscilação de frequência')
  })

  it('exibe mensagem apropriada sem alertas ativos', async () => {
    vi.mocked(alertasApi.listarAlertasAtivos).mockResolvedValue([])

    render(<Alertas />)

    expect(await screen.findByText('Nenhum alerta ativo no momento.')).toBeInTheDocument()
  })

  it('diferencia filtro vazio de ausencia de alertas', async () => {
    render(<Alertas />)
    await itens()

    await userEvent.click(screen.getByRole('button', { name: 'Média' }))

    expect(screen.getByText('Nenhum alerta corresponde ao filtro.')).toBeInTheDocument()
    expect(screen.queryByText('Nenhum alerta ativo no momento.')).not.toBeInTheDocument()
  })
})

describe('registro de alerta', () => {
  it('exige ativo no escopo ATIVO', async () => {
    render(<Alertas />)
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar alerta' }))
    preencher('Tipo', 'Teste')
    preencher('Criticidade', 'MEDIA')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(screen.getByText('Selecione o ativo afetado.')).toBeInTheDocument()
    expect(alertasApi.criarAlerta).not.toHaveBeenCalled()
  })

  it('envia ativoId nulo para a rede monitorada e recarrega a lista', async () => {
    vi.mocked(alertasApi.criarAlerta).mockResolvedValue(alerta({ id: 4 }))

    render(<Alertas />)
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar alerta' }))
    preencher('Tipo', '  Queda de frequência ')
    preencher('Criticidade', 'CRITICA')
    preencher('Escopo', 'REDE_MONITORADA')
    expect(screen.queryByLabelText('Ativo afetado')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(alertasApi.criarAlerta).toHaveBeenCalledWith({
      tipo: 'Queda de frequência',
      criticidade: 'CRITICA',
      escopo: 'REDE_MONITORADA',
      ativoId: null,
    })
    await screen.findAllByRole('listitem')
    expect(alertasApi.listarAlertasAtivos).toHaveBeenCalledTimes(2)
  })

  it('envia o ativo selecionado e exibe recusa do servidor', async () => {
    vi.mocked(alertasApi.criarAlerta).mockRejectedValue(erroHttp(404, 'Ativo não encontrado.'))

    render(<Alertas />)
    await userEvent.click(await screen.findByRole('button', { name: 'Registrar alerta' }))
    preencher('Tipo', 'Sobrecarga')
    preencher('Criticidade', 'ALTA')
    preencher('Ativo afetado', '30')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Ativo não encontrado.')).toBeInTheDocument()
    expect(alertasApi.criarAlerta).toHaveBeenCalledWith({ tipo: 'Sobrecarga', criticidade: 'ALTA', escopo: 'ATIVO', ativoId: 30 })
  })
})
