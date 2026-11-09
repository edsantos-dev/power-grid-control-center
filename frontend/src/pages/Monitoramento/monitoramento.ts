import type { AmostraSnapshot, LinhaSnapshot } from '../../types'

// Proposta a aprovar (US08-CA02/CA03): cadência de 30 s e atraso após 300 s,
// o mesmo padrão da tolerância de comunicação do backend (US23)
export const CADENCIA_MS = 30_000
export const ATRASO_SEGUNDOS = 300

export type CampoNumerico = 'tensaoKv' | 'correnteA' | 'potenciaAtivaMw' | 'potenciaReativaMvar' | 'frequenciaHz' | 'fatorPotencia'

export type Grandeza = {
  campo: CampoNumerico
  rotulo: string
  unidade: string
  // Potências têm sinal relativo ao terminal de referência da fonte
  comTerminal?: boolean
}

export const GRANDEZAS_LINHA: Grandeza[] = [
  { campo: 'tensaoKv', rotulo: 'Tensão', unidade: 'kV' },
  { campo: 'correnteA', rotulo: 'Corrente', unidade: 'A' },
  { campo: 'potenciaAtivaMw', rotulo: 'Potência ativa', unidade: 'MW', comTerminal: true },
  { campo: 'potenciaReativaMvar', rotulo: 'Potência reativa', unidade: 'MVAr', comTerminal: true },
  { campo: 'frequenciaHz', rotulo: 'Frequência', unidade: 'Hz' },
  { campo: 'fatorPotencia', rotulo: 'Fator de potência', unidade: '' },
]

// Referência didática do contrato; valores fora dela são aceitos e apenas exibidos
export const FREQUENCIA_NOMINAL_HZ = 60

export type Leitura = {
  valor: number
  medidoEm: string
  origem: AmostraSnapshot['origemLeitura']
  subestacaoReferenciaId: number | null
}

// Cada grandeza do snapshot traz a leitura mais recente que a contém, com instante, origem e terminal próprios
export function leituraDaAmostra(amostra: AmostraSnapshot | null): Leitura | null {
  return amostra
    ? { valor: amostra.valor, medidoEm: amostra.medidoEm, origem: amostra.origemLeitura, subestacaoReferenciaId: amostra.terminalReferenciaId }
    : null
}

export type ResultadoFatorPotencia =
  | { tipo: 'calculado'; leitura: Leitura }
  | { tipo: 'indefinido'; medidoEm: string }

// O fator é calculado pelo servidor com P e Q da mesma leitura; sem P ou Q não há cálculo
export function fatorPotenciaDoSnapshot(linha: LinhaSnapshot): ResultadoFatorPotencia | null {
  if (linha.situacaoFatorPotencia === 'DADOS_INSUFICIENTES') return null
  const leitura = leituraDaAmostra(linha.fatorPotencia)
  if (leitura) return { tipo: 'calculado', leitura }
  // P e Q nulas: o fator é indefinido e a idade vem da leitura de potência que o originou
  const origem = linha.potenciaAtivaMw ?? linha.potenciaReativaMvar
  return origem ? { tipo: 'indefinido', medidoEm: origem.medidoEm } : null
}

// O gráfico abre na primeira linha com alguma leitura: ativos recém-cadastrados ou de demonstração não escondem os demais
export function linhaComLeitura(linha: LinhaSnapshot) {
  return GRANDEZAS_LINHA.some((g) => linha[g.campo] !== null)
}

export function estaAtrasada(medidoEm: string, agora: number) {
  return agora - new Date(medidoEm).getTime() > ATRASO_SEGUNDOS * 1000
}

export function descreverIdade(medidoEm: string, agora: number) {
  const minutos = Math.max(0, Math.floor((agora - new Date(medidoEm).getTime()) / 60_000))
  if (minutos < 1) return 'há menos de 1 min'
  if (minutos < 60) return `há ${minutos} min`
  const horas = Math.floor(minutos / 60)
  return horas < 24 ? `há ${horas} h` : `há ${Math.floor(horas / 24)} dia(s)`
}

export type SituacaoTensao = 'NORMAL' | 'SUBTENSAO' | 'SOBRETENSAO'

// Mesma faixa da geração de alertas (US22): fronteiras de 95% e 105% contam como anormais
export function avaliarTensao(tensaoKv: number, referenciaKv: number): SituacaoTensao {
  if (tensaoKv <= referenciaKv * 0.95) return 'SUBTENSAO'
  if (tensaoKv >= referenciaKv * 1.05) return 'SOBRETENSAO'
  return 'NORMAL'
}
