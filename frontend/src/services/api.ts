import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiError, CsrfResponse, Usuario } from '../types'

type RequestConfig = InternalAxiosRequestConfig & { csrfRenovado?: boolean }

const METODOS_DE_ESCRITA = ['post', 'put', 'patch', 'delete']
const ROTA_CSRF = '/api/auth/csrf'
const ROTA_LOGIN = '/api/auth/login'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
})

let csrf: Promise<CsrfResponse> | null = null
let aoExpirarSessao: () => void = () => {}

export function definirAoExpirarSessao(callback: () => void) {
  aoExpirarSessao = callback
}

function obterCsrf() {
  if (!csrf) {
    csrf = api.get<CsrfResponse>(ROTA_CSRF).then((response) => response.data)
    csrf.catch(() => {
      csrf = null
    })
  }

  return csrf
}

function isEscrita(config: InternalAxiosRequestConfig) {
  return METODOS_DE_ESCRITA.includes(config.method ?? '')
}

api.interceptors.request.use(async (config) => {
  if (isEscrita(config)) {
    const { token, headerName } = await obterCsrf()
    config.headers.set(headerName, token)
  }

  return config
})

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as RequestConfig | undefined
  const status = error.response?.status

  // O token fica inválido quando a sessão expira; renova uma vez antes de desistir
  if (status === 403 && config && isEscrita(config) && !config.csrfRenovado) {
    config.csrfRenovado = true
    csrf = null
    return api.request(config)
  }

  if (status === 401 && config?.url !== ROTA_LOGIN) {
    csrf = null
    aoExpirarSessao()
  }

  return Promise.reject(error)
})

export function extrairErroApi(error: unknown) {
  if (!axios.isAxiosError<ApiError>(error) || !error.response) {
    return { mensagem: 'Não foi possível conectar ao servidor. Tente novamente.', campos: {} }
  }

  const { data } = error.response
  const campos: Record<string, string> = {}

  data?.fieldErrors?.forEach(({ field, message }) => {
    campos[field] = message
  })

  return { mensagem: data?.message || 'Não foi possível concluir a operação.', campos }
}

export async function login(email: string, senha: string) {
  const dados = new URLSearchParams({ email: email.trim(), password: senha })

  try {
    const { data } = await api.post<Usuario>(ROTA_LOGIN, dados)
    return data
  } finally {
    // O login renova a sessão e invalida o token anterior
    csrf = null
  }
}

export async function consultarUsuarioAutenticado() {
  const { data } = await api.get<Usuario>('/api/auth/me')
  return data
}

export async function logout() {
  try {
    await api.post('/api/auth/logout')
  } finally {
    csrf = null
  }
}

export default api
