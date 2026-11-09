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
  // Primeira autoria de cada etapa; repetir a ação não a substitui
  reconhecidoEm: string | null
  reconhecidoPorUsuarioId: number | null
  resolvidoEm: string | null
  resolvidoPorUsuarioId: number | null
}

// Registro imutável da tratativa: autor e instante vêm do servidor
export type TratativaAlerta = {
  id: number
  alertaId: number
  usuarioId: number
  situacaoAnterior: SituacaoAlerta
  situacaoNova: SituacaoAlerta
  alteradoEm: string
  comentario: string
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

export type TipoAtivoAlerta = 'SUBESTACAO' | 'ALIMENTADOR' | 'TRANSFORMADOR' | 'LINHA' | 'USINA' | 'MEDIDOR'

// Item da fila global: só alertas que ainda exigem atenção, com a criticidade podendo faltar nos legados
export type AlertaFila = Omit<AlertaHistorico, 'criticidade'> & {
  criticidade: Criticidade | null
  resolvidoEm: string | null
  resolvidoPorUsuarioId: number | null
  tipoAtivo: TipoAtivoAlerta | null
}

export type FiltrosFilaAlertas = {
  tipoAlerta?: TipoAlertaHistorico
  criticidade?: Criticidade
  escopo?: EscopoAlerta
  tipoAtivo?: TipoAtivoAlerta
  subestacaoId?: number
  dataInicio?: string
  dataFim?: string
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
  // A versão protege a designação contra alterações concorrentes: divergência retorna 409
  equipeDesignadaId: number | null
  versaoDesignacao: number
  // Preenchido apenas na conclusão de uma falha de equipamento
  estadoAposReparo: EstadoOperacional | null
}

// equipeId nulo retira a equipe atual
export type DesignacaoEquipeRequest = {
  equipeId: number | null
  versaoDesignacaoEsperada: number
  comentario: string
}

// Registro imutável do despacho: autor e instante vêm do servidor
export type DesignacaoEquipe = {
  id: number
  eventoOperacionalId: number
  equipeAnteriorId: number | null
  equipeNovaId: number | null
  usuarioId: number
  registradoEm: string
  comentario: string
  versaoDesignacao: number
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
  // Obrigatório só na conclusão de falha de equipamento: OPERACIONAL ou MANUTENCAO
  estadoAposReparo: EstadoOperacional | null
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

// O registrador vem da sessão; o estado anterior é capturado e o ativo passa a FALHA na mesma transação
export type FalhaEquipamentoRequest = {
  ativoId: number
  responsavelUsuarioId: number
  descricao: string
  criticidade: Criticidade | null
}

// A identidade é a do evento operacional da falha
export type FalhaEquipamento = {
  id: number
  evento: EventoOperacional
  estadoAnterior: EstadoOperacional
  registradoEm: string
  estadoAposReparo: EstadoOperacional | null
  estadoOperacionalAtual: EstadoOperacional
}

export type FiltrosFalhas = {
  ativoId?: number
  situacao?: SituacaoEvento
  dataInicio?: string
  dataFim?: string
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

export type SituacaoFatorPotencia = 'CALCULADO' | 'DADOS_INSUFICIENTES' | 'POTENCIA_APARENTE_NULA'

// Leituras podem ser parciais: grandezas não medidas chegam nulas, nunca como zero
export type MedicaoLinha = {
  id: number
  linhaId: number
  fonteId: number
  origem: 'REAL' | 'SIMULADA'
  medidoEm: string
  recebidoEm: string
  tensaoKv: number | null
  correnteA: number | null
  potenciaAtivaMw: number | null
  potenciaReativaMvar: number | null
  subestacaoReferenciaId: number | null
  frequenciaHz: number | null
  fatorPotencia: number | null
  situacaoFatorPotencia: SituacaoFatorPotencia
}

export type SituacaoPerdaTecnica = 'OK' | 'DADOS_INSUFICIENTES' | 'SEM_ENERGIA_INJETADA'

// O percentual de referência é o snapshot gravado na medição, não o parâmetro atual do alimentador
export type MedicaoEnergia = {
  id: number
  alimentadorId: number
  fonteId: number
  origem: 'REAL' | 'SIMULADA'
  inicioPeriodo: string
  fimPeriodo: string
  recebidoEm: string
  energiaInjetadaMwh: number
  percentualPerdaReferencia: number
}

// Totais e percentual ficam nulos quando a cobertura do período é insuficiente
export type PerdaTecnica = {
  alimentadorId: number
  dataInicio: string
  dataFim: string
  totalEnergiaInjetadaMwh: number | null
  totalPerdaEstimadaMwh: number | null
  percentualPonderado: number | null
  situacao: SituacaoPerdaTecnica
  percentualCobertura: number
  medicoesConsideradas: MedicaoEnergia[]
}

export type GranularidadeHistorico = 'DIA' | 'MES'

// Pontos com dados insuficientes trazem os totais nulos, sem rateio entre períodos
export type PontoHistoricoEnergia = {
  inicioPeriodo: string
  fimPeriodo: string
  energiaInjetadaMwh: number | null
  perdaEstimadaMwh: number | null
  percentualPerdaPonderado: number | null
  quantidadeLeituras: number
  situacao: SituacaoPerdaTecnica
}

export type HistoricoEnergia = {
  alimentadorId: number
  dataInicio: string
  dataFim: string
  granularidade: GranularidadeHistorico
  fusoHorario: string
  possuiDados: boolean
  pontos: PontoHistoricoEnergia[]
}

export type PontoConsumoMedido = {
  inicioPeriodo: string
  fimPeriodo: string
  consumoMedidoMwh: number | null
  quantidadeLeituras: number
  quantidadeMedidoresComCoberturaCompleta: number
  situacao: 'OK' | 'DADOS_INSUFICIENTES' | 'SEM_CONSUMO'
}

export type HistoricoConsumoMedido = {
  alimentadorId: number
  dataInicio: string
  dataFim: string
  granularidade: GranularidadeHistorico
  fusoHorario: string
  possuiDados: boolean
  quantidadeMedidoresConsiderados: number
  coberturaCadastral: string
  pontos: PontoConsumoMedido[]
}

export type TipoAtivoMapa = 'USINA' | 'SUBESTACAO' | 'TRANSFORMADOR' | 'LINHA'

export type AreaMapa = { minLat: number; maxLat: number; minLng: number; maxLng: number }

// GeoJSON: coordenadas em [longitude, latitude]
export type FeatureMapa = {
  type: 'Feature'
  id: string
  geometry: { type: 'Point'; coordinates: [number, number] } | { type: 'LineString'; coordinates: [number, number][] }
  properties: {
    ativoId: number
    tipoAtivo: TipoAtivoMapa
    nome: string
    estadoOperacional: EstadoOperacional
    detalheUri: string
    subestacaoOrigemId?: number
    subestacaoDestinoId?: number
    tipoRepresentacao?: 'CONEXAO_SIMPLIFICADA'
  }
}

export type ColecaoMapa = {
  type: 'FeatureCollection'
  features: FeatureMapa[]
  metadados: { totalFeatures: number; totalAtivosSemGeometria: number }
}

export type TipoAtivoIndicador = 'SUBESTACAO' | 'ALIMENTADOR' | 'TRANSFORMADOR' | 'LINHA' | 'USINA'

export type SituacaoDisponibilidade = 'COBERTURA_NAO_AFERIDA' | 'SEM_POPULACAO_ATIVA' | 'DADOS_INCONSISTENTES'

// Tempos em segundos; disponibilidade e tempo médio ficam nulos quando não há base de cálculo
export type IndicadoresOperacionais = {
  calculadoEm: string
  dataInicio: string
  dataFim: string
  filtrosAplicados: { ativoId: number | null; tipos: TipoAtivoIndicador[]; subestacaoId: number | null }
  quantidadeAtivos: { tipoAtivo: TipoAtivoIndicador; estadoOperacional: EstadoOperacional; quantidade: number }[]
  totalAtivos: number
  quantidadeIniciadasNoPeriodo: number
  quantidadeSobrepostasAoPeriodo: number
  quantidadeSemTerminoFisico: number
  quantidadeRestabelecimentosNoPeriodo: number
  quantidadeRegistrosInconsistentes: number
  tempoInterrompidoSegundos: number
  tempoMedioRestabelecimentoSegundos: number | null
  tempoObservadoAtivosSegundos: number
  disponibilidadeEstimadaPercentual: number | null
  situacaoDisponibilidade: SituacaoDisponibilidade
}

export type TipoGeracao = 'HIDRELETRICA' | 'SOLAR' | 'EOLICA' | 'TERMICA'

export type Usina = {
  id: number
  nome: string
  latitude: number
  longitude: number
  tipoGeracao: TipoGeracao
  capacidadeMaximaMw: number
  subestacaoId: number
  dataInstalacao: string | null
  estadoOperacional: EstadoOperacional
}

export type UsinaRequest = {
  nome: string
  latitude: string
  longitude: string
  tipoGeracao: TipoGeracao
  capacidadeMaximaMw: string
  subestacaoId: number
  dataInstalacao: string | null
  estadoOperacional: EstadoOperacional
}

export type FiltrosUsina = { nome?: string; tipoGeracao?: TipoGeracao; estadoOperacional?: EstadoOperacional }

export type SituacaoEquipe = 'DISPONIVEL' | 'EM_ATENDIMENTO' | 'FORA_DE_SERVICO'

export type Equipe = {
  id: number
  nome: string
  responsabilidades: string | null
  especialidades: string | null
  situacao: SituacaoEquipe
}

export type EquipeRequest = {
  nome: string
  responsabilidades: string | null
  especialidades: string | null
}

// Vínculo vigente de um usuário com a equipe
export type Integrante = {
  id: number
  equipeId: number
  usuarioId: number
  nome: string
  perfil: PerfilAcesso
  inicioVigencia: string
  fimVigencia: string | null
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

// Última leitura de cada grandeza, com instante, fonte, origem e terminal próprios
export type AmostraSnapshot = {
  valor: number
  medidoEm: string
  recebidoEm: string
  fonteId: number
  origemLeitura: 'REAL' | 'SIMULADA'
  terminalReferenciaId: number | null
}

export type LinhaSnapshot = {
  ativoId: number
  nome: string
  tipoAtivo: 'LINHA'
  subestacaoOrigemId: number
  subestacaoDestinoId: number
  tensaoOperacao: number
  tensaoKv: AmostraSnapshot | null
  correnteA: AmostraSnapshot | null
  frequenciaHz: AmostraSnapshot | null
  potenciaAtivaMw: AmostraSnapshot | null
  potenciaReativaMvar: AmostraSnapshot | null
  fatorPotencia: AmostraSnapshot | null
  situacaoFatorPotencia: SituacaoFatorPotencia
}

// Sem leitura, todas as amostras chegam nulas, nunca como zero
export type TransformadorSnapshot = {
  ativoId: number
  nome: string
  tipoAtivo: 'TRANSFORMADOR'
  subestacaoId: number
  tensaoOperacao: number
  potenciaAparenteMva: AmostraSnapshot | null
  capacidadeReferenciaMva: AmostraSnapshot | null
  cargaPercentual: AmostraSnapshot | null
}

export type SnapshotMonitoramento = {
  // Instante do servidor: a idade das leituras é medida contra ele
  consultadoEm: string
  linhas: LinhaSnapshot[]
  transformadores: TransformadorSnapshot[]
}

// Consumo de um período, em kWh; os vínculos são o retrato do medidor no momento da leitura
export type MedicaoConsumo = {
  id: number
  medidorId: number
  fonteId: number
  origem: 'REAL' | 'SIMULADA'
  inicioPeriodo: string
  fimPeriodo: string
  recebidoEm: string
  consumoKwh: number
  consumidorId: number | null
  transformadorId: number | null
  alimentadorId: number | null
  subestacaoId: number | null
}
