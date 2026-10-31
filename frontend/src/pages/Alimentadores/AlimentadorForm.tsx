import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { atualizarAlimentador, criarAlimentador } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import type { Alimentador, EstadoOperacional, Subestacao } from '../../types'
import { ESTADO_OPCOES } from '../../utils/constants'
import {
  normalizarAlimentador,
  paraRequisicao,
  validarAlimentador,
  type AlimentadorFormValues,
  type ErrosAlimentador,
} from './validacao'

type Props = {
  alimentador: Alimentador | null
  subestacoes: Subestacao[]
  aoFechar: () => void
  aoSalvar: (alimentador: Alimentador) => void
}

function valoresIniciais(alimentador: Alimentador | null): AlimentadorFormValues {
  if (!alimentador) {
    return {
      nome: '',
      latitude: '',
      longitude: '',
      tensaoNominalKv: '',
      subestacaoId: '',
      dataInstalacao: '',
      estadoOperacional: 'OPERACIONAL',
    }
  }

  return {
    nome: alimentador.nome,
    latitude: String(alimentador.latitude),
    longitude: String(alimentador.longitude),
    tensaoNominalKv: String(alimentador.tensaoNominalKv),
    subestacaoId: String(alimentador.subestacaoId),
    dataInstalacao: alimentador.dataInstalacao,
    estadoOperacional: alimentador.estadoOperacional,
  }
}

function AlimentadorForm({ alimentador, subestacoes, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState(() => valoresIniciais(alimentador))
  const [erros, setErros] = useState<ErrosAlimentador>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const opcoesSubestacao = subestacoes.map((s) => ({ valor: String(s.id), label: s.nome }))

  function alterar(campo: keyof AlimentadorFormValues) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = normalizarAlimentador(dados)
    const errosLocais = validarAlimentador(normalizados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      const requisicao = paraRequisicao(normalizados)
      aoSalvar(
        alimentador
          ? await atualizarAlimentador(alimentador.id, requisicao)
          : await criarAlimentador(requisicao)
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
      titulo={alimentador ? 'Editar alimentador' : 'Novo alimentador'}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="Alimentador Central" valor={dados.nome} aoAlterar={alterar('nome')} erro={erros.nome} />
      <CampoSelect
        id="subestacaoId"
        label="Subestação"
        className="sm:col-span-2"
        placeholder={subestacoes.length ? 'Selecione' : 'Cadastre uma subestação antes'}
        opcoes={opcoesSubestacao}
        valor={dados.subestacaoId}
        aoAlterar={alterar('subestacaoId')}
        erro={erros.subestacaoId}
      />
      <CampoTexto id="latitude" label="Latitude" decimal placeholder="-25.4284001" valor={dados.latitude} aoAlterar={alterar('latitude')} erro={erros.latitude} />
      <CampoTexto id="longitude" label="Longitude" decimal placeholder="-49.2733001" valor={dados.longitude} aoAlterar={alterar('longitude')} erro={erros.longitude} />
      <CampoTexto id="tensaoNominalKv" label="Tensão nominal (kV)" decimal placeholder="13.8" valor={dados.tensaoNominalKv} aoAlterar={alterar('tensaoNominalKv')} erro={erros.tensaoNominalKv} />
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

export default AlimentadorForm
