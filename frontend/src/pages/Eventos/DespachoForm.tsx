import { useState, type FormEvent } from 'react'
import axios from 'axios'
import { CampoSelect, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { designarEquipe } from '../../services/eventos'
import type { Equipe, EventoOperacional } from '../../types'
import { CAMPO_OBRIGATORIO, removerCamposValidos } from '../../utils/validacao'
import { SITUACAO_EQUIPE } from '../Equipes/constantes'
import { nomeEquipe, podeReceberDesignacao } from './designacao'

type Props = {
  evento: EventoOperacional
  equipes: Equipe[]
  aoFechar: () => void
  aoSalvar: (evento: EventoOperacional) => void
  // Chamado quando o servidor indica que o evento ou a equipe mudaram desde a última consulta
  aoDesatualizar: () => void
}

type Erros = { equipe?: string; comentario?: string }

const RETIRAR = 'retirar'

function DespachoForm({ evento, equipes, aoFechar, aoSalvar, aoDesatualizar }: Props) {
  const [equipe, setEquipe] = useState('')
  const [comentario, setComentario] = useState('')
  const [erros, setErros] = useState<Erros>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const atual = evento.equipeDesignadaId
  // A equipe atual já está designada; as demais só aparecem quando podem receber a designação
  const opcoes = [
    ...equipes
      .filter((e) => e.id !== atual && podeReceberDesignacao(e))
      .map((e) => ({ valor: String(e.id), label: `${e.nome} (${SITUACAO_EQUIPE[e.situacao].label})` })),
    ...(atual !== null ? [{ valor: RETIRAR, label: 'Retirar a equipe atual' }] : []),
  ]

  function validar() {
    const texto = comentario.trim()

    return removerCamposValidos<Erros>({
      equipe: !equipe ? CAMPO_OBRIGATORIO : undefined,
      comentario: !texto ? CAMPO_OBRIGATORIO : texto.length > 1000 ? 'Informe até 1.000 caracteres.' : undefined,
    })
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const errosLocais = validar()
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(
        await designarEquipe(evento.id, {
          equipeId: equipe === RETIRAR ? null : Number(equipe),
          versaoDesignacaoEsperada: evento.versaoDesignacao,
          comentario: comentario.trim(),
        })
      )
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros({ comentario: campos.comentario })
      setEnviando(false)

      if (axios.isAxiosError(error) && error.response?.status === 409) {
        aoDesatualizar()
      }
    }
  }

  return (
    <FormularioModal
      titulo={atual === null ? 'Despachar equipe' : 'Alterar equipe designada'}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <p className="text-sm text-slate-300 sm:col-span-2">
        {evento.descricao}
        <span className="block text-xs text-slate-500">Equipe atual: {nomeEquipe(atual, equipes)}</span>
      </p>

      <CampoSelect
        id="equipe"
        label="Equipe"
        placeholder="Selecione"
        opcoes={opcoes}
        valor={equipe}
        aoAlterar={setEquipe}
        erro={erros.equipe}
        className="sm:col-span-2"
      />

      {opcoes.length === 0 && (
        <p className="text-xs text-yellow-400 sm:col-span-2">Nenhuma equipe disponível ou em atendimento para receber a designação.</p>
      )}

      <div className="sm:col-span-2">
        <label htmlFor="comentario-despacho" className="mb-2 block text-sm font-medium text-slate-300">
          Comentário
        </label>
        <textarea
          id="comentario-despacho"
          rows={3}
          value={comentario}
          onChange={(e) => setComentario(e.target.value)}
          className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
        />
        <p className={`mt-1 text-xs ${erros.comentario ? 'text-red-400' : 'text-slate-500'}`}>
          {erros.comentario ?? `${comentario.trim().length}/1000`}
        </p>
      </div>

      <p className="text-xs text-slate-500 sm:col-span-2">
        A designação não altera a situação da equipe nem o responsável pelo evento. Autor e instante são registrados pelo servidor.
      </p>
    </FormularioModal>
  )
}

export default DespachoForm
