import { useEffect, useState } from 'react'
import { Search, MapPin, Pencil, Plus, Building2 } from 'lucide-react'
import { listarAlimentadores } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import { listarSubestacoes } from '../../services/subestacoes'
import type { Alimentador, EstadoOperacional, Subestacao } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { formatarData, formatarNumero } from '../../utils/formatacao'
import AlimentadorForm from './AlimentadorForm'

function Alimentadores() {
  const [alimentadores, setAlimentadores] = useState<Alimentador[]>([])
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<EstadoOperacional | 'todos'>('todos')
  const [busca, setBusca] = useState('')
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [emEdicao, setEmEdicao] = useState<Alimentador | null>(null)

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      const [listaAlimentadores, listaSubestacoes] = await Promise.all([listarAlimentadores(), listarSubestacoes()])
      setAlimentadores(listaAlimentadores)
      setSubestacoes(listaSubestacoes)
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [])

  function nomeSubestacao(id: number) {
    return subestacoes.find((s) => s.id === id)?.nome ?? `Subestação #${id}`
  }

  function abrirFormulario(alimentador: Alimentador | null) {
    setEmEdicao(alimentador)
    setFormularioAberto(true)
  }

  function handleSalvar(salvo: Alimentador) {
    setAlimentadores((atuais) => [...atuais.filter((a) => a.id !== salvo.id), salvo])
    setFormularioAberto(false)
  }

  // A API não garante ordenação da listagem
  const alimentadoresFiltrados = alimentadores
    .filter((a) => {
      const termo = busca.toLowerCase()
      const passaStatus = filtroStatus === 'todos' || a.estadoOperacional === filtroStatus
      const passaBusca = a.nome.toLowerCase().includes(termo) || nomeSubestacao(a.subestacaoId).toLowerCase().includes(termo)
      return passaStatus && passaBusca
    })
    .sort((a, b) => a.id - b.id)

  const totalOperacionais = alimentadores.filter((a) => a.estadoOperacional === 'OPERACIONAL').length
  const totalManutencao = alimentadores.filter((a) => a.estadoOperacional === 'MANUTENCAO').length
  const totalFalha = alimentadores.filter((a) => a.estadoOperacional === 'FALHA').length
  const subestacoesAtendidas = new Set(alimentadores.map((a) => a.subestacaoId)).size

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Alimentadores
          </h2>

          <p className="mt-2 text-slate-400">
            Cadastro dos alimentadores de distribuição.
          </p>
        </div>

        <button
          type="button"
          onClick={() => abrirFormulario(null)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Novo alimentador
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {alimentadores.length} alimentadores</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em manutenção</p>
          <p className="mt-2 text-3xl font-bold text-slate-300">{totalManutencao}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em falha</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalFalha}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Subestações com alimentadores</p>
          <p className="mt-2 text-3xl font-bold text-white">{subestacoesAtendidas}</p>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Buscar alimentador ou subestação..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'OPERACIONAL', 'FALHA', 'MANUTENCAO'] as const).map((status) => (
              <button
                key={status}
                onClick={() => setFiltroStatus(status)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroStatus === status
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {status === 'todos' ? 'Todos' : ESTADO_STYLES[status].label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Alimentador</th>
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Localização</th>
                <th className="pb-3 font-normal">Tensão (kV)</th>
                <th className="pb-3 font-normal">Instalação</th>
                <th className="pb-3 font-normal">Status</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {alimentadoresFiltrados.map((a) => {
                const status = ESTADO_STYLES[a.estadoOperacional]

                return (
                  <tr key={a.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{a.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">#{a.id}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <Building2 className="h-3.5 w-3.5 text-slate-500" />
                        {nomeSubestacao(a.subestacaoId)}
                      </span>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {a.latitude}, {a.longitude}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{formatarNumero(a.tensaoNominalKv)}</td>
                    <td className="py-3 text-slate-300">{formatarData(a.dataInstalacao)}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                    <td className="py-3">
                      <button
                        type="button"
                        onClick={() => abrirFormulario(a)}
                        aria-label={`Editar ${a.nome}`}
                        className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {carregando && (
            <p className="py-10 text-center text-sm text-slate-500">Carregando alimentadores...</p>
          )}

          {!carregando && erro && (
            <div className="flex flex-col items-center gap-3 py-10">
              <p className="text-sm text-red-400">{erro}</p>
              <button
                type="button"
                onClick={carregar}
                className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
              >
                Tentar novamente
              </button>
            </div>
          )}

          {!carregando && !erro && alimentadoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum alimentador encontrado.</p>
            </div>
          )}
        </div>
      </div>

      {formularioAberto && (
        <AlimentadorForm
          alimentador={emEdicao}
          subestacoes={subestacoes}
          aoFechar={() => setFormularioAberto(false)}
          aoSalvar={handleSalvar}
        />
      )}
    </div>
  )
}

export default Alimentadores
