import { Gauge } from 'lucide-react'
import { listarAlertasSobrecarga, reconhecerAlertaSobrecarga } from '../../services/alertas'
import type { AtivoCatalogo } from '../../services/ativos'
import type { AlertaSobrecarga, StatusAlertaAutomatico } from '../../types'
import { formatarDataHora, formatarPercentual } from '../../utils/formatacao'
import SecaoAlertasAutomaticos from './SecaoAlertasAutomaticos'

type Props = {
  ativos: AtivoCatalogo[]
  aoAlterar?: () => void
}

// Referência estável para não disparar novas consultas a cada renderização
const listar = (page: number, { status }: { status?: string }) =>
  listarAlertasSobrecarga(page, status as StatusAlertaAutomatico | undefined)

function AlertasSobrecarga({ ativos, aoAlterar }: Props) {
  function descrever(alerta: AlertaSobrecarga) {
    const transformador = ativos.find((a) => a.id === alerta.ativoId)?.nome ?? `Transformador #${alerta.ativoId}`

    return {
      alvo: transformador,
      resumo: `${transformador} com carga de ${formatarPercentual(alerta.cargaPercentual)}`,
      detalhe: `Limiar ${formatarPercentual(alerta.limiarPercentual)} • ${formatarDataHora(alerta.geradoEm)} • Medição #${alerta.medicaoId}`,
    }
  }

  return (
    <SecaoAlertasAutomaticos
      titulo="Sobrecarga em transformadores"
      descricao="Gerados automaticamente quando uma medição ultrapassa o limiar de carga. Mais recentes primeiro."
      rotuloLista="Alertas de sobrecarga"
      icone={Gauge}
      cor={{ texto: 'text-orange-400', fundo: 'bg-orange-500/10', borda: 'border-orange-500/40' }}
      listar={listar}
      reconhecer={reconhecerAlertaSobrecarga}
      aoReconhecer={aoAlterar}
      descrever={descrever}
    />
  )
}

export default AlertasSobrecarga
