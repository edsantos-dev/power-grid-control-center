import { useState, type FormEvent } from 'react'
import { X } from 'lucide-react'
import { extrairErroApi } from '../../services/api'
import { atualizarSubestacao, criarSubestacao } from '../../services/subestacoes'
import type { EstadoOperacional, Subestacao, SubestacaoRequest } from '../../types'
import { normalizarSubestacao, validarSubestacao, type ErrosSubestacao } from './validacao'

type Props = {
  subestacao: Subestacao | null
  aoFechar: () => void
  aoSalvar: (subestacao: Subestacao) => void
}

type CampoTexto = Exclude<keyof SubestacaoRequest, 'estadoOperacional'>

const campos: { nome: CampoTexto; label: string; type?: string; placeholder?: string }[] = [
  { nome: 'nome', label: 'Nome', placeholder: 'Subestação Central' },
  { nome: 'latitude', label: 'Latitude', placeholder: '-25.4284001' },
  { nome: 'longitude', label: 'Longitude', placeholder: '-49.2733001' },
  { nome: 'tensaoNominalKv', label: 'Tensão nominal (kV)', placeholder: '138' },
  { nome: 'capacidadeMva', label: 'Capacidade (MVA)', placeholder: '100' },
  { nome: 'dataInstalacao', label: 'Data de instalação', type: 'date' },
]

const estados: { valor: EstadoOperacional; label: string }[] = [
  { valor: 'OPERACIONAL', label: 'Operacional' },
  { valor: 'FALHA', label: 'Falha' },
  { valor: 'MANUTENCAO', label: 'Manutenção' },
]

function valoresIniciais(subestacao: Subestacao | null): SubestacaoRequest {
  if (!subestacao) {
    return {
      nome: '',
      latitude: '',
      longitude: '',
      tensaoNominalKv: '',
      capacidadeMva: '',
      dataInstalacao: '',
      estadoOperacional: 'OPERACIONAL',
    }
  }

  return {
    nome: subestacao.nome,
    latitude: String(subestacao.latitude),
    longitude: String(subestacao.longitude),
    tensaoNominalKv: String(subestacao.tensaoNominalKv),
    capacidadeMva: String(subestacao.capacidadeMva),
    dataInstalacao: subestacao.dataInstalacao,
    estadoOperacional: subestacao.estadoOperacional,
  }
}

function SubestacaoForm({ subestacao, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState(() => valoresIniciais(subestacao))
  const [erros, setErros] = useState<ErrosSubestacao>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const titulo = subestacao ? 'Editar subestação' : 'Nova subestação'

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = normalizarSubestacao(dados)
    const errosLocais = validarSubestacao(normalizados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(
        subestacao
          ? await atualizarSubestacao(subestacao.id, normalizados)
          : await criarSubestacao(normalizados)
      )
    } catch (error) {
      const { mensagem, campos: errosDoServidor } = extrairErroApi(error)
      setErro(mensagem)
      setErros(errosDoServidor)
      setEnviando(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/80 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className="w-full max-w-2xl rounded-xl border border-slate-800 bg-slate-900 p-6"
      >
        <div className="mb-6 flex items-center justify-between">
          <h3 className="text-xl font-bold text-white">{titulo}</h3>

          <button
            type="button"
            onClick={aoFechar}
            aria-label="Fechar"
            className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-800 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            {campos.map((campo) => (
              <div key={campo.nome} className={campo.nome === 'nome' ? 'sm:col-span-2' : undefined}>
                <label htmlFor={campo.nome} className="mb-2 block text-sm font-medium text-slate-300">
                  {campo.label}
                </label>

                <input
                  id={campo.nome}
                  type={campo.type ?? 'text'}
                  inputMode={campo.nome === 'nome' || campo.type ? undefined : 'decimal'}
                  placeholder={campo.placeholder}
                  value={dados[campo.nome]}
                  onChange={(e) => setDados({ ...dados, [campo.nome]: e.target.value })}
                  className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none placeholder:text-slate-500 focus:border-blue-500"
                />

                {erros[campo.nome] && (
                  <p className="mt-1 text-xs text-red-400">{erros[campo.nome]}</p>
                )}
              </div>
            ))}

            <div>
              <label htmlFor="estadoOperacional" className="mb-2 block text-sm font-medium text-slate-300">
                Status
              </label>

              <select
                id="estadoOperacional"
                value={dados.estadoOperacional}
                onChange={(e) => setDados({ ...dados, estadoOperacional: e.target.value as EstadoOperacional })}
                className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
              >
                {estados.map((estado) => (
                  <option key={estado.valor} value={estado.valor}>{estado.label}</option>
                ))}
              </select>

              {erros.estadoOperacional && (
                <p className="mt-1 text-xs text-red-400">{erros.estadoOperacional}</p>
              )}
            </div>
          </div>

          {erro && (
            <p className="mt-4 text-sm text-red-400">{erro}</p>
          )}

          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={aoFechar}
              className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800 hover:text-white"
            >
              Cancelar
            </button>

            <button
              type="submit"
              disabled={enviando}
              className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {enviando ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default SubestacaoForm
