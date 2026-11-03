import { useEffect, useState, type ElementType } from 'react'
import { CheckCheck } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import type { PageResponse, StatusAlertaAutomatico } from '../../types'
import { formatarDataHora } from '../../utils/formatacao'

export type AlertaAutomatico = {
  id: number
  status: StatusAlertaAutomatico
  reconhecidoEm: string | null
  reconhecidoPorUsuarioId: number | null
}

type Opcao = { valor: string; label: string }

type Props<T extends AlertaAutomatico> = {
  titulo: string
  descricao: string
  rotuloLista: string
  icone: ElementType
  cor: { texto: string; fundo: string; borda: string }
  filtroExtra?: { rotulo: string; opcoes: Opcao[] }
  listar: (page: number, filtros: { status?: StatusAlertaAutomatico; extra?: string }) => Promise<PageResponse<T>>
  reconhecer: (id: number) => Promise<T>
  descrever: (alerta: T) => { resumo: string; detalhe: string; alvo: string }
}

type FiltroStatus = StatusAlertaAutomatico | 'todos'

const filtrosStatus: { valor: FiltroStatus; label: string }[] = [
  { valor: 'todos', label: 'Todos' },
  { valor: 'ATIVO', label: 'Não reconhecidos' },
  { valor: 'RECONHECIDO', label: 'Reconhecidos' },
]

function BotoesFiltro<V extends string>({ rotulo, opcoes, valor, aoAlterar }: { rotulo: string; opcoes: { valor: V; label: string }[]; valor: V; aoAlterar: (valor: V) => void }) {
  return (
    <div className="flex flex-wrap gap-2" role="group" aria-label={rotulo}>
      {opcoes.map((opcao) => (
        <button
          key={opcao.valor}
          onClick={() => aoAlterar(opcao.valor)}
          aria-pressed={valor === opcao.valor}
          className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
            valor === opcao.valor
              ? 'border-slate-600 bg-slate-800 text-white'
              : 'border-slate-800 text-slate-400 hover:border-slate-700'
          }`}
        >
          {opcao.label}
        </button>
      ))}
    </div>
  )
}

// Seção comum aos alertas gerados pelo backend: lista paginada, filtros no servidor e reconhecimento
function SecaoAlertasAutomaticos<T extends AlertaAutomatico>({
  titulo,
  descricao,
  rotuloLista,
  icone: Icone,
  cor,
  filtroExtra,
  listar,
  reconhecer,
  descrever,
}: Props<T>) {
  const { usuario } = useAuth()
  const [alertas, setAlertas] = useState<T[]>([])
  const [total, setTotal] = useState(0)
  const [pagina, setPagina] = useState(0)
  const [ultimaPagina, setUltimaPagina] = useState(true)
  const [status, setStatus] = useState<FiltroStatus>('todos')
  const [extra, setExtra] = useState('')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [erroReconhecimento, setErroReconhecimento] = useState<Record<number, string>>({})
  const [reconhecendo, setReconhecendo] = useState<number | null>(null)
  const [tentativa, setTentativa] = useState(0)

  // Trocar um filtro reinicia a paginação; "Carregar mais" acrescenta a página seguinte
  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro('')

    listar(pagina, { status: status === 'todos' ? undefined : status, extra: extra || undefined })
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
  }, [listar, pagina, status, extra, tentativa])

  function descreverReconhecimento(alerta: T) {
    const autor = alerta.reconhecidoPorUsuarioId === usuario?.id ? 'você' : `usuário #${alerta.reconhecidoPorUsuarioId}`
    return `Reconhecido por ${autor}${alerta.reconhecidoEm ? ` em ${formatarDataHora(alerta.reconhecidoEm)}` : ''}`
  }

  async function handleReconhecer(id: number) {
    setReconhecendo(id)
    setErroReconhecimento((atuais) => ({ ...atuais, [id]: '' }))

    try {
      const atualizado = await reconhecer(id)
      // No filtro de não reconhecidos o alerta deixa de pertencer à lista
      setAlertas((atuais) =>
        status === 'ATIVO' ? atuais.filter((a) => a.id !== id) : atuais.map((a) => (a.id === id ? atualizado : a))
      )
      if (status === 'ATIVO') setTotal((atual) => atual - 1)
    } catch (error) {
      setErroReconhecimento((atuais) => ({ ...atuais, [id]: extrairErroApi(error).mensagem }))
    } finally {
      setReconhecendo(null)
    }
  }

  const idTitulo = `titulo-${rotuloLista.toLowerCase().replace(/\s+/g, '-')}`

  return (
    <section className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby={idTitulo}>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h3 id={idTitulo} className="text-lg font-semibold text-white">{titulo}</h3>
          <p className="mt-1 text-sm text-slate-400">{descricao}</p>
        </div>

        <div className="flex flex-col gap-2 lg:items-end">
          <BotoesFiltro
            rotulo={`Status dos ${rotuloLista.toLowerCase()}`}
            opcoes={filtrosStatus}
            valor={status}
            aoAlterar={(valor) => {
              setStatus(valor)
              setPagina(0)
            }}
          />
          {filtroExtra && (
            <BotoesFiltro
              rotulo={filtroExtra.rotulo}
              opcoes={[{ valor: '', label: 'Todos os tipos' }, ...filtroExtra.opcoes]}
              valor={extra}
              aoAlterar={(valor) => {
                setExtra(valor)
                setPagina(0)
              }}
            />
          )}
        </div>
      </div>

      {!erro && !(carregando && pagina === 0) && (
        <p className="mt-4 text-xs text-slate-500">{total} alerta(s) neste filtro</p>
      )}

      <ul className="mt-3 space-y-3" aria-label={rotuloLista}>
        {alertas.map((alerta) => {
          const { resumo, detalhe, alvo } = descrever(alerta)

          return (
            <li key={alerta.id} className={`flex flex-wrap items-start justify-between gap-4 rounded-lg border p-4 ${cor.borda}`}>
              <div className="flex items-start gap-3">
                <span className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${cor.fundo}`}>
                  <Icone className={`h-4 w-4 ${cor.texto}`} />
                </span>

                <div>
                  <p className="text-sm text-slate-200">{resumo}</p>
                  <p className="mt-1 text-xs text-slate-500">{detalhe}</p>
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
                  onClick={() => handleReconhecer(alerta.id)}
                  disabled={reconhecendo === alerta.id}
                  aria-label={`Reconhecer alerta de ${alvo}`}
                  className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <CheckCheck className="h-3.5 w-3.5" />
                  {reconhecendo === alerta.id ? 'Reconhecendo...' : 'Reconhecer'}
                </button>
              ) : (
                <span className="text-xs text-yellow-400">Reconhecido</span>
              )}
            </li>
          )
        })}
      </ul>

      {carregando && (
        <p className="py-6 text-center text-sm text-slate-500">Carregando {rotuloLista.toLowerCase()}...</p>
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
        <p className="py-6 text-center text-sm text-slate-500">Nenhum alerta neste filtro.</p>
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

export default SecaoAlertasAutomaticos
