import { useState } from 'react'
import { RefreshCw, WifiOff } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { avaliarComunicacao, listarAlertasComunicacao } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import type { AlertaComunicacao, AvaliacaoComunicacao, StatusComunicacao } from '../../types'
import { formatarDataHora } from '../../utils/formatacao'
import SecaoAlertasAutomaticos from './SecaoAlertasAutomaticos'

type Props = {
  ativos: AtivoCatalogo[]
}

const filtrosStatus = [
  { valor: 'ATIVO', label: 'Sem comunicação' },
  { valor: 'RESTABELECIDO', label: 'Restabelecidos' },
]

// Referência estável para não disparar novas consultas a cada renderização
const listar = (page: number, { status }: { status?: string }) =>
  listarAlertasComunicacao(page, status as StatusComunicacao | undefined)

function situacao(alerta: AlertaComunicacao) {
  return alerta.status === 'ATIVO'
    ? { texto: 'Sem comunicação', classe: 'text-red-400' }
    : { texto: 'Restabelecido', classe: 'text-green-400' }
}

function AlertasComunicacao({ ativos }: Props) {
  const { usuario } = useAuth()
  const [versao, setVersao] = useState(0)
  const [avaliando, setAvaliando] = useState(false)
  const [resultado, setResultado] = useState<AvaliacaoComunicacao | null>(null)
  const [erro, setErro] = useState('')

  // O backend restringe a avaliação a operadores e gerentes
  const podeAvaliar = usuario?.perfil === 'OPERADOR' || usuario?.perfil === 'GERENTE_OPERACOES'

  async function handleAvaliar() {
    setAvaliando(true)
    setErro('')

    try {
      setResultado(await avaliarComunicacao())
      setVersao((atual) => atual + 1)
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setAvaliando(false)
    }
  }

  function descrever(alerta: AlertaComunicacao) {
    const ativo = ativos.find((a) => a.id === alerta.ativoId)
    const alvo = ativo ? `${ativo.tipo} · ${ativo.nome}` : `Ativo #${alerta.ativoId}`
    const referencia = alerta.ultimaComunicacaoEm
      ? `Último contato ${formatarDataHora(alerta.ultimaComunicacaoEm)}`
      : `Sem contato desde o início do monitoramento, em ${formatarDataHora(alerta.monitoradaDesde)}`

    return {
      alvo,
      resumo: `Perda de comunicação em ${alvo} (fonte #${alerta.fonteId})`,
      detalhe: `${referencia} • tolerância de ${alerta.toleranciaSegundos} s • detectada em ${formatarDataHora(alerta.detectadoEm)}`,
      complemento: alerta.restabelecidoEm ? `Restabelecida em ${formatarDataHora(alerta.restabelecidoEm)}` : undefined,
    }
  }

  const acoes = podeAvaliar && (
    <div className="flex flex-col gap-1 lg:items-end">
      <button
        type="button"
        onClick={handleAvaliar}
        disabled={avaliando}
        className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
      >
        <RefreshCw className={`h-3.5 w-3.5 ${avaliando ? 'animate-spin' : ''}`} />
        {avaliando ? 'Avaliando...' : 'Avaliar comunicação agora'}
      </button>

      {resultado && (
        <p className="text-xs text-slate-400" role="status">
          {resultado.fontesAvaliadas} fonte(s) avaliada(s), {resultado.alertasCriados} nova(s) perda(s) e{' '}
          {resultado.alertasAtivosExistentes} já em andamento.
        </p>
      )}

      {erro && <p className="text-xs text-red-400">{erro}</p>}
    </div>
  )

  return (
    <SecaoAlertasAutomaticos
      // Recria a seção após cada avaliação para recarregar a lista desde a primeira página
      key={versao}
      titulo="Perda de comunicação"
      descricao="Fontes de medição sem contato além da tolerância. O restabelecimento ocorre quando uma nova medição é recebida."
      rotuloLista="Alertas de comunicação"
      icone={WifiOff}
      cor={{ texto: 'text-yellow-400', fundo: 'bg-yellow-500/10', borda: 'border-slate-800' }}
      filtrosStatus={filtrosStatus}
      acoes={acoes}
      listar={listar}
      situacao={situacao}
      descrever={descrever}
    />
  )
}

export default AlertasComunicacao
