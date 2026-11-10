import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { atualizarUsina, criarUsina } from '../../services/usinas'
import type { EstadoOperacional, Subestacao, TipoGeracao, Usina } from '../../types'
import { opcoesEstadoCadastro } from '../../utils/constants'
import {
  CAMPO_OBRIGATORIO,
  normalizarDecimal,
  removerCamposValidos,
  validarCoordenada,
  validarNome,
  validarObrigatorio,
  validarPositivo,
} from '../../utils/validacao'
import { TIPO_GERACAO_LABEL, TIPOS_GERACAO } from './constantes'

type Props = {
  usina: Usina | null
  subestacoes: Subestacao[]
  aoFechar: () => void
  aoSalvar: (usina: Usina) => void
}

type Valores = {
  nome: string
  tipoGeracao: TipoGeracao | ''
  capacidadeMaximaMw: string
  subestacaoId: string
  latitude: string
  longitude: string
  dataInstalacao: string
  estadoOperacional: EstadoOperacional
}

type Erros = Partial<Record<keyof Valores, string>>

function iniciais(usina: Usina | null): Valores {
  return {
    nome: usina?.nome ?? '',
    tipoGeracao: usina?.tipoGeracao ?? '',
    capacidadeMaximaMw: usina ? String(usina.capacidadeMaximaMw) : '',
    subestacaoId: usina ? String(usina.subestacaoId) : '',
    latitude: usina ? String(usina.latitude) : '',
    longitude: usina ? String(usina.longitude) : '',
    dataInstalacao: usina?.dataInstalacao ?? '',
    estadoOperacional: usina?.estadoOperacional ?? 'OPERACIONAL',
  }
}

function UsinaForm({ usina, subestacoes, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState(() => iniciais(usina))
  const [erros, setErros] = useState<Erros>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  function alterar(campo: keyof Valores) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = {
      ...dados,
      nome: dados.nome.trim(),
      capacidadeMaximaMw: normalizarDecimal(dados.capacidadeMaximaMw),
      latitude: normalizarDecimal(dados.latitude),
      longitude: normalizarDecimal(dados.longitude),
    }
    const errosLocais = removerCamposValidos<Erros>({
      nome: validarNome(normalizados.nome),
      tipoGeracao: normalizados.tipoGeracao ? undefined : CAMPO_OBRIGATORIO,
      capacidadeMaximaMw: validarPositivo(normalizados.capacidadeMaximaMw),
      subestacaoId: validarObrigatorio(normalizados.subestacaoId),
      latitude: validarCoordenada(normalizados.latitude, 90),
      longitude: validarCoordenada(normalizados.longitude, 180),
    })
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      const requisicao = {
        ...normalizados,
        tipoGeracao: normalizados.tipoGeracao as TipoGeracao,
        subestacaoId: Number(normalizados.subestacaoId),
        dataInstalacao: normalizados.dataInstalacao || null,
      }
      aoSalvar(usina ? await atualizarUsina(usina.id, requisicao) : await criarUsina(requisicao))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal titulo={usina ? 'Editar usina' : 'Nova usina'} erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="UHE Rio Verde" valor={dados.nome} aoAlterar={alterar('nome')} erro={erros.nome} />
      <CampoSelect id="tipoGeracao" label="Tipo de geração" placeholder="Selecione" opcoes={TIPOS_GERACAO.map((t) => ({ valor: t, label: TIPO_GERACAO_LABEL[t] }))} valor={dados.tipoGeracao} aoAlterar={alterar('tipoGeracao')} erro={erros.tipoGeracao} />
      <CampoTexto id="capacidadeMaximaMw" label="Capacidade máxima (MW)" decimal placeholder="120" valor={dados.capacidadeMaximaMw} aoAlterar={alterar('capacidadeMaximaMw')} erro={erros.capacidadeMaximaMw} />
      <CampoSelect
        id="subestacaoId"
        label="Subestação de conexão"
        className="sm:col-span-2"
        placeholder={subestacoes.length ? 'Selecione' : 'Cadastre uma subestação antes'}
        opcoes={subestacoes.map((s) => ({ valor: String(s.id), label: s.nome }))}
        valor={dados.subestacaoId}
        aoAlterar={alterar('subestacaoId')}
        erro={erros.subestacaoId}
      />
      <CampoTexto id="latitude" label="Latitude" decimal placeholder="-25.4284001" valor={dados.latitude} aoAlterar={alterar('latitude')} erro={erros.latitude} />
      <CampoTexto id="longitude" label="Longitude" decimal placeholder="-49.2733001" valor={dados.longitude} aoAlterar={alterar('longitude')} erro={erros.longitude} />
      <CampoTexto id="dataInstalacao" label="Data de instalação (opcional)" type="date" valor={dados.dataInstalacao} aoAlterar={alterar('dataInstalacao')} erro={erros.dataInstalacao} />
      <CampoSelect
        id="estadoOperacional"
        label="Status"
        opcoes={opcoesEstadoCadastro(usina?.estadoOperacional)}
        valor={dados.estadoOperacional}
        aoAlterar={(valor) => setDados((atuais) => ({ ...atuais, estadoOperacional: valor as EstadoOperacional }))}
        erro={erros.estadoOperacional}
      />
    </FormularioModal>
  )
}

export default UsinaForm
