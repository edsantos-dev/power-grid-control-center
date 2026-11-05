import { CheckCheck, CircleCheckBig, History } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import type { SituacaoAlerta } from '../../types'
import { podeTratar, proximaAcao, ROTULO_ACAO, type AcaoTratativa } from './tratativa'

type Props = {
  situacao: SituacaoAlerta
  rotulo: string
  aoTratar: (acao: AcaoTratativa) => void
  aoVerHistorico: () => void
}

const icones: Record<AcaoTratativa, typeof CheckCheck> = {
  reconhecer: CheckCheck,
  resolver: CircleCheckBig,
}

// Os modais ficam na página: recarregar a lista não pode fechá-los junto com o item
function AcoesTratativa({ situacao, rotulo, aoTratar, aoVerHistorico }: Props) {
  const { usuario } = useAuth()
  const acao = podeTratar(usuario) ? proximaAcao(situacao) : null
  const Icone = acao ? icones[acao] : null

  return (
    <div className="flex items-center gap-1.5">
      {acao && Icone && (
        <button
          type="button"
          onClick={() => aoTratar(acao)}
          aria-label={`${ROTULO_ACAO[acao]} ${rotulo}`}
          className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white"
        >
          <Icone className="h-3.5 w-3.5" />
          {ROTULO_ACAO[acao]}
        </button>
      )}

      <button
        type="button"
        onClick={aoVerHistorico}
        aria-label={`Tratativas de ${rotulo}`}
        className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white"
      >
        <History className="h-4 w-4" />
      </button>
    </div>
  )
}

export default AcoesTratativa
