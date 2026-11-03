import { AlertTriangle, CheckCircle2, Clock } from 'lucide-react'
import { formatarNumero } from '../../utils/formatacao'
import { avaliarTensao, descreverIdade, estaAtrasada, type Leitura } from './monitoramento'

type Props = {
  leitura: Leitura | null
  unidade: string
  agora: number
  // Tensão de operação da linha, quando a célula deve avaliar a faixa normal
  referenciaKv?: number
}

const SITUACAO_TENSAO = {
  NORMAL: { rotulo: 'Normal', classe: 'text-green-400', icone: CheckCircle2 },
  SUBTENSAO: { rotulo: 'Subtensão', classe: 'text-orange-400', icone: AlertTriangle },
  SOBRETENSAO: { rotulo: 'Sobretensão', classe: 'text-orange-400', icone: AlertTriangle },
}

// Ausência de leitura nunca é exibida como zero
function CelulaLeitura({ leitura, unidade, agora, referenciaKv }: Props) {
  if (!leitura) {
    return <span className="text-xs text-slate-500">Sem leitura</span>
  }

  const atrasada = estaAtrasada(leitura.medidoEm, agora)
  const situacao = referenciaKv ? SITUACAO_TENSAO[avaliarTensao(leitura.valor, referenciaKv)] : null

  return (
    <div>
      <p className={atrasada ? 'text-slate-400' : 'text-slate-100'}>
        {formatarNumero(leitura.valor)} <span className="text-xs text-slate-500">{unidade}</span>
      </p>

      {situacao && (
        <p className={`mt-0.5 flex items-center gap-1 text-xs ${situacao.classe}`}>
          <situacao.icone className="h-3 w-3" />
          {situacao.rotulo}
        </p>
      )}

      <p className="mt-0.5 text-xs text-slate-500">
        {descreverIdade(leitura.medidoEm, agora)} · {leitura.origem === 'SIMULADA' ? 'Simulada' : 'Real'}
      </p>

      {atrasada && (
        <p className="mt-0.5 flex items-center gap-1 text-xs text-yellow-400">
          <Clock className="h-3 w-3" />
          Atrasada
        </p>
      )}
    </div>
  )
}

export default CelulaLeitura
