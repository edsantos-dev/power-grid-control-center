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

export type Criticidade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA'

export type EscopoAlerta = 'ATIVO' | 'REDE_MONITORADA'

export type SituacaoAlerta = 'ABERTO' | 'RECONHECIDO' | 'RESOLVIDO'

export type Alerta = {
  id: number
  tipo: string
  criticidade: Criticidade
  escopo: EscopoAlerta
  situacao: SituacaoAlerta
  geradoEm: string
  ativoId: number | null
}

export type AlertaRequest = {
  tipo: string
  criticidade: Criticidade
  escopo: EscopoAlerta
  ativoId: number | null
}

export type StatusAlertaAutomatico = 'ATIVO' | 'RECONHECIDO'

export type AlertaSobrecarga = {
  id: number
  ativoId: number
  medicaoId: number
  cargaPercentual: number
  limiarPercentual: number
  status: StatusAlertaAutomatico
  geradoEm: string
  reconhecidoEm: string | null
  reconhecidoPorUsuarioId: number | null
}

export type TipoAlertaTensao = 'SUBTENSAO' | 'SOBRETENSAO'

export type AlertaTensao = {
  id: number
  ativoId: number
  medicaoId: number
  tipo: TipoAlertaTensao
  tensaoMedidaKv: number
  tensaoReferenciaKv: number
  limiteInferiorKv: number
  limiteSuperiorKv: number
  status: StatusAlertaAutomatico
  geradoEm: string
  reconhecidoEm: string | null
  reconhecidoPorUsuarioId: number | null
}

export type StatusComunicacao = 'ATIVO' | 'RESTABELECIDO'

export type AlertaComunicacao = {
  id: number
  fonteId: number
  ativoId: number
  monitoradaDesde: string
  ultimaComunicacaoEm: string | null
  toleranciaSegundos: number
  detectadoEm: string
  status: StatusComunicacao
  restabelecidoEm: string | null
}

export type AvaliacaoComunicacao = {
  avaliadoEm: string
  toleranciaSegundos: number
  fontesAvaliadas: number
  alertasCriados: number
  alertasAtivosExistentes: number
}

export type TipoAlertaHistorico = 'GENERICO' | 'SOBRECARGA' | 'TENSAO' | 'COMUNICACAO'

export type StatusOrigemAlerta = 'ABERTO' | 'ATIVO' | 'RECONHECIDO' | 'RESOLVIDO' | 'RESTABELECIDO'

// A identidade é composta: o mesmo idOrigem pode existir em tipos diferentes
export type AlertaHistorico = {
  idOrigem: number
  tipoAlerta: TipoAlertaHistorico
  criticidade: Criticidade
  escopo: EscopoAlerta
  ativoId: number | null
  fonteId: number | null
  medicaoId: number | null
  ocorridoEm: string | null
  statusOrigem: StatusOrigemAlerta
  subtipo: string | null
  mensagem: string | null
  reconhecidoEm: string | null
  reconhecidoPorUsuarioId: number | null
  restabelecidoEm: string | null
  descricao: string | null
}

export type FiltrosHistoricoAlertas = {
  tipoAlerta?: TipoAlertaHistorico
  criticidade?: Criticidade
  escopo?: EscopoAlerta
  statusOrigem?: StatusOrigemAlerta
  dataInicio?: string
  dataFim?: string
}

export type CategoriaEvento =
  | 'MANOBRA'
  | 'DESLIGAMENTO_PROGRAMADO'
  | 'INTERRUPCAO_FORNECIMENTO'
  | 'SOBRECARGA'
  | 'FALHA_EQUIPAMENTO'
  | 'MANUTENCAO'

export type SituacaoEvento = 'ABERTO' | 'EM_ANDAMENTO' | 'CONCLUIDO'

export type EventoOperacional = {
  id: number
  ativoId: number
  categoria: CategoriaEvento
  descricao: string
  criticidade: string | null
  situacao: SituacaoEvento
  registradorUsuarioId: number
  responsavelUsuarioId: number
  inicioPrevisto: string | null
  terminoPrevisto: string | null
  inicioEfetivo: string | null
  terminoEfetivo: string | null
}

// O registrador vem da sessão e a situação inicial é definida pelo servidor
export type EventoOperacionalRequest = {
  ativoId: number
  categoria: CategoriaEvento
  descricao: string
  responsavelUsuarioId: number
  inicioPrevisto: string
  terminoPrevisto: string | null
  criticidade: string | null
}

// Categoria e situação inicial são fixadas pelo servidor
export type InterrupcaoRequest = {
  ativoId: number
  descricao: string
  responsavelUsuarioId: number
  inicioEfetivo: string
  criticidade: string | null
}

export type TransicaoEventoRequest = {
  situacaoDestino: SituacaoEvento
  situacaoEsperada: SituacaoEvento
  comentario: string
  terminoEfetivo: string | null
}

// Registro imutável: autor e instante vêm do servidor
export type TransicaoEvento = {
  id: number
  eventoId: number
  usuarioId: number
  situacaoAnterior: SituacaoEvento
  situacaoNova: SituacaoEvento
  alteradoEm: string
  comentario: string
}

export type ManutencaoHistorico = {
  evento: EventoOperacional
  ultimaTransicao: TransicaoEvento | null
}

export type FiltrosManutencao = {
  situacao?: SituacaoEvento
  responsavelUsuarioId?: number
  dataInicio?: string
  dataFim?: string
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
