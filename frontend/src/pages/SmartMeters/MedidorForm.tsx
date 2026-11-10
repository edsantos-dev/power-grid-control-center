import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { criarMedidor } from '../../services/medidores'
import type { EstadoOperacional, Medidor, MedidorRequest } from '../../types'
import { opcoesEstadoCadastro } from '../../utils/constants'
import { normalizarMedidor, validarMedidor, type ErrosMedidor } from './validacao'

type Props = {
  aoFechar: () => void
  aoSalvar: (medidor: Medidor) => void
}

function MedidorForm({ aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState<MedidorRequest>({ nome: '', numeroSerie: '', estadoOperacional: 'OPERACIONAL' })
  const [erros, setErros] = useState<ErrosMedidor>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  function alterar(campo: keyof MedidorRequest) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = normalizarMedidor(dados)
    const errosLocais = validarMedidor(normalizados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(await criarMedidor(normalizados))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal titulo="Novo medidor" erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="Medidor Rua das Palmeiras" valor={dados.nome} aoAlterar={alterar('nome')} erro={erros.nome} />
      <CampoTexto id="numeroSerie" label="Número de série" placeholder="SN-000123" valor={dados.numeroSerie} aoAlterar={alterar('numeroSerie')} erro={erros.numeroSerie} />
      <CampoSelect
        id="estadoOperacional"
        label="Status"
        opcoes={opcoesEstadoCadastro()}
        valor={dados.estadoOperacional}
        aoAlterar={(valor) => setDados((atuais) => ({ ...atuais, estadoOperacional: valor as EstadoOperacional }))}
        erro={erros.estadoOperacional}
      />
      <p className="text-xs text-slate-500 sm:col-span-2">
        O medidor é cadastrado em estoque, sem titular nem transformador. A instalação é feita depois, pela lista.
      </p>
    </FormularioModal>
  )
}

export default MedidorForm
