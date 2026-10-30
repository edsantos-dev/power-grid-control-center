import { AxiosError, AxiosHeaders, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import api, { definirAoExpirarSessao, login, logout } from './api'

const usuario = { id: 1, nome: 'Operador', email: 'operador@powergrid.local', perfil: 'OPERADOR' }

let tokensEmitidos: number
let respostasDeEscrita: number[]
let escritas: InternalAxiosRequestConfig[]

function responder(config: InternalAxiosRequestConfig, status: number, data: unknown = null) {
  const response = { config, status, data, statusText: '', headers: new AxiosHeaders() } as AxiosResponse

  if (status >= 400) {
    throw new AxiosError('erro', undefined, config, undefined, response)
  }

  return response
}

beforeEach(async () => {
  tokensEmitidos = 0
  respostasDeEscrita = []
  escritas = []

  api.defaults.adapter = async (config) => {
    if (config.url === '/api/auth/csrf') {
      tokensEmitidos += 1
      return responder(config, 200, { token: `token-${tokensEmitidos}`, headerName: 'X-CSRF-TOKEN' })
    }

    escritas.push(config)
    return responder(config, respostasDeEscrita.shift() ?? 200, usuario)
  }

  definirAoExpirarSessao(() => {})
  // Descarta o token guardado pelo teste anterior
  await logout()
  escritas = []
})

describe('login', () => {
  it('envia formulario com e-mail sem espacos e token CSRF', async () => {
    await expect(login('  operador@powergrid.local ', 'SenhaSegura123')).resolves.toEqual(usuario)

    const [requisicao] = escritas
    expect(String(requisicao.data)).toBe('email=operador%40powergrid.local&password=SenhaSegura123')
    expect(requisicao.headers.get('X-CSRF-TOKEN')).toMatch(/^token-/)
  })

  it('busca novo token depois do login', async () => {
    await login('operador@powergrid.local', 'SenhaSegura123')
    await api.post('/api/subestacoes', {})

    const [loginRequest, escrita] = escritas
    expect(escrita.headers.get('X-CSRF-TOKEN')).not.toBe(loginRequest.headers.get('X-CSRF-TOKEN'))
  })

  it('nao trata credenciais invalidas como sessao expirada', async () => {
    const aoExpirar = vi.fn()
    definirAoExpirarSessao(aoExpirar)
    respostasDeEscrita = [401]

    await expect(login('operador@powergrid.local', 'errada')).rejects.toBeInstanceOf(AxiosError)
    expect(aoExpirar).not.toHaveBeenCalled()
  })
})

describe('interceptadores', () => {
  it('nao envia token em consultas', async () => {
    const { config } = await api.get('/api/subestacoes')

    expect(config.headers.get('X-CSRF-TOKEN')).toBeUndefined()
  })

  it('renova o token uma vez apos 403', async () => {
    respostasDeEscrita = [403, 200]

    await api.post('/api/subestacoes', {})

    expect(escritas).toHaveLength(2)
    expect(escritas[1].headers.get('X-CSRF-TOKEN')).not.toBe('token-1')
  })

  it('desiste quando o 403 se repete', async () => {
    respostasDeEscrita = [403, 403, 200]

    await expect(api.post('/api/subestacoes', {})).rejects.toBeInstanceOf(AxiosError)
    expect(escritas).toHaveLength(2)
  })

  it('avisa quando a sessao expira', async () => {
    const aoExpirar = vi.fn()
    definirAoExpirarSessao(aoExpirar)
    respostasDeEscrita = [401]

    await expect(api.post('/api/subestacoes', {})).rejects.toBeInstanceOf(AxiosError)
    expect(aoExpirar).toHaveBeenCalledOnce()
  })
})
