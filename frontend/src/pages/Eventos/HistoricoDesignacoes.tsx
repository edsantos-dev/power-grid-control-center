import { useEffect, useState } from 'react'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import { listarDesignacoes } from '../../services/eventos'
import type { DesignacaoEquipe, Equipe } from '../../types'
import { formatarDataHora } from '../../utils/formatacao'
import { nomeEquipe } from './designacao'

type Props = {
  eventoId: number
  equipes: Equipe[]
}

// Auditoria própria do despacho, separada das transições de situação
function HistoricoDesignacoes({ eventoId, equipes }: Props) {
  const { usuario } = useAuth()
  const [designacoes, setDesignacoes] = useState<DesignacaoEquipe[]>([])
  const [pagina, setPagina] = useState(0)
  const [ultimaPagina, setUltimaPagina] = useState(true)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro('')

    listarDesignacoes(eventoId, pagina)
      .then((resposta) => {
        if (!ativo) return
        setDesignacoes((atuais) => (pagina === 0 ? resposta.content : [...atuais, ...resposta.content]))
        setUltimaPagina(resposta.last)
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [eventoId, pagina])

  const autor = (id: number) => (id === usuario?.id ? 'você' : `usuário #${id}`)

  function descrever(d: DesignacaoEquipe) {
    if (d.equipeAnteriorId === null) return `${nomeEquipe(d.equipeNovaId, equipes)} designada`
    if (d.equipeNovaId === null) return `${nomeEquipe(d.equipeAnteriorId, equipes)} retirada`
    return `${nomeEquipe(d.equipeAnteriorId, equipes)} → ${nomeEquipe(d.equipeNovaId, equipes)}`
  }

  return (
    <section className="mt-6" aria-labelledby="titulo-designacoes">
      <h4 id="titulo-designacoes" className="mb-3 text-sm font-semibold text-slate-300">Designações de equipe</h4>

      <ol className="space-y-3" aria-label="Designações de equipe">
        {designacoes.map((d) => (
          <li key={d.id} className="rounded-lg border border-slate-800 p-3">
            <p className="text-sm text-slate-200">{descrever(d)}</p>
            <p className="mt-1 text-xs text-slate-500">
              {formatarDataHora(d.registradoEm)} por {autor(d.usuarioId)} • versão {d.versaoDesignacao}
            </p>
            <p className="mt-2 whitespace-pre-line text-sm text-slate-300">{d.comentario}</p>
          </li>
        ))}
      </ol>

      {carregando && <p className="py-4 text-center text-sm text-slate-500">Carregando designações...</p>}
      {!carregando && erro && <p className="py-4 text-center text-sm text-red-400">{erro}</p>}
      {!carregando && !erro && designacoes.length === 0 && (
        <p className="py-4 text-center text-sm text-slate-500">Nenhuma designação registrada.</p>
      )}
      {!carregando && !erro && !ultimaPagina && (
        <div className="mt-4 flex justify-center">
          <button
            type="button"
            onClick={() => setPagina((atual) => atual + 1)}
            className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
          >
            Carregar mais designações
          </button>
        </div>
      )}
    </section>
  )
}

export default HistoricoDesignacoes
