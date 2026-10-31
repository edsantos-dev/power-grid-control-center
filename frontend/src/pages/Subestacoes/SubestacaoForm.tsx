import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { atualizarSubestacao, criarSubestacao } from '../../services/subestacoes'
import type { EstadoOperacional, Subestacao, SubestacaoRequest } from '../../types'
import { ESTADO_OPCOES } from '../../utils/constants'
import { normalizarSubestacao, validarSubestacao, type ErrosSubestacao } from './validacao'

type Props = {
  subestacao: Subestacao | null
  aoFechar: () => void
  aoSalvar: (subestacao: Subestacao) => void
}

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

  function alterar(campo: keyof SubestacaoRequest) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

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
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal
      titulo={subestacao ? 'Editar subestação' : 'Nova subestação'}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="Subestação Central" valor={dados.nome} aoAlterar={alterar('nome')} erro={erros.nome} />
      <CampoTexto id="latitude" label="Latitude" decimal placeholder="-25.4284001" valor={dados.latitude} aoAlterar={alterar('latitude')} erro={erros.latitude} />
      <CampoTexto id="longitude" label="Longitude" decimal placeholder="-49.2733001" valor={dados.longitude} aoAlterar={alterar('longitude')} erro={erros.longitude} />
      <CampoTexto id="tensaoNominalKv" label="Tensão nominal (kV)" decimal placeholder="138" valor={dados.tensaoNominalKv} aoAlterar={alterar('tensaoNominalKv')} erro={erros.tensaoNominalKv} />
      <CampoTexto id="capacidadeMva" label="Capacidade (MVA)" decimal placeholder="100" valor={dados.capacidadeMva} aoAlterar={alterar('capacidadeMva')} erro={erros.capacidadeMva} />
      <CampoTexto id="dataInstalacao" label="Data de instalação" type="date" valor={dados.dataInstalacao} aoAlterar={alterar('dataInstalacao')} erro={erros.dataInstalacao} />
      <CampoSelect
        id="estadoOperacional"
        label="Status"
        opcoes={ESTADO_OPCOES}
        valor={dados.estadoOperacional}
        aoAlterar={(valor) => setDados((atuais) => ({ ...atuais, estadoOperacional: valor as EstadoOperacional }))}
        erro={erros.estadoOperacional}
      />
    </FormularioModal>
  )
}

export default SubestacaoForm
