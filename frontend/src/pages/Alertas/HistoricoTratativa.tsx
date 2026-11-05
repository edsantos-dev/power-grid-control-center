import { useEffect, useState } from 'react'
import Modal from '../../components/Modal'
import { useAuth } from '../../hooks/useAuth'
import { buscarAlerta, listarTratativasAlerta } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { Alerta, TratativaAlerta } from '../../types'
import { CRITICIDADE_STYLES, SITUACAO_ALERTA_LABEL } from '../../utils/constants'
import { formatarDataHora } from '../../utils/formatacao'
import { autorTratativa, descreverEtapa } from './tratativa'

type Props = {
  id: number
  rotulo: string
  aoFechar: () => void
}

function HistoricoTratativa({ id, rotulo, aoFechar }: Props) {
  const { usuario } = useAuth()
  const [alerta, setAlerta] = useState<Alerta | null>(null)
  const [tratativas, setTratativas] = useState<TratativaAlerta[]>([])
  const [pagina, setPagina] = useState(0)
  const [ultimaPagina, setUltimaPagina] = useState(true)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')

  // O detalhe traz a autoria consolidada de cada etapa, mesmo depois da resolução
  useEffect(() => {
    let ativo = true
    buscarAlerta(id)
      .then((resposta) => ativo && setAlerta(resposta))
      .catch(() => ativo && setAlerta(null))

    return () => {
      ativo = false
    }
  }, [id])

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro('')

    listarTratativasAlerta(id, pagina)
      .then((resposta) => {
        if (!ativo) return
        setTratativas((atuais) => (pagina === 0 ? resposta.content : [...atuais, ...resposta.content]))
        setUltimaPagina(resposta.last)
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [id, pagina])

  const etapas = alerta
    ? [
        descreverEtapa('Reconhecido', alerta.reconhecidoEm, alerta.reconhecidoPorUsuarioId, usuario),
        descreverEtapa('Resolvido', alerta.resolvidoEm, alerta.resolvidoPorUsuarioId, usuario),
      ].filter(Boolean)
    : []

  return (
    <Modal titulo={`Tratativa: ${rotulo}`} aoFechar={aoFechar}>
      {alerta && (
        <div className="mb-4 rounded-lg border border-slate-800 bg-slate-950 p-3 text-sm">
          <p className="text-slate-200">
            {SITUACAO_ALERTA_LABEL[alerta.situacao]}
            <span className={`ml-2 text-xs ${CRITICIDADE_STYLES[alerta.criticidade].text}`}>
              {CRITICIDADE_STYLES[alerta.criticidade].label}
            </span>
          </p>
          <p className="mt-1 text-xs text-slate-500">Gerado em {formatarDataHora(alerta.geradoEm)}</p>
          {etapas.map((etapa) => (
            <p key={etapa} className="mt-1 text-xs text-slate-400">{etapa}</p>
          ))}
        </div>
      )}

      <ol className="space-y-3" aria-label="Tratativas do alerta">
        {tratativas.map((t) => (
          <li key={t.id} className="rounded-lg border border-slate-800 p-3">
            <p className="text-sm text-slate-200">
              {SITUACAO_ALERTA_LABEL[t.situacaoAnterior]} → {SITUACAO_ALERTA_LABEL[t.situacaoNova]}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {formatarDataHora(t.alteradoEm)} por {autorTratativa(t.usuarioId, usuario)}
            </p>
            <p className="mt-2 whitespace-pre-line text-sm text-slate-300">{t.comentario}</p>
          </li>
        ))}
      </ol>

      {carregando && <p className="py-6 text-center text-sm text-slate-500">Carregando tratativas...</p>}
      {!carregando && erro && <p className="py-6 text-center text-sm text-red-400">{erro}</p>}
      {!carregando && !erro && tratativas.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-500">Nenhuma tratativa registrada.</p>
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
    </Modal>
  )
}

export default HistoricoTratativa
