import { useEffect, useState } from 'react'
import { Search, Pencil, Plus, Users, ChevronLeft, ChevronRight } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import { listarEquipes } from '../../services/equipes'
import type { Equipe, PageResponse, SituacaoEquipe } from '../../types'
import { SITUACAO_EQUIPE, SITUACOES_EQUIPE } from './constantes'
import EquipeDetalhe from './EquipeDetalhe'
import EquipeForm from './EquipeForm'

function Equipes() {
  const { usuario } = useAuth()
  const [resultado, setResultado] = useState<PageResponse<Equipe> | null>(null)
  const [pagina, setPagina] = useState(0)
  const [nome, setNome] = useState('')
  const [situacao, setSituacao] = useState<SituacaoEquipe | ''>('')
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [tentativa, setTentativa] = useState(0)
  const [formulario, setFormulario] = useState<{ equipe: Equipe | null } | null>(null)
  const [detalhe, setDetalhe] = useState<Equipe | null>(null)

  // O backend autoriza o gerente e o técnico de manutenção a alterar equipes
  const podeEditar = usuario?.perfil === 'GERENTE_OPERACOES' || usuario?.perfil === 'TECNICO_MANUTENCAO'

  useEffect(() => {
    let ativo = true
    const espera = setTimeout(() => {
      setCarregando(true)
      listarEquipes(pagina, { nome: nome.trim() || undefined, situacao: situacao || undefined })
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
  }, [pagina, nome, situacao, tentativa])

  function recarregar() {
    setTentativa((atual) => atual + 1)
  }

  const equipes = resultado?.content ?? []

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Equipes
          </h2>

          <p className="mt-2 text-slate-400">
            Equipes de manutenção, responsabilidades e integrantes vigentes.
          </p>
        </div>

        {podeEditar && (
          <button
            type="button"
            onClick={() => setFormulario({ equipe: null })}
            className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
          >
            <Plus className="h-4 w-4" />
            Nova equipe
          </button>
        )}
      </div>

      <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={nome}
              onChange={(e) => {
                setNome(e.target.value)
                setPagina(0)
              }}
              placeholder="Buscar equipe pelo nome..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['', ...SITUACOES_EQUIPE] as const).map((s) => (
              <button
                key={s || 'todas'}
                onClick={() => {
                  setSituacao(s)
                  setPagina(0)
                }}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  situacao === s ? 'border-slate-600 bg-slate-800 text-white' : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {s ? SITUACAO_EQUIPE[s].label : 'Todas'}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Equipe</th>
                <th className="pb-3 font-normal">Responsabilidades</th>
                <th className="pb-3 font-normal">Especialidades</th>
                <th className="pb-3 font-normal">Situação</th>
                <th className="pb-3 font-normal">Ações</th>
              </tr>
            </thead>
            <tbody>
              {equipes.map((e) => {
                const estilo = SITUACAO_EQUIPE[e.situacao]

                return (
                  <tr key={e.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{e.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">#{e.id}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">{e.responsabilidades ?? <span className="text-slate-500">Não informadas</span>}</td>
                    <td className="py-3 pr-4 text-slate-400">{e.especialidades ?? <span className="text-slate-500">Não informadas</span>}</td>
                    <td className="py-3 pr-4">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${estilo.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${estilo.dot}`} />
                        {estilo.label}
                      </span>
                    </td>
                    <td className="py-3">
                      <div className="flex gap-1.5">
                        <button
                          type="button"
                          onClick={() => setDetalhe(e)}
                          aria-label={`Integrantes de ${e.nome}`}
                          className="flex items-center gap-1.5 rounded-lg border border-slate-700 px-2.5 py-1 text-xs text-slate-300 transition hover:bg-slate-800"
                        >
                          <Users className="h-3.5 w-3.5" />
                          Integrantes
                        </button>
                        {podeEditar && (
                          <button
                            type="button"
                            onClick={() => setFormulario({ equipe: e })}
                            aria-label={`Editar ${e.nome}`}
                            className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-white"
                          >
                            <Pencil className="h-4 w-4" />
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {carregando && !resultado && <p className="py-10 text-center text-sm text-slate-500">Carregando equipes...</p>}

          {!carregando && erro && (
            <div className="flex flex-col items-center gap-3 py-10">
              <p className="text-sm text-red-400">{erro}</p>
              <button type="button" onClick={recarregar} className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800">
                Tentar novamente
              </button>
            </div>
          )}

          {!carregando && !erro && equipes.length === 0 && (
            <p className="py-10 text-center text-sm text-slate-500">Nenhuma equipe encontrada.</p>
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
        <EquipeForm
          equipe={formulario.equipe}
          aoFechar={() => setFormulario(null)}
          aoSalvar={() => {
            setFormulario(null)
            recarregar()
          }}
        />
      )}

      {detalhe && (
        <EquipeDetalhe
          equipe={detalhe}
          podeEditar={podeEditar}
          aoFechar={() => setDetalhe(null)}
          aoAtualizar={(atualizada) => {
            setDetalhe(atualizada)
            recarregar()
          }}
        />
      )}
    </div>
  )
}

export default Equipes
