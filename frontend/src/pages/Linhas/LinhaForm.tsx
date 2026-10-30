import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { atualizarLinha, criarLinha } from '../../services/linhas'
import type { EstadoOperacional, LinhaTransmissao, Subestacao } from '../../types'
import { ESTADO_OPCOES } from '../../utils/constants'
import { normalizarLinha, paraRequisicao, validarLinha, type ErrosLinha, type LinhaFormValues } from './validacao'

type Props = {
  linha: LinhaTransmissao | null
  subestacoes: Subestacao[]
  aoFechar: () => void
  aoSalvar: (linha: LinhaTransmissao) => void
}

function valoresIniciais(linha: LinhaTransmissao | null): LinhaFormValues {
  if (!linha) {
    return {
      nome: '',
      latitude: '',
      longitude: '',
      tensaoOperacaoKv: '',
      subestacaoOrigemId: '',
      subestacaoDestinoId: '',
      extensaoKm: '',
      dataInstalacao: '',
      estadoOperacional: 'OPERACIONAL',
    }
  }

  return {
    nome: linha.nome,
    latitude: linha.latitude === null ? '' : String(linha.latitude),
    longitude: linha.longitude === null ? '' : String(linha.longitude),
    tensaoOperacaoKv: String(linha.tensaoOperacaoKv),
    subestacaoOrigemId: String(linha.subestacaoOrigemId),
    subestacaoDestinoId: String(linha.subestacaoDestinoId),
    extensaoKm: String(linha.extensaoKm),
    dataInstalacao: linha.dataInstalacao,
    estadoOperacional: linha.estadoOperacional,
  }
}

function LinhaForm({ linha, subestacoes, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState(() => valoresIniciais(linha))
  const [erros, setErros] = useState<ErrosLinha>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const opcoesSubestacao = subestacoes.map((s) => ({ valor: String(s.id), label: s.nome }))
  const placeholderSubestacao = subestacoes.length >= 2 ? 'Selecione' : 'Cadastre ao menos duas subestações'

  function alterar(campo: keyof LinhaFormValues) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = normalizarLinha(dados)
    const errosLocais = validarLinha(normalizados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      const requisicao = paraRequisicao(normalizados)
      aoSalvar(linha ? await atualizarLinha(linha.id, requisicao) : await criarLinha(requisicao))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal
      titulo={linha ? 'Editar linha' : 'Nova linha'}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="LT Norte-Central" valor={dados.nome} aoAlterar={alterar('nome')} erro={erros.nome} />
      <CampoSelect id="subestacaoOrigemId" label="Subestação de origem" placeholder={placeholderSubestacao} opcoes={opcoesSubestacao} valor={dados.subestacaoOrigemId} aoAlterar={alterar('subestacaoOrigemId')} erro={erros.subestacaoOrigemId} />
      <CampoSelect id="subestacaoDestinoId" label="Subestação de destino" placeholder={placeholderSubestacao} opcoes={opcoesSubestacao} valor={dados.subestacaoDestinoId} aoAlterar={alterar('subestacaoDestinoId')} erro={erros.subestacaoDestinoId} />
      <CampoTexto id="tensaoOperacaoKv" label="Tensão de operação (kV)" decimal placeholder="138" valor={dados.tensaoOperacaoKv} aoAlterar={alterar('tensaoOperacaoKv')} erro={erros.tensaoOperacaoKv} />
      <CampoTexto id="extensaoKm" label="Extensão (km)" decimal placeholder="56" valor={dados.extensaoKm} aoAlterar={alterar('extensaoKm')} erro={erros.extensaoKm} />
      <CampoTexto id="latitude" label="Latitude (opcional)" decimal placeholder="-25.4284001" valor={dados.latitude} aoAlterar={alterar('latitude')} erro={erros.latitude} />
      <CampoTexto id="longitude" label="Longitude (opcional)" decimal placeholder="-49.2733001" valor={dados.longitude} aoAlterar={alterar('longitude')} erro={erros.longitude} />
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

export default LinhaForm
