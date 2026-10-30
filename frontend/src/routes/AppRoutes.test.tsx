import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../contexts/AuthContext'
import * as authApi from '../services/api'
import AppRoutes from './AppRoutes'

vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof authApi>()),
  consultarUsuarioAutenticado: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}))

const usuario = {
  id: 1,
  nome: 'Gerente Inicial',
  email: 'gerente@powergrid.local',
  perfil: 'GERENTE_OPERACOES',
} as const

function erroHttp(status: number) {
  const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
  return new AxiosError('erro', undefined, config, undefined, {
    config,
    status,
    data: null,
    statusText: '',
    headers: {},
  })
}

function abrirEm(caminho: string) {
  window.history.pushState({}, '', caminho)
  render(
    <AuthProvider>
      <AppRoutes />
    </AuthProvider>
  )
}

async function preencherLogin(senha: string) {
  await userEvent.type(screen.getByPlaceholderText('operador@empresa.com'), usuario.email)
  await userEvent.type(screen.getByPlaceholderText('Digite sua senha'), senha)
  await userEvent.click(screen.getByRole('button', { name: 'Entrar no sistema' }))
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(authApi.consultarUsuarioAutenticado).mockRejectedValue(erroHttp(401))
})

describe('US25', () => {
  it('redireciona rota interna para o login sem sessao', async () => {
    abrirEm('/subestacoes')

    expect(await screen.findByRole('button', { name: 'Entrar no sistema' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })

  it('mantem rota interna com sessao existente e exibe o perfil', async () => {
    vi.mocked(authApi.consultarUsuarioAutenticado).mockResolvedValue(usuario)

    abrirEm('/configuracoes')

    expect(await screen.findByText('Gerente de Operações')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/configuracoes')
  })

  it('leva ao dashboard com credenciais validas', async () => {
    vi.mocked(authApi.login).mockResolvedValue(usuario)

    abrirEm('/login')
    await preencherLogin('SenhaSegura123')

    expect(await screen.findByText('Gerente Inicial')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/dashboard')
  })

  it('exibe erro com credenciais invalidas', async () => {
    vi.mocked(authApi.login).mockRejectedValue(erroHttp(401))

    abrirEm('/login')
    await preencherLogin('errada')

    expect(await screen.findByText('E-mail ou senha inválidos.')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })

  it('volta ao login depois de sair', async () => {
    vi.mocked(authApi.consultarUsuarioAutenticado).mockResolvedValue(usuario)
    vi.mocked(authApi.logout).mockResolvedValue()

    abrirEm('/dashboard')
    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))

    expect(await screen.findByRole('button', { name: 'Entrar no sistema' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
  })

  it('permanece autenticado e avisa quando o logout falha', async () => {
    vi.mocked(authApi.consultarUsuarioAutenticado).mockResolvedValue(usuario)
    vi.mocked(authApi.logout).mockRejectedValue(new Error('rede'))

    abrirEm('/dashboard')
    await userEvent.click(await screen.findByRole('button', { name: 'Sair' }))

    expect(await screen.findByText('Não foi possível sair. Tente novamente.')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/dashboard')
  })
})
