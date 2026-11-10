import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { atualizarTransformador, criarTransformador } from '../../services/transformadores'
import type { Alimentador, EstadoOperacional, Subestacao, Transformador } from '../../types'
import { opcoesEstadoCadastro } from '../../utils/constants'
import {
  normalizarTransformador,
  paraRequisicao,
  validarTransformador,
  type ErrosTransformador,
  type TransformadorFormValues,
} from './validacao'

type Props = {
  transformador: Transformador | null
  alimentadores: Alimentador[]
  subestacoes: Subestacao[]
  aoFechar: () => void
  aoSalvar: (transformador: Transformador) => void
}

function valoresIniciais(transformador: Transformador | null): TransformadorFormValues {
  if (!transformador) {
    return {
      nome: '',
      latitude: '',
      longitude: '',
      capacidadeMva: '',
      alimentadorId: '',
      dataInstalacao: '',
      estadoOperacional: 'OPERACIONAL',
    }
  }

  return {
    nome: transformador.nome,
    latitude: String(transformador.latitude),
    longitude: String(transformador.longitude),
    capacidadeMva: String(transformador.capacidadeMva),
    alimentadorId: String(transformador.alimentadorId),
    dataInstalacao: transformador.dataInstalacao,
    estadoOperacional: transformador.estadoOperacional,
  }
}

function TransformadorForm({ transformador, alimentadores, subestacoes, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState(() => valoresIniciais(transformador))
  const [erros, setErros] = useState<ErrosTransformador>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const nomeSubestacao = (id: number) => subestacoes.find((s) => s.id === id)?.nome ?? `Subestação #${id}`
  const opcoesAlimentador = alimentadores.map((a) => ({
    valor: String(a.id),
    label: `${a.nome} (${nomeSubestacao(a.subestacaoId)})`,
  }))
  const alimentadorSelecionado = alimentadores.find((a) => String(a.id) === dados.alimentadorId)

  function alterar(campo: keyof TransformadorFormValues) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = normalizarTransformador(dados)
    const errosLocais = validarTransformador(normalizados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      const requisicao = paraRequisicao(normalizados)
      aoSalvar(
        transformador
          ? await atualizarTransformador(transformador.id, requisicao)
          : await criarTransformador(requisicao)
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
      titulo={transformador ? 'Editar transformador' : 'Novo transformador'}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="Transformador T-01" valor={dados.nome} aoAlterar={alterar('nome')} erro={erros.nome} />
      <div className="sm:col-span-2">
        <CampoSelect
          id="alimentadorId"
          label="Alimentador"
          placeholder={alimentadores.length ? 'Selecione' : 'Cadastre um alimentador antes'}
          opcoes={opcoesAlimentador}
          valor={dados.alimentadorId}
          aoAlterar={alterar('alimentadorId')}
          erro={erros.alimentadorId}
        />

        {alimentadorSelecionado && (
          <p className="mt-1 text-xs text-slate-400">
            Subestação: {nomeSubestacao(alimentadorSelecionado.subestacaoId)}
          </p>
        )}
      </div>
      <CampoTexto id="latitude" label="Latitude" decimal placeholder="-25.4284001" valor={dados.latitude} aoAlterar={alterar('latitude')} erro={erros.latitude} />
      <CampoTexto id="longitude" label="Longitude" decimal placeholder="-49.2733001" valor={dados.longitude} aoAlterar={alterar('longitude')} erro={erros.longitude} />
      <CampoTexto id="capacidadeMva" label="Capacidade (MVA)" decimal placeholder="45" valor={dados.capacidadeMva} aoAlterar={alterar('capacidadeMva')} erro={erros.capacidadeMva} />
      <CampoTexto id="dataInstalacao" label="Data de instalação" type="date" valor={dados.dataInstalacao} aoAlterar={alterar('dataInstalacao')} erro={erros.dataInstalacao} />
      <CampoSelect
        id="estadoOperacional"
        label="Status"
        opcoes={opcoesEstadoCadastro(transformador?.estadoOperacional)}
        valor={dados.estadoOperacional}
        aoAlterar={(valor) => setDados((atuais) => ({ ...atuais, estadoOperacional: valor as EstadoOperacional }))}
        erro={erros.estadoOperacional}
      />
    </FormularioModal>
  )
}

export default TransformadorForm
