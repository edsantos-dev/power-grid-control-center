import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { criarConsumidor } from '../../services/consumidores'
import type { Consumidor } from '../../types'
import { paraRequisicao, validarConsumidor, type ConsumidorFormValues, type ErrosConsumidor } from './validacao'

type Props = {
  aoFechar: () => void
  aoSalvar: (consumidor: Consumidor) => void
}

const opcoesTipo = [
  { valor: 'PF', label: 'Pessoa física' },
  { valor: 'PJ', label: 'Pessoa jurídica' },
]

function ConsumidorForm({ aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState<ConsumidorFormValues>({
    nomeRazaoSocial: '',
    tipoPessoa: '',
    documento: '',
    enderecoCorrespondencia: '',
  })
  const [erros, setErros] = useState<ErrosConsumidor>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  function alterar(campo: keyof ConsumidorFormValues) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const errosLocais = validarConsumidor(dados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(await criarConsumidor(paraRequisicao(dados)))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  const rotuloNome = dados.tipoPessoa === 'PJ' ? 'Razão social' : dados.tipoPessoa === 'PF' ? 'Nome' : 'Nome ou razão social'
  const rotuloDocumento = dados.tipoPessoa === 'PJ' ? 'CNPJ' : dados.tipoPessoa === 'PF' ? 'CPF' : 'Documento'

  return (
    <FormularioModal titulo="Novo consumidor" erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoSelect
        id="tipoPessoa"
        label="Tipo de pessoa"
        placeholder="Selecione"
        opcoes={opcoesTipo}
        valor={dados.tipoPessoa}
        aoAlterar={alterar('tipoPessoa')}
        erro={erros.tipoPessoa}
      />
      <CampoTexto
        id="documento"
        label={rotuloDocumento}
        placeholder={dados.tipoPessoa === 'PJ' ? '00.000.000/0000-00' : '000.000.000-00'}
        valor={dados.documento}
        aoAlterar={alterar('documento')}
        erro={erros.documento}
      />
      <CampoTexto id="nomeRazaoSocial" label={rotuloNome} className="sm:col-span-2" valor={dados.nomeRazaoSocial} aoAlterar={alterar('nomeRazaoSocial')} erro={erros.nomeRazaoSocial} />
      <CampoTexto
        id="enderecoCorrespondencia"
        label="Endereço de correspondência (opcional)"
        className="sm:col-span-2"
        valor={dados.enderecoCorrespondencia}
        aoAlterar={alterar('enderecoCorrespondencia')}
        erro={erros.enderecoCorrespondencia}
      />
    </FormularioModal>
  )
}

export default ConsumidorForm
