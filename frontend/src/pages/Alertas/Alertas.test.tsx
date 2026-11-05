import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as alertasApi from '../../services/alertas'
import * as ativosApi from '../../services/ativos'
import type { Alerta } from '../../types'
import Alertas from '.'

vi.mock('../../services/alertas')
vi.mock('../../services/ativos')
const montagensSobrecarga = vi.hoisted(() => ({ total: 0 }))

vi.mock('./AlertasSobrecarga', async () => {
  const { useEffect } = await import('react')
  return {
    default: function SobrecargaSimulada({ aoAlterar }: { aoAlterar: () => void }) {
      useEffect(() => {
        montagensSobrecarga.total += 1
      }, [])
      return <button onClick={aoAlterar}>Reconhecer na seção</button>
    },
  }
})
vi.mock('./AlertasTensao', () => ({ default: () => null }))
vi.mock('./AlertasComunicacao', () => ({ default: () => null }))
vi.mock('./HistoricoAlertas', () => ({ default: () => <p>Histórico simulado</p> }))
vi.mock('./FilaAlertasAtivos', () => ({
  default: ({ versao, aoAlterar }: { versao: number; aoAlterar: () => void }) => (
    <div>
      <p>Fila na versão {versao}</p>
      <button onClick={aoAlterar}>Reconhecer na fila</button>
    </div>
  ),
}))

const alerta: Alerta = {
  id: 4,
  tipo: 'Queda de frequência',
  criticidade: 'CRITICA',
  escopo: 'REDE_MONITORADA',
  situacao: 'ABERTO',
  geradoEm: '2026-11-03T12:00:00Z',
  ativoId: null,
  reconhecidoEm: null,
  reconhecidoPorUsuarioId: null,
  resolvidoEm: null,
  resolvidoPorUsuarioId: null,
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

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(ativosApi.listarCatalogoAtivos).mockResolvedValue([{ id: 30, nome: 'T-01', tipo: 'Transformador' }])
})

describe('pagina de alertas', () => {
  it('abre na fila de ativos e alterna para o historico', async () => {
    render(<Alertas />)

    expect(screen.getByText('Fila na versão 0')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Histórico' }))
    expect(screen.getByText('Histórico simulado')).toBeInTheDocument()
  })

  it('mantem a fila e as secoes por origem consistentes apos uma acao', async () => {
    montagensSobrecarga.total = 0
    render(<Alertas />)
    expect(montagensSobrecarga.total).toBe(1)

    await userEvent.click(screen.getByRole('button', { name: 'Reconhecer na seção' }))
    expect(screen.getByText('Fila na versão 1')).toBeInTheDocument()
    expect(montagensSobrecarga.total).toBe(1)

    await userEvent.click(screen.getByRole('button', { name: 'Reconhecer na fila' }))
    expect(montagensSobrecarga.total).toBe(2)
    expect(screen.getByText('Fila na versão 1')).toBeInTheDocument()
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

  it('envia ativoId nulo para a rede monitorada e atualiza a fila', async () => {
    vi.mocked(alertasApi.criarAlerta).mockResolvedValue(alerta)

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
    expect(await screen.findByText('Fila na versão 1')).toBeInTheDocument()
  })

  it('envia o ativo selecionado e exibe recusa do servidor', async () => {
    vi.mocked(alertasApi.criarAlerta).mockRejectedValue(erroHttp(404, 'Ativo não encontrado.'))

    render(<Alertas />)
    await screen.findByText('Fila na versão 0')
    await userEvent.click(screen.getByRole('button', { name: 'Registrar alerta' }))
    await vi.waitFor(() => expect(screen.getByRole('option', { name: /T-01/ })).toBeInTheDocument())
    preencher('Tipo', 'Sobrecarga')
    preencher('Criticidade', 'ALTA')
    preencher('Ativo afetado', '30')
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }))

    expect(await screen.findByText('Ativo não encontrado.')).toBeInTheDocument()
    expect(alertasApi.criarAlerta).toHaveBeenCalledWith({ tipo: 'Sobrecarga', criticidade: 'ALTA', escopo: 'ATIVO', ativoId: 30 })
  })
})
