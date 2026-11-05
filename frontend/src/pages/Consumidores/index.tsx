import { useEffect, useState } from 'react'
import { Search, Plus, Router } from 'lucide-react'
import { extrairErroApi } from '../../services/api'
import { listarConsumidores } from '../../services/consumidores'
import type { Consumidor, TipoPessoa } from '../../types'
import ConsumidorForm from './ConsumidorForm'
import MedidoresDoConsumidor from './MedidoresDoConsumidor'

const tipoLabel: Record<TipoPessoa, string> = {
  PF: 'Pessoa física',
  PJ: 'Pessoa jurídica',
}

function Consumidores() {
  const [consumidores, setConsumidores] = useState<Consumidor[]>([])
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [filtroTipo, setFiltroTipo] = useState<TipoPessoa | 'todos'>('todos')
  const [busca, setBusca] = useState('')
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [consultando, setConsultando] = useState<Consumidor | null>(null)

  async function carregar() {
    setCarregando(true)
    setErro('')

    try {
      setConsumidores(await listarConsumidores())
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setCarregando(false)
    }
  }

  useEffect(() => {
    carregar()
  }, [])

  function handleSalvar(salvo: Consumidor) {
    setConsumidores((atuais) => [...atuais, salvo])
    setFormularioAberto(false)
  }

  const consumidoresFiltrados = consumidores
    .filter((c) => {
      const termo = busca.trim().toLowerCase()
      const passaTipo = filtroTipo === 'todos' || c.tipoPessoa === filtroTipo
      const passaBusca = c.nomeRazaoSocial.toLowerCase().includes(termo)
      return passaTipo && passaBusca
    })
    .sort((a, b) => a.id - b.id)

  const totalPf = consumidores.filter((c) => c.tipoPessoa === 'PF').length
  const totalPj = consumidores.filter((c) => c.tipoPessoa === 'PJ').length

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Consumidores
          </h2>

          <p className="mt-2 text-slate-400">
            Cadastro dos titulares atendidos pela rede de distribuição.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setFormularioAberto(true)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Novo consumidor
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Titulares</p>
          <p className="mt-2 text-3xl font-bold text-white">{consumidores.length}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Pessoa física</p>
          <p className="mt-2 text-3xl font-bold text-blue-400">{totalPf}</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Pessoa jurídica</p>
          <p className="mt-2 text-3xl font-bold text-blue-400">{totalPj}</p>
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
              placeholder="Buscar por nome..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'PF', 'PJ'] as const).map((tipo) => (
              <button
                key={tipo}
                onClick={() => setFiltroTipo(tipo)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroTipo === tipo
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {tipo === 'todos' ? 'Todos' : tipoLabel[tipo]}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Consumidor</th>
                <th className="pb-3 font-normal">Tipo</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {consumidoresFiltrados.map((c) => (
                <tr key={c.id} className="border-b border-slate-800/60 last:border-0">
                  <td className="py-3 pr-4">
                    <p className="text-slate-200">{c.nomeRazaoSocial}</p>
                    <p className="mt-1 text-xs text-slate-500">#{c.id}</p>
                  </td>
                  <td className="py-3 pr-4 text-slate-300">{tipoLabel[c.tipoPessoa]}</td>
                  <td className="py-3">
                    <button
                      type="button"
                      onClick={() => setConsultando(c)}
                      aria-label={`Ver medidores de ${c.nomeRazaoSocial}`}
                      className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1.5 text-xs text-slate-300 transition hover:bg-slate-800 hover:text-white"
                    >
                      <Router className="h-3.5 w-3.5" />
                      Medidores
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {carregando && (
            <p className="py-10 text-center text-sm text-slate-500">Carregando consumidores...</p>
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

          {!carregando && !erro && consumidoresFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum consumidor encontrado.</p>
            </div>
          )}
        </div>
      </div>

      {formularioAberto && (
        <ConsumidorForm aoFechar={() => setFormularioAberto(false)} aoSalvar={handleSalvar} />
      )}

      {consultando && (
        <MedidoresDoConsumidor consumidor={consultando} aoFechar={() => setConsultando(null)} />
      )}
    </div>
  )
}

export default Consumidores
