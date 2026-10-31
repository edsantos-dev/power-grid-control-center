export type PerfilAcesso = 'OPERADOR' | 'GERENTE_OPERACOES' | 'TECNICO_MANUTENCAO'

export type Usuario = {
  id: number
  nome: string
  email: string
  perfil: PerfilAcesso
}

export type CsrfResponse = {
  token: string
  headerName: string
}

export type ApiError = {
  message?: string
  fieldErrors?: { field: string; message: string }[]
}

export type EstadoOperacional = 'OPERACIONAL' | 'FALHA' | 'MANUTENCAO'

export type Subestacao = {
  id: number
  nome: string
  latitude: number
  longitude: number
  tensaoNominalKv: number
  capacidadeMva: number
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

// Os decimais seguem como texto para o backend validar a precisão sem arredondamento
export type SubestacaoRequest = {
  nome: string
  latitude: string
  longitude: string
  tensaoNominalKv: string
  capacidadeMva: string
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

export type Alimentador = {
  id: number
  nome: string
  latitude: number
  longitude: number
  tensaoNominalKv: number
  subestacaoId: number
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

export type AlimentadorRequest = {
  nome: string
  latitude: string
  longitude: string
  tensaoNominalKv: string
  subestacaoId: number
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

export type LinhaTransmissao = {
  id: number
  nome: string
  latitude: number | null
  longitude: number | null
  tensaoOperacaoKv: number
  subestacaoOrigemId: number
  subestacaoDestinoId: number
  extensaoKm: number
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

export type LinhaTransmissaoRequest = {
  nome: string
  latitude: string | null
  longitude: string | null
  tensaoOperacaoKv: string
  subestacaoOrigemId: number
  subestacaoDestinoId: number
  extensaoKm: string
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}
