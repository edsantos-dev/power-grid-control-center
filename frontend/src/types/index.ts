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

export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type Transformador = {
  id: number
  nome: string
  latitude: number
  longitude: number
  capacidadeMva: number
  alimentadorId: number
  subestacaoId: number
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

// O cliente não envia subestacaoId: o backend deriva a subestação pelo alimentador
export type TransformadorRequest = {
  nome: string
  latitude: string
  longitude: string
  capacidadeMva: string
  alimentadorId: number
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

export type MedicaoTransformador = {
  id: number
  transformadorId: number
  fonteId: number
  origem: 'REAL' | 'SIMULADA'
  medidoEm: string
  recebidoEm: string
  potenciaAparenteMva: number
  capacidadeReferenciaMva: number
  cargaPercentual: number
}

export type TipoPessoa = 'PF' | 'PJ'

export type Consumidor = {
  id: number
  nomeRazaoSocial: string
  tipoPessoa: TipoPessoa
  documento: string
  enderecoCorrespondencia: string | null
}

export type ConsumidorRequest = {
  nomeRazaoSocial: string
  tipoPessoa: TipoPessoa
  documento: string
  enderecoCorrespondencia: string | null
}

// Transformador, alimentador e subestação são nulos para medidor em estoque
export type Medidor = {
  id: number
  nome: string
  numeroSerie: string
  estadoOperacional: EstadoOperacional
  latitude: number | null
  longitude: number | null
  dataInstalacao: string | null
  enderecoFornecimento: string | null
  consumidorId: number | null
  transformadorId: number | null
  alimentadorId: number | null
  subestacaoId: number | null
}

export type MedidorRequest = {
  nome: string
  numeroSerie: string
  estadoOperacional: EstadoOperacional
}

export type InstalacaoMedidorRequest = {
  consumidorId: number
  transformadorId: number
  latitude: string
  longitude: string
  dataInstalacao: string
  enderecoFornecimento: string | null
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
