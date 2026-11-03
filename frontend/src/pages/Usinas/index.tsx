import { useEffect, useState } from 'react'
import { Search, MapPin, Pencil, Plus, Building2, ChevronLeft, ChevronRight } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import { listarSubestacoes } from '../../services/subestacoes'
import { listarUsinas } from '../../services/usinas'
import type { EstadoOperacional, PageResponse, Subestacao, TipoGeracao, Usina } from '../../types'
import { ESTADO_STYLES, ESTADOS } from '../../utils/constants'
import { formatarData, formatarNumero } from '../../utils/formatacao'
import { TIPO_GERACAO_LABEL, TIPOS_GERACAO } from './constantes'
import UsinaForm from './UsinaForm'

function Usinas() {
  const { usuario } = useAuth()
  const [resultado, setResultado] = useState<PageResponse<Usina> | null>(null)
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])
  const [pagina, setPagina] = useState(0)
  const [nome, setNome] = useState('')
  const [tipo, setTipo] = useState<TipoGeracao | ''>('')
  const [estado, setEstado] = useState<EstadoOperacional | ''>('')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [tentativa, setTentativa] = useState(0)
  const [formulario, setFormulario] = useState<{ usina: Usina | null } | null>(null)

  // Somente o gerente cadastra e edita usinas
  const podeEditar = usuario?.perfil === 'GERENTE_OPERACOES'

  useEffect(() => {
    listarSubestacoes().then(setSubestacoes).catch(() => setSubestacoes([]))
  }, [])

  // Filtros aplicados no servidor; a busca por nome espera a digitação terminar
  useEffect(() => {
    let ativo = true
    const espera = setTimeout(() => {
      setCarregando(true)
      listarUsinas(pagina, { nome: nome.trim() || undefined, tipoGeracao: tipo || undefined, estadoOperacional: estado || undefined })
        .then((resposta) => {
          if (!ativo) return
          setResultado(resposta)
          setErro('')
        })
        .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
        .finally(() => ativo && setCarregando(false))
    }, 250)

    return () => {
      ativo = false
      clearTimeout(espera)
    }
  }, [pagina, nome, tipo, estado, tentativa])

  function nomeSubestacao(id: number) {
    return subestacoes.find((s) => s.id === id)?.nome ?? `Subestação #${id}`
  }

  function handleSalvar() {
    setFormulario(null)
    setTentativa((atual) => atual + 1)
  }

  const usinas = resultado?.content ?? []

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Usinas
          </h2>

          <p className="mt-2 text-slate-400">
            Usinas geradoras conectadas à rede, com capacidade máxima nominal.
          </p>
        </div>

        {podeEditar && (
          <button
            type="button"
            onClick={() => setFormulario({ usina: null })}
            className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
          >
            <Plus className="h-4 w-4" />
            Nova usina
          </button>
        )}
      </div>

      <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="relative w-full lg:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={nome}
              onChange={(e) => {
                setNome(e.target.value)
                setPagina(0)
              }}
              placeholder="Buscar usina pelo nome..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <select aria-label="Filtrar por tipo de geração" value={tipo} onChange={(e) => { setTipo(e.target.value as TipoGeracao | ''); setPagina(0) }} className="rounded-lg border border-slate-800 bg-slate-950 px-3 py-1.5 text-xs text-slate-300">
              <option value="">Todos os tipos</option>
              {TIPOS_GERACAO.map((t) => (
                <option key={t} value={t}>{TIPO_GERACAO_LABEL[t]}</option>
              ))}
            </select>
            <select aria-label="Filtrar por status" value={estado} onChange={(e) => { setEstado(e.target.value as EstadoOperacional | ''); setPagina(0) }} className="rounded-lg border border-slate-800 bg-slate-950 px-3 py-1.5 text-xs text-slate-300">
              <option value="">Todos os status</option>
              {ESTADOS.map((e) => (
                <option key={e} value={e}>{ESTADO_STYLES[e].label}</option>
              ))}
            </select>
          </div>
        </div>

        <p className="mt-4 text-xs text-slate-500">
          {resultado ? `${resultado.totalElements} usina(s) encontrada(s)` : ''} · A geração observada não é medida neste cadastro: os valores são capacidades nominais.
        </p>

        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Usina</th>
                <th className="pb-3 font-normal">Tipo</th>
                <th className="pb-3 font-normal">Capacidade máxima</th>
                <th className="pb-3 font-normal">Geração observada</th>
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Localização</th>
                <th className="pb-3 font-normal">Status</th>
                {podeEditar && <th className="pb-3 font-normal">Ações</th>}
              </tr>
            </thead>
            <tbody>
              {usinas.map((u) => {
                const status = ESTADO_STYLES[u.estadoOperacional]

                return (
                  <tr key={u.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{u.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">#{u.id}{u.dataInstalacao ? ` · instalada em ${formatarData(u.dataInstalacao)}` : ''}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-300">{TIPO_GERACAO_LABEL[u.tipoGeracao]}</td>
                    <td className="py-3 pr-4 text-slate-300">{formatarNumero(u.capacidadeMaximaMw)} MW</td>
                    <td className="py-3 pr-4 text-xs text-slate-500">Sem medição</td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <Building2 className="h-3.5 w-3.5 text-slate-500" />
                        {nomeSubestacao(u.subestacaoId)}
                      </span>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {u.latitude}, {u.longitude}
                      </span>
                    </td>
                    <td className="py-3 pr-4">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                    {podeEditar && (
                      <td className="py-3">
                        <button
                          type="button"
                          onClick={() => setFormulario({ usina: u })}
                          aria-label={`Editar ${u.nome}`}
                          className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white"
                        >
                          <Pencil className="h-4 w-4" />
                        </button>
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>

          {carregando && !resultado && <p className="py-10 text-center text-sm text-slate-500">Carregando usinas...</p>}

          {!carregando && erro && (
            <div className="flex flex-col items-center gap-3 py-10">
              <p className="text-sm text-red-400">{erro}</p>
              <button type="button" onClick={() => setTentativa((atual) => atual + 1)} className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800">
                Tentar novamente
              </button>
            </div>
          )}

          {!carregando && !erro && usinas.length === 0 && (
            <p className="py-10 text-center text-sm text-slate-500">Nenhuma usina encontrada.</p>
          )}
        </div>

        {resultado && resultado.totalPages > 1 && (
          <div className="mt-4 flex items-center justify-end gap-3 text-sm text-slate-400">
            <button type="button" disabled={resultado.first} onClick={() => setPagina((atual) => atual - 1)} aria-label="Página anterior" className="rounded-lg border border-slate-700 p-1.5 transition hover:bg-slate-800 disabled:opacity-40">
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span>Página {resultado.page + 1} de {resultado.totalPages}</span>
            <button type="button" disabled={resultado.last} onClick={() => setPagina((atual) => atual + 1)} aria-label="Próxima página" className="rounded-lg border border-slate-700 p-1.5 transition hover:bg-slate-800 disabled:opacity-40">
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>
        )}
      </div>

      {formulario && (
        <UsinaForm usina={formulario.usina} subestacoes={subestacoes} aoFechar={() => setFormulario(null)} aoSalvar={handleSalvar} />
      )}
    </div>
  )
}

export default Usinas
