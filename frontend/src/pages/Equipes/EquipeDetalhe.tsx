import { useEffect, useState, type FormEvent } from 'react'
import { UserMinus } from 'lucide-react'
import Modal from '../../components/Modal'
import { extrairErroApi } from '../../services/api'
import { alterarSituacaoEquipe, incluirIntegrante, listarIntegrantes, removerIntegrante } from '../../services/equipes'
import type { Equipe, Integrante, SituacaoEquipe } from '../../types'
import { PERFIL_LABEL } from '../../utils/constants'
import { formatarDataHora } from '../../utils/formatacao'
import { SITUACAO_EQUIPE, SITUACOES_EQUIPE } from './constantes'

type Props = {
  equipe: Equipe
  podeEditar: boolean
  aoFechar: () => void
  aoAtualizar: (equipe: Equipe) => void
}

function EquipeDetalhe({ equipe, podeEditar, aoFechar, aoAtualizar }: Props) {
  const [integrantes, setIntegrantes] = useState<Integrante[]>([])
  const [carregando, setCarregando] = useState(true)
  const [usuarioId, setUsuarioId] = useState('')
  const [erro, setErro] = useState('')
  const [versao, setVersao] = useState(0)

  useEffect(() => {
    let ativo = true
    setCarregando(true)

    listarIntegrantes(equipe.id)
      .then((lista) => ativo && setIntegrantes(lista))
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    return () => {
      ativo = false
    }
  }, [equipe.id, versao])

  async function executar(acao: () => Promise<void>) {
    setErro('')
    try {
      await acao()
      setVersao((atual) => atual + 1)
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    }
  }

  function handleIncluir(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!/^[1-9]\d*$/.test(usuarioId.trim())) {
      setErro('Informe o ID numérico do usuário.')
      return
    }
    executar(async () => {
      await incluirIntegrante(equipe.id, Number(usuarioId.trim()))
      setUsuarioId('')
    })
  }

  function handleSituacao(situacao: SituacaoEquipe) {
    executar(async () => aoAtualizar(await alterarSituacaoEquipe(equipe.id, situacao)))
  }

  const situacao = SITUACAO_EQUIPE[equipe.situacao]

  return (
    <Modal titulo={`Equipe ${equipe.nome}`} aoFechar={aoFechar}>
      <div className="space-y-1 text-sm">
        <p className="text-slate-300"><span className="text-slate-500">Responsabilidades:</span> {equipe.responsabilidades ?? 'Não informadas'}</p>
        <p className="text-slate-300"><span className="text-slate-500">Especialidades:</span> {equipe.especialidades ?? 'Não informadas'}</p>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <span className={`flex items-center gap-1.5 text-sm font-medium ${situacao.text}`}>
          <span className={`h-2 w-2 rounded-full ${situacao.dot}`} />
          {situacao.label}
        </span>

        {podeEditar && (
          <select
            aria-label="Alterar situação"
            value={equipe.situacao}
            onChange={(e) => handleSituacao(e.target.value as SituacaoEquipe)}
            className="rounded-lg border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs text-white"
          >
            {SITUACOES_EQUIPE.map((s) => (
              <option key={s} value={s}>{SITUACAO_EQUIPE[s].label}</option>
            ))}
          </select>
        )}
      </div>
      <p className="mt-1 text-xs text-slate-500">Disponível e em atendimento exigem ao menos um integrante ativo.</p>

      <h4 className="mt-6 text-sm font-medium text-slate-200">Integrantes ativos</h4>
      {carregando && <p className="mt-2 text-sm text-slate-500">Carregando integrantes...</p>}
      {!carregando && integrantes.length === 0 && <p className="mt-2 text-sm text-slate-500">Nenhum integrante ativo.</p>}

      <ul className="mt-2 space-y-2" aria-label="Integrantes ativos">
        {integrantes.map((i) => (
          <li key={i.id} className="flex items-center justify-between gap-3 rounded-lg border border-slate-800 px-3 py-2 text-sm">
            <div>
              <p className="text-slate-200">{i.nome} <span className="text-xs text-slate-500">#{i.usuarioId}</span></p>
              <p className="text-xs text-slate-500">{PERFIL_LABEL[i.perfil]} · desde {formatarDataHora(i.inicioVigencia)}</p>
            </div>
            {podeEditar && (
              <button
                type="button"
                onClick={() => executar(() => removerIntegrante(equipe.id, i.usuarioId))}
                aria-label={`Remover ${i.nome}`}
                className="rounded-lg p-1.5 text-slate-400 transition hover:bg-slate-800 hover:text-red-300"
              >
                <UserMinus className="h-4 w-4" />
              </button>
            )}
          </li>
        ))}
      </ul>

      {podeEditar && (
        <form onSubmit={handleIncluir} noValidate className="mt-4 flex flex-wrap items-end gap-2">
          <div>
            <label htmlFor="integrante-id" className="mb-1 block text-xs text-slate-400">Incluir usuário pelo ID</label>
            <input
              id="integrante-id"
              inputMode="numeric"
              value={usuarioId}
              onChange={(e) => setUsuarioId(e.target.value)}
              className="w-32 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white outline-none focus:border-blue-500"
            />
          </div>
          <button type="submit" className="rounded-lg border border-slate-700 px-3 py-2 text-sm text-slate-300 transition hover:bg-slate-800">
            Incluir
          </button>
        </form>
      )}

      {erro && <p className="mt-3 text-sm text-red-400">{erro}</p>}
      <p className="mt-4 text-xs text-slate-500">
        Cada integrante é uma conta de usuário. Remover encerra a vigência, sem apagar o histórico.
      </p>
    </Modal>
  )
}

export default EquipeDetalhe
