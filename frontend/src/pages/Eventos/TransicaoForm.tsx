import { useState, type FormEvent } from 'react'
import axios from 'axios'
import { CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { buscarEvento, transicionarEvento } from '../../services/eventos'
import type { EventoOperacional } from '../../types'
import { dataHoraLocalAtual, formatarDataHora, instanteDeDataHoraLocal } from '../../utils/formatacao'
import { CAMPO_OBRIGATORIO, removerCamposValidos } from '../../utils/validacao'
import { SITUACAO_EVENTO_STYLES } from './constantes'
import { exigeTerminoEfetivo, type Transicao } from './transicoes'

type Props = {
  evento: EventoOperacional
  transicao: Transicao
  aoFechar: () => void
  aoSalvar: (evento: EventoOperacional) => void
  // Chamado quando o servidor indica que o evento mudou desde a última consulta
  aoDesatualizar: () => void
}

type Erros = { comentario?: string; terminoEfetivo?: string }

function TransicaoForm({ evento, transicao, aoFechar, aoSalvar, aoDesatualizar }: Props) {
  const [comentario, setComentario] = useState('')
  const [terminoEfetivo, setTerminoEfetivo] = useState('')
  const [erros, setErros] = useState<Erros>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const pedeTermino = exigeTerminoEfetivo(evento, transicao.destino)

  function validar() {
    const texto = comentario.trim()
    const inicioLocal = evento.inicioEfetivo ? dataHoraLocalAtual(new Date(evento.inicioEfetivo)) : ''

    return removerCamposValidos<Erros>({
      comentario: !texto ? CAMPO_OBRIGATORIO : texto.length > 1000 ? 'Informe até 1.000 caracteres.' : undefined,
      terminoEfetivo: !pedeTermino
        ? undefined
        : !terminoEfetivo
          ? CAMPO_OBRIGATORIO
          : terminoEfetivo > dataHoraLocalAtual()
            ? 'O término não pode estar no futuro.'
            : terminoEfetivo < inicioLocal
              ? 'O término não pode ser anterior ao início da interrupção.'
              : undefined,
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
      await transicionarEvento(evento.id, {
        situacaoEsperada: evento.situacao,
        situacaoDestino: transicao.destino,
        comentario: comentario.trim(),
        terminoEfetivo: pedeTermino ? instanteDeDataHoraLocal(terminoEfetivo) : null,
      })
      // A resposta é o registro do histórico; o evento atualizado vem de uma nova consulta
      aoSalvar(await buscarEvento(evento.id))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)

      if (axios.isAxiosError(error) && error.response?.status === 409) {
        aoDesatualizar()
      }
    }
  }

  const destino = SITUACAO_EVENTO_STYLES[transicao.destino].label

  return (
    <FormularioModal
      titulo={`${transicao.rotulo} evento`}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <p className="text-sm text-slate-300 sm:col-span-2">
        {evento.descricao}: {SITUACAO_EVENTO_STYLES[evento.situacao].label} → {destino}
        {evento.inicioEfetivo && (
          <span className="block text-xs text-slate-500">Início efetivo {formatarDataHora(evento.inicioEfetivo)}</span>
        )}
      </p>

      <div className="sm:col-span-2">
        <label htmlFor="comentario" className="mb-2 block text-sm font-medium text-slate-300">
          Comentário
        </label>
        <textarea
          id="comentario"
          rows={3}
          value={comentario}
          onChange={(e) => setComentario(e.target.value)}
          className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
        />
        <p className={`mt-1 text-xs ${erros.comentario ? 'text-red-400' : 'text-slate-500'}`}>
          {erros.comentario ?? `${comentario.trim().length}/1000`}
        </p>
      </div>

      {pedeTermino && (
        <CampoTexto
          id="terminoEfetivo"
          label="Término da interrupção"
          type="datetime-local"
          valor={terminoEfetivo}
          aoAlterar={setTerminoEfetivo}
          erro={erros.terminoEfetivo}
        />
      )}

      <p className="text-xs text-slate-500 sm:col-span-2">
        {transicao.destino === 'EM_ANDAMENTO'
          ? 'O início efetivo é registrado pelo servidor no momento da transição.'
          : pedeTermino
            ? 'Informe quando o fornecimento foi restabelecido.'
            : 'O término efetivo é registrado pelo servidor no momento da conclusão.'}
      </p>
    </FormularioModal>
  )
}

export default TransicaoForm
