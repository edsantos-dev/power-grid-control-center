import { useEffect, useState } from 'react'
import { Search, Pencil, Plus, Building2, Gauge } from 'lucide-react'
import { listarAlimentadores } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import { listarSubestacoes } from '../../services/subestacoes'
import { consultarUltimaMedicao, listarTransformadores } from '../../services/transformadores'
import type { Alimentador, EstadoOperacional, MedicaoTransformador, Subestacao, Transformador } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { formatarData, formatarDataHora, formatarNumero, formatarPercentual } from '../../utils/formatacao'
import TransformadorForm from './TransformadorForm'

type Carga =
  | { estado: 'carregando' }
  | { estado: 'erro' }
  | { estado: 'pronta'; medicao: MedicaoTransformador | null }

function CelulaCarga({ carga }: { carga: Carga | undefined }) {
  if (!carga || carga.estado === 'carregando') {
    return <span className="text-slate-500">...</span>
  }

  if (carga.estado === 'erro') {
    return <span className="text-slate-500">Indisponível</span>
  }

  if (!carga.medicao) {
    return <span className="text-slate-500">Sem medição</span>
  }

  const { cargaPercentual, medidoEm, origem } = carga.medicao
  const cor = cargaPercentual > 100 ? 'text-red-400' : cargaPercentual >= 90 ? 'text-yellow-400' : 'text-slate-300'

  return (
    <div>
      <span className={`flex items-center gap-1.5 ${cor}`}>
        <Gauge className="h-3.5 w-3.5" />
        {formatarPercentual(cargaPercentual)}
      </span>
      <p className="mt-1 text-xs text-slate-500">
        {formatarDataHora(medidoEm)} · {origem === 'SIMULADA' ? 'Simulada' : 'Real'}
      </p>
    </div>
  )
}

function Transformadores() {
  const [transformadores, setTransformadores] = useState<Transformador[]>([])
  const [alimentadores, setAlimentadores] = useState<Alimentador[]>([])
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])
  const [cargas, setCargas] = useState<Record<number, Carga>>({})
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroStatus, setFiltroStatus] = useState<EstadoOperacional | 'todos'>('todos')
  const [busca, setBusca] = useState('')
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [emEdicao, setEmEdicao] = useState<Transformador | null>(null)

  // Cada transformador consulta sua última medição sem bloquear o cadastro
  function carregarCarga(id: number) {
    setCargas((atuais) => ({ ...atuais, [id]: { estado: 'carregando' } }))

    consultarUltimaMedicao(id)
      .then((medicao) => setCargas((atuais) => ({ ...atuais, [id]: { estado: 'pronta', medicao } })))
      .catch(() => setCargas((atuais) => ({ ...atuais, [id]: { estado: 'erro' } })))
  }

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      const [listaTransformadores, listaAlimentadores, listaSubestacoes] = await Promise.all([
        listarTransformadores(),
        listarAlimentadores(),
        listarSubestacoes(),
      ])
      setTransformadores(listaTransformadores)
      setAlimentadores(listaAlimentadores)
      setSubestacoes(listaSubestacoes)
      listaTransformadores.forEach((t) => carregarCarga(t.id))
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [])

  function nomeAlimentador(id: number) {
    return alimentadores.find((a) => a.id === id)?.nome ?? `Alimentador #${id}`
  }

  function nomeSubestacao(id: number) {
    return subestacoes.find((s) => s.id === id)?.nome ?? `Subestação #${id}`
  }

  function abrirFormulario(transformador: Transformador | null) {
    setEmEdicao(transformador)
    setFormularioAberto(true)
  }

  function handleSalvar(salvo: Transformador) {
    setTransformadores((atuais) => [...atuais.filter((t) => t.id !== salvo.id), salvo])
    setFormularioAberto(false)

    if (!cargas[salvo.id]) {
      carregarCarga(salvo.id)
    }
  }

  // A API não garante ordenação da listagem
  const transformadoresFiltrados = transformadores
    .filter((t) => {
      const termo = busca.toLowerCase()
      const passaStatus = filtroStatus === 'todos' || t.estadoOperacional === filtroStatus
      const passaBusca =
        t.nome.toLowerCase().includes(termo) ||
        nomeAlimentador(t.alimentadorId).toLowerCase().includes(termo) ||
        nomeSubestacao(t.subestacaoId).toLowerCase().includes(termo)
      return passaStatus && passaBusca
    })
    .sort((a, b) => a.id - b.id)

  const totalOperacionais = transformadores.filter((t) => t.estadoOperacional === 'OPERACIONAL').length
  const totalManutencao = transformadores.filter((t) => t.estadoOperacional === 'MANUTENCAO').length
  const totalFalha = transformadores.filter((t) => t.estadoOperacional === 'FALHA').length
  const capacidadeTotal = transformadores.reduce((acc, t) => acc + t.capacidadeMva, 0)

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Transformadores
          </h2>

          <p className="mt-2 text-slate-400">
            Cadastro dos transformadores e carga da última medição.
          </p>
        </div>

        <button
          type="button"
          onClick={() => abrirFormulario(null)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Novo transformador
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Operacionais</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOperacionais}</p>
          <p className="mt-2 text-sm text-green-400">de {transformadores.length} transformadores</p>
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
              placeholder="Buscar transformador, alimentador ou subestação..."
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
                <th className="pb-3 font-normal">Transformador</th>
                <th className="pb-3 font-normal">Alimentador</th>
                <th className="pb-3 font-normal">Subestação</th>
                <th className="pb-3 font-normal">Capacidade (MVA)</th>
                <th className="pb-3 font-normal">Carga</th>
                <th className="pb-3 font-normal">Instalação</th>
                <th className="pb-3 font-normal">Status</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {transformadoresFiltrados.map((t) => {
                const status = ESTADO_STYLES[t.estadoOperacional]

                return (
                  <tr key={t.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{t.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">#{t.id}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">{nomeAlimentador(t.alimentadorId)}</td>
                    <td className="py-3 pr-4 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <Building2 className="h-3.5 w-3.5 text-slate-500" />
                        {nomeSubestacao(t.subestacaoId)}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{formatarNumero(t.capacidadeMva)}</td>
                    <td className="py-3 pr-4">
                      <CelulaCarga carga={cargas[t.id]} />
                    </td>
                    <td className="py-3 text-slate-300">{formatarData(t.dataInstalacao)}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                    <td className="py-3">
                      <button
                        type="button"
                        onClick={() => abrirFormulario(t)}
                        aria-label={`Editar ${t.nome}`}
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
            <p className="py-10 text-center text-sm text-slate-500">Carregando transformadores...</p>
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

          {!carregando && !erro && transformadoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum transformador encontrado.</p>
            </div>
          )}
        </div>
      </div>

      {formularioAberto && (
        <TransformadorForm
          transformador={emEdicao}
          alimentadores={alimentadores}
          subestacoes={subestacoes}
          aoFechar={() => setFormularioAberto(false)}
          aoSalvar={handleSalvar}
        />
      )}
    </div>
  )
}

export default Transformadores
