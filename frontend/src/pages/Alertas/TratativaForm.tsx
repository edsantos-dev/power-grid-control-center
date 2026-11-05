import { useState, type FormEvent } from 'react'
import axios from 'axios'
import { FormularioModal } from '../../components/formulario'
import { reconhecerAlerta, resolverAlerta } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { Alerta } from '../../types'
import { CAMPO_OBRIGATORIO } from '../../utils/validacao'
import { ROTULO_ACAO, type AcaoTratativa } from './tratativa'

type Props = {
  id: number
  rotulo: string
  acao: AcaoTratativa
  aoFechar: () => void
  aoSalvar: (alerta: Alerta) => void
  // Chamado quando o servidor indica que o alerta mudou desde a última consulta
  aoDesatualizar: () => void
}

function TratativaForm({ id, rotulo, acao, aoFechar, aoSalvar, aoDesatualizar }: Props) {
  const [comentario, setComentario] = useState('')
  const [erroComentario, setErroComentario] = useState('')
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const texto = comentario.trim()
    const erroLocal = !texto ? CAMPO_OBRIGATORIO : texto.length > 1000 ? 'Informe até 1.000 caracteres.' : ''
    setErroComentario(erroLocal)

    if (erroLocal) {
      return
    }

    setEnviando(true)

    try {
      const enviar = acao === 'reconhecer' ? reconhecerAlerta : resolverAlerta
      aoSalvar(await enviar(id, texto))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErroComentario(campos.comentario ?? '')
      setEnviando(false)

      if (axios.isAxiosError(error) && error.response?.status === 409) {
        aoDesatualizar()
      }
    }
  }

  return (
    <FormularioModal
      titulo={`${ROTULO_ACAO[acao]} alerta`}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <p className="text-sm text-slate-300 sm:col-span-2">
        {rotulo}: {acao === 'reconhecer' ? 'Aberto → Reconhecido' : 'Reconhecido → Resolvido'}
      </p>

      <div className="sm:col-span-2">
        <label htmlFor="comentario-tratativa" className="mb-2 block text-sm font-medium text-slate-300">
          Comentário
        </label>
        <textarea
          id="comentario-tratativa"
          rows={3}
          value={comentario}
          onChange={(e) => setComentario(e.target.value)}
          className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
        />
        <p className={`mt-1 text-xs ${erroComentario ? 'text-red-400' : 'text-slate-500'}`}>
          {erroComentario || `${comentario.trim().length}/1000`}
        </p>
      </div>

      <p className="text-xs text-slate-500 sm:col-span-2">
        {acao === 'reconhecer'
          ? 'O alerta continua ativo até ser resolvido. Autor e instante são registrados pelo servidor.'
          : 'O alerta resolvido sai da lista de ativos e continua disponível no histórico.'}
      </p>
    </FormularioModal>
  )
}

export default TratativaForm
