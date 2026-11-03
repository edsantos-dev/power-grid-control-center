import { Activity } from 'lucide-react'
import { listarAlertasTensao, reconhecerAlertaTensao } from '../../services/alertas'
import type { AtivoCatalogo } from '../../services/ativos'
import type { AlertaTensao, StatusAlertaAutomatico, TipoAlertaTensao } from '../../types'
import { formatarDataHora, formatarNumero } from '../../utils/formatacao'
import SecaoAlertasAutomaticos from './SecaoAlertasAutomaticos'

type Props = {
  ativos: AtivoCatalogo[]
}

const tipoLabel: Record<TipoAlertaTensao, string> = {
  SUBTENSAO: 'Subtensão',
  SOBRETENSAO: 'Sobretensão',
}

const opcoesTipo = (Object.keys(tipoLabel) as TipoAlertaTensao[]).map((valor) => ({ valor, label: tipoLabel[valor] }))

// Referência estável para não disparar novas consultas a cada renderização
const listar = (page: number, { status, extra }: { status?: string; extra?: string }) =>
  listarAlertasTensao(page, status as StatusAlertaAutomatico | undefined, extra as TipoAlertaTensao | undefined)

function AlertasTensao({ ativos }: Props) {
  function descrever(alerta: AlertaTensao) {
    const linha = ativos.find((a) => a.id === alerta.ativoId)?.nome ?? `Linha #${alerta.ativoId}`
    const kv = (valor: number) => `${formatarNumero(valor)} kV`

    // A faixa é a registrada no alerta, não recalculada com a tensão cadastral atual
    return {
      alvo: linha,
      resumo: `${tipoLabel[alerta.tipo]} em ${linha}: ${kv(alerta.tensaoMedidaKv)}`,
      detalhe: `Referência ${kv(alerta.tensaoReferenciaKv)} • faixa normal acima de ${kv(alerta.limiteInferiorKv)} e abaixo de ${kv(alerta.limiteSuperiorKv)} • ${formatarDataHora(alerta.geradoEm)} • Medição #${alerta.medicaoId}`,
    }
  }

  return (
    <SecaoAlertasAutomaticos
      titulo="Tensão anormal em linhas"
      descricao="Gerados automaticamente quando a tensão medida atinge 95% ou 105% da tensão de operação da linha. Mais recentes primeiro."
      rotuloLista="Alertas de tensão"
      icone={Activity}
      cor={{ texto: 'text-orange-400', fundo: 'bg-orange-500/10', borda: 'border-orange-500/40' }}
      filtroExtra={{ rotulo: 'Tipo de alerta de tensão', opcoes: opcoesTipo }}
      listar={listar}
      reconhecer={reconhecerAlertaTensao}
      descrever={descrever}
    />
  )
}

export default AlertasTensao
