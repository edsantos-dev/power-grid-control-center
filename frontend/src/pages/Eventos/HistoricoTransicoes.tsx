import { useEffect, useState } from 'react'
import Modal from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import { listarTransicoes } from '../../services/eventos'
import type { Equipe, EventoOperacional, TransicaoEvento } from '../../types'
import { formatarDataHora } from '../../utils/formatacao'
import { SITUACAO_EVENTO_STYLES } from './constantes'
import HistoricoDesignacoes from './HistoricoDesignacoes'

type Props = {
  evento: EventoOperacional
  equipes: Equipe[]
  aoFechar: () => void
}

function HistoricoTransicoes({ evento, equipes, aoFechar }: Props) {
  const { usuario } = useAuth()
  const [transicoes, setTransicoes] = useState<TransicaoEvento[]>([])
  const [pagina, setPagina] = useState(0)
  const [ultimaPagina, setUltimaPagina] = useState(true)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro('')

    listarTransicoes(evento.id, pagina)
      .then((resposta) => {
        if (!ativo) return
        setTransicoes((atuais) => (pagina === 0 ? resposta.content : [...atuais, ...resposta.content]))
        setUltimaPagina(resposta.last)
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [evento.id, pagina])

  const autor = (id: number) => (id === usuario?.id ? 'você' : `usuário #${id}`)

  return (
    <Modal titulo={`Histórico: ${evento.descricao}`} aoFechar={aoFechar}>
      <h4 className="mb-3 text-sm font-semibold text-slate-300">Transições de situação</h4>
      <ol className="space-y-3" aria-label="Transições do evento">
        {transicoes.map((t) => (
          <li key={t.id} className="rounded-lg border border-slate-800 p-3">
            <p className="text-sm text-slate-200">
              <span className={SITUACAO_EVENTO_STYLES[t.situacaoAnterior].text}>{SITUACAO_EVENTO_STYLES[t.situacaoAnterior].label}</span>
              {' → '}
              <span className={SITUACAO_EVENTO_STYLES[t.situacaoNova].text}>{SITUACAO_EVENTO_STYLES[t.situacaoNova].label}</span>
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {formatarDataHora(t.alteradoEm)} por {autor(t.usuarioId)}
            </p>
            <p className="mt-2 whitespace-pre-line text-sm text-slate-300">{t.comentario}</p>
          </li>
        ))}
      </ol>

      {carregando && <p className="py-6 text-center text-sm text-slate-500">Carregando histórico...</p>}
      {!carregando && erro && <p className="py-6 text-center text-sm text-red-400">{erro}</p>}
      {!carregando && !erro && transicoes.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-500">Nenhuma alteração de situação registrada.</p>
      )}
      {!carregando && !erro && !ultimaPagina && (
        <div className="mt-4 flex justify-center">
          <button
            type="button"
            onClick={() => setPagina((atual) => atual + 1)}
            className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
          >
            Carregar mais
          </button>
        </div>
      )}

      <HistoricoDesignacoes eventoId={evento.id} equipes={equipes} />
    </Modal>
  )
}

export default HistoricoTransicoes
