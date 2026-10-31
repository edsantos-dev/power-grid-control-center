import { useEffect, useState } from 'react'
import { Search, MapPin, Pencil, Plus } from 'lucide-react'
import { extrairErroApi } from '../../services/api'
import { listarSubestacoes } from '../../services/subestacoes'
import type { EstadoOperacional, Subestacao } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { formatarData, formatarNumero } from '../../utils/formatacao'
import SubestacaoForm from './SubestacaoForm'

function Subestacoes() {
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<EstadoOperacional | 'todos'>('todos')
  const [busca, setBusca] = useState('')
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [emEdicao, setEmEdicao] = useState<Subestacao | null>(null)

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      setSubestacoes(await listarSubestacoes())
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [])

  function abrirFormulario(subestacao: Subestacao | null) {
    setEmEdicao(subestacao)
    setFormularioAberto(true)
  }

  function handleSalvar(salva: Subestacao) {
    setSubestacoes((atuais) => [...atuais.filter((s) => s.id !== salva.id), salva])
    setFormularioAberto(false)
  }

  // A API não garante ordenação da listagem
  const subestacoesFiltradas = subestacoes
    .filter((s) => {
      const passaStatus = filtroStatus === 'todos' || s.estadoOperacional === filtroStatus
      const passaBusca = s.nome.toLowerCase().includes(busca.toLowerCase())
      return passaStatus && passaBusca
    })
    .sort((a, b) => a.id - b.id)

  const totalOperacionais = subestacoes.filter((s) => s.estadoOperacional === 'OPERACIONAL').length
  const totalManutencao = subestacoes.filter((s) => s.estadoOperacional === 'MANUTENCAO').length
  const totalFalha = subestacoes.filter((s) => s.estadoOperacional === 'FALHA').length
  const capacidadeTotal = subestacoes.reduce((acc, s) => acc + s.capacidadeMva, 0)

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Subestações
          </h2>

          <p className="mt-2 text-slate-400">
            Cadastro das subestações da rede elétrica.
          </p>
        </div>

        <button
          type="button"
          onClick={() => abrirFormulario(null)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Nova subestação
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {subestacoes.length} subestações</p>
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
          <p className="text-sm text-slate-400">Capacidade cadastrada</p>
          <p className="mt-2 text-3xl font-bold text-white">{formatarNumero(capacidadeTotal)} MVA</p>
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
              placeholder="Buscar subestação..."
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
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Localização</th>
                <th className="pb-3 font-normal">Tensão (kV)</th>
                <th className="pb-3 font-normal">Capacidade (MVA)</th>
                <th className="pb-3 font-normal">Instalação</th>
                <th className="pb-3 font-normal">Status</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {subestacoesFiltradas.map((s) => {
                const status = ESTADO_STYLES[s.estadoOperacional]

                return (
                  <tr key={s.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{s.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">#{s.id}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {s.latitude}, {s.longitude}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{formatarNumero(s.tensaoNominalKv)}</td>
                    <td className="py-3 text-slate-300">{formatarNumero(s.capacidadeMva)}</td>
                    <td className="py-3 text-slate-300">{formatarData(s.dataInstalacao)}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                    <td className="py-3">
                      <button
                        type="button"
                        onClick={() => abrirFormulario(s)}
                        aria-label={`Editar ${s.nome}`}
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
            <p className="py-10 text-center text-sm text-slate-500">Carregando subestações...</p>
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

          {!carregando && !erro && subestacoesFiltradas.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhuma subestação encontrada.</p>
            </div>
          )}
        </div>
      </div>

      {formularioAberto && (
        <SubestacaoForm
          subestacao={emEdicao}
          aoFechar={() => setFormularioAberto(false)}
          aoSalvar={handleSalvar}
        />
      )}
    </div>
  )
}

export default Subestacoes
