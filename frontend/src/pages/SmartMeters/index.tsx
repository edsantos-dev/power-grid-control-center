import { useEffect, useState } from 'react'
import { Search, Plus, PlugZap, Zap } from 'lucide-react'
import { CadeiaRede, Fornecimento } from '../../components/medidores'
import { listarAlimentadores } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import { listarConsumidores } from '../../services/consumidores'
import { listarMedidores } from '../../services/medidores'
import { listarSubestacoes } from '../../services/subestacoes'
import { listarTransformadores } from '../../services/transformadores'
import type { Alimentador, Consumidor, Medidor, Subestacao, Transformador } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { formatarData } from '../../utils/formatacao'
import { nomesPorId } from '../../utils/rede'
import ConsumoMedidor from './ConsumoMedidor'
import InstalacaoForm from './InstalacaoForm'
import MedidorForm from './MedidorForm'

type Situacao = 'todos' | 'instalados' | 'estoque'

const situacaoLabel: Record<Situacao, string> = {
  todos: 'Todos',
  instalados: 'Instalados',
  estoque: 'Em estoque',
}

// Estoque não é um estado operacional: é a ausência de vínculo de instalação
function estaInstalado(medidor: Medidor) {
  return medidor.consumidorId !== null
}

function Smartmeters() {
  const [medidores, setMedidores] = useState<Medidor[]>([])
  const [consumidores, setConsumidores] = useState<Consumidor[]>([])
  const [transformadores, setTransformadores] = useState<Transformador[]>([])
  const [alimentadores, setAlimentadores] = useState<Alimentador[]>([])
  const [subestacoes, setSubestacoes] = useState<Subestacao[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroSituacao, setFiltroSituacao] = useState<Situacao>('todos')
  const [busca, setBusca] = useState('')
  const [cadastroAberto, setCadastroAberto] = useState(false)
  const [instalando, setInstalando] = useState<Medidor | null>(null)
  const [consumoDe, setConsumoDe] = useState<Medidor | null>(null)

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      const [listaMedidores, listaConsumidores, listaTransformadores, listaAlimentadores, listaSubestacoes] =
        await Promise.all([
          listarMedidores(),
          listarConsumidores(),
          listarTransformadores(),
          listarAlimentadores(),
          listarSubestacoes(),
        ])
      setMedidores(listaMedidores)
      setConsumidores(listaConsumidores)
      setTransformadores(listaTransformadores)
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

  const nomes = {
    transformadores: nomesPorId(transformadores),
    alimentadores: nomesPorId(alimentadores),
    subestacoes: nomesPorId(subestacoes),
  }

  function nomeConsumidor(id: number) {
    return consumidores.find((c) => c.id === id)?.nomeRazaoSocial ?? `Consumidor #${id}`
  }

  function handleSalvar(salvo: Medidor) {
    setMedidores((atuais) => [...atuais.filter((m) => m.id !== salvo.id), salvo])
    setCadastroAberto(false)
    setInstalando(null)
  }

  // A API não garante ordenação da listagem
  const medidoresFiltrados = medidores
    .filter((m) => {
      const termo = busca.trim().toLowerCase()
      const passaSituacao =
        filtroSituacao === 'todos' || (filtroSituacao === 'instalados') === estaInstalado(m)
      const passaBusca =
        m.nome.toLowerCase().includes(termo) ||
        m.numeroSerie.toLowerCase().includes(termo) ||
        (m.consumidorId !== null && nomeConsumidor(m.consumidorId).toLowerCase().includes(termo))
      return passaSituacao && passaBusca
    })
    .sort((a, b) => a.id - b.id)

  const totalInstalados = medidores.filter(estaInstalado).length
  const totalEstoque = medidores.length - totalInstalados
  const totalForaDeOperacao = medidores.filter((m) => m.estadoOperacional !== 'OPERACIONAL').length

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Smartmeters
          </h2>

          <p className="mt-2 text-slate-400">
            Cadastro, estoque e instalação dos medidores inteligentes.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setCadastroAberto(true)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Novo medidor
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Medidores</p>
          <p className="mt-2 text-3xl font-bold text-white">{medidores.length}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Instalados</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalInstalados}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em estoque</p>
          <p className="mt-2 text-3xl font-bold text-blue-400">{totalEstoque}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Em falha ou manutenção</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalForaDeOperacao}</p>
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
              placeholder="Buscar medidor, série ou consumidor..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'instalados', 'estoque'] as const).map((situacao) => (
              <button
                key={situacao}
                onClick={() => setFiltroSituacao(situacao)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroSituacao === situacao
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {situacaoLabel[situacao]}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Medidor</th>
                <th className="pb-3 font-normal">Consumidor</th>
                <th className="pb-3 font-normal">Fornecimento</th>
                <th className="pb-3 font-normal">Vínculo na rede</th>
                <th className="pb-3 font-normal">Instalação</th>
                <th className="pb-3 font-normal">Status</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {medidoresFiltrados.map((m) => {
                const status = ESTADO_STYLES[m.estadoOperacional]

                return (
                  <tr key={m.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{m.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">Série {m.numeroSerie}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-300">
                      {m.consumidorId === null ? (
                        <span className="rounded-md bg-blue-500/10 px-2 py-0.5 text-xs text-blue-400">Em estoque</span>
                      ) : (
                        nomeConsumidor(m.consumidorId)
                      )}
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <Fornecimento medidor={m} />
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <CadeiaRede medidor={m} nomes={nomes} />
                    </td>
                    <td className="py-3 text-slate-300">{m.dataInstalacao ? formatarData(m.dataInstalacao) : '—'}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                    <td className="py-3">
                      <div className="flex flex-wrap gap-1.5">
                        {!estaInstalado(m) && (
                          <button
                            type="button"
                            onClick={() => setInstalando(m)}
                            aria-label={`Instalar ${m.nome}`}
                            className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white"
                          >
                            <PlugZap className="h-3.5 w-3.5" />
                            Instalar
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => setConsumoDe(m)}
                          aria-label={`Consumo de ${m.nome}`}
                          className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white"
                        >
                          <Zap className="h-3.5 w-3.5" />
                          Consumo
                        </button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {carregando && (
            <p className="py-10 text-center text-sm text-slate-500">Carregando medidores...</p>
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

          {!carregando && !erro && medidoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum medidor encontrado.</p>
            </div>
          )}
        </div>
      </div>

      {cadastroAberto && (
        <MedidorForm aoFechar={() => setCadastroAberto(false)} aoSalvar={handleSalvar} />
      )}

      {consumoDe && <ConsumoMedidor medidor={consumoDe} consumidores={consumidores} nomes={nomes} aoFechar={() => setConsumoDe(null)} />}

      {instalando && (
        <InstalacaoForm
          medidor={instalando}
          consumidores={consumidores}
          transformadores={transformadores}
          alimentadores={alimentadores}
          subestacoes={subestacoes}
          aoFechar={() => setInstalando(null)}
          aoSalvar={handleSalvar}
        />
      )}
    </div>
  )
}

export default Smartmeters
