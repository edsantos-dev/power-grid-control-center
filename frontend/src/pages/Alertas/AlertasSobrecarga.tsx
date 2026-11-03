import { useEffect, useState } from 'react'
import { Gauge, CheckCheck } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { listarAlertasSobrecarga, reconhecerAlertaSobrecarga } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import type { AlertaSobrecarga, StatusAlertaAutomatico } from '../../types'
import { formatarDataHora, formatarPercentual } from '../../utils/formatacao'

type Props = {
  ativos: AtivoCatalogo[]
}

type Filtro = StatusAlertaAutomatico | 'todos'

const filtros: { valor: Filtro; label: string }[] = [
  { valor: 'todos', label: 'Todos' },
  { valor: 'ATIVO', label: 'Não reconhecidos' },
  { valor: 'RECONHECIDO', label: 'Reconhecidos' },
]

function AlertasSobrecarga({ ativos }: Props) {
  const { usuario } = useAuth()
  const [alertas, setAlertas] = useState<AlertaSobrecarga[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(0)
  const [ultimaPagina, setUltimaPagina] = useState(true)
  const [filtro, setFiltro] = useState<Filtro>('todos')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [erroReconhecimento, setErroReconhecimento] = useState<Record<number, string>>({})
  const [reconhecendo, setReconhecendo] = useState<number | null>(null)
  const [tentativa, setTentativa] = useState(0)

  // Trocar o filtro reinicia a paginação; "Carregar mais" acrescenta a página seguinte
  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro('')

    listarAlertasSobrecarga(pagina, filtro === 'todos' ? undefined : filtro)
      .then((resposta) => {
        if (!ativo) return
        setAlertas((atuais) => (pagina === 0 ? resposta.content : [...atuais, ...resposta.content]))
        setTotal(resposta.totalElements)
        setUltimaPagina(resposta.last)
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [pagina, filtro, tentativa])

  function trocarFiltro(novo: Filtro) {
    setFiltro(novo)
    setPagina(0)
  }

  function nomeTransformador(id: number) {
    const ativo = ativos.find((a) => a.id === id)
    return ativo ? ativo.nome : `Transformador #${id}`
  }

  function descreverReconhecimento(alerta: AlertaSobrecarga) {
    const autor = alerta.reconhecidoPorUsuarioId === usuario?.id ? 'você' : `usuário #${alerta.reconhecidoPorUsuarioId}`
    return `Reconhecido por ${autor}${alerta.reconhecidoEm ? ` em ${formatarDataHora(alerta.reconhecidoEm)}` : ''}`
  }

  async function reconhecer(id: number) {
    setReconhecendo(id)
    setErroReconhecimento((atuais) => ({ ...atuais, [id]: '' }))

    try {
      const atualizado = await reconhecerAlertaSobrecarga(id)
      // No filtro de não reconhecidos o alerta deixa de pertencer à lista
      setAlertas((atuais) =>
        filtro === 'ATIVO' ? atuais.filter((a) => a.id !== id) : atuais.map((a) => (a.id === id ? atualizado : a))
      )
      if (filtro === 'ATIVO') setTotal((atual) => atual - 1)
    } catch (error) {
      setErroReconhecimento((atuais) => ({ ...atuais, [id]: extrairErroApi(error).mensagem }))
    } finally {
      setReconhecendo(null)
    }
  }

  return (
    <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-sobrecarga">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h3 id="titulo-sobrecarga" className="text-lg font-semibold text-white">
            Sobrecarga em transformadores
          </h3>
          <p className="mt-1 text-sm text-slate-400">
            Gerados automaticamente quando uma medição ultrapassa o limiar de carga. Mais recentes primeiro.
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          {filtros.map(({ valor, label }) => (
            <button
              key={valor}
              onClick={() => trocarFiltro(valor)}
              className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                filtro === valor
                  ? 'border-slate-600 bg-slate-800 text-white'
                  : 'border-slate-800 text-slate-400 hover:border-slate-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {!erro && !(carregando && pagina === 0) && (
        <p className="mt-4 text-xs text-slate-500">{total} alerta(s) neste filtro</p>
      )}

      <ul className="mt-3 space-y-3" aria-label="Alertas de sobrecarga">
        {alertas.map((alerta) => (
          <li
            key={alerta.id}
            className="flex flex-wrap items-start justify-between gap-4 rounded-lg border border-orange-500/40 p-4"
          >
            <div className="flex items-start gap-3">
              <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-orange-500/10">
                <Gauge className="h-4 w-4 text-orange-400" />
              </span>

              <div>
                <p className="text-sm text-slate-200">
                  {nomeTransformador(alerta.ativoId)} com carga de {formatarPercentual(alerta.cargaPercentual)}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Limiar {formatarPercentual(alerta.limiarPercentual)} • {formatarDataHora(alerta.geradoEm)} • Medição #{alerta.medicaoId}
                </p>
                {alerta.status === 'RECONHECIDO' && (
                  <p className="mt-1 text-xs text-slate-400">{descreverReconhecimento(alerta)}</p>
                )}
                {erroReconhecimento[alerta.id] && (
                  <p className="mt-1 text-xs text-red-400">{erroReconhecimento[alerta.id]}</p>
                )}
              </div>
            </div>

            {alerta.status === 'ATIVO' ? (
              <button
                type="button"
                onClick={() => reconhecer(alerta.id)}
                disabled={reconhecendo === alerta.id}
                aria-label={`Reconhecer alerta de ${nomeTransformador(alerta.ativoId)}`}
                className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                <CheckCheck className="h-3.5 w-3.5" />
                {reconhecendo === alerta.id ? 'Reconhecendo...' : 'Reconhecer'}
              </button>
            ) : (
              <span className="text-xs text-yellow-400">Reconhecido</span>
            )}
          </li>
        ))}
      </ul>

      {carregando && (
        <p className="py-6 text-center text-sm text-slate-500">Carregando alertas de sobrecarga...</p>
      )}

      {!carregando && erro && (
        <div className="flex flex-col items-center gap-3 py-6">
          <p className="text-sm text-red-400">{erro}</p>
          <button
            type="button"
            onClick={() => setTentativa((atual) => atual + 1)}
            className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
          >
            Tentar novamente
          </button>
        </div>
      )}

      {!carregando && !erro && alertas.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-500">Nenhum alerta de sobrecarga neste filtro.</p>
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
    </section>
  )
}

export default AlertasSobrecarga
