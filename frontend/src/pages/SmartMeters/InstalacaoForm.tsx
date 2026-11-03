import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { instalarMedidor } from '../../services/medidores'
import type { Alimentador, Consumidor, Medidor, Subestacao, Transformador } from '../../types'
import {
  hojeLocal,
  normalizarInstalacao,
  paraRequisicaoInstalacao,
  validarInstalacao,
  type ErrosInstalacao,
  type InstalacaoFormValues,
} from './validacao'

type Props = {
  medidor: Medidor
  consumidores: Consumidor[]
  transformadores: Transformador[]
  alimentadores: Alimentador[]
  subestacoes: Subestacao[]
  aoFechar: () => void
  aoSalvar: (medidor: Medidor) => void
}

function InstalacaoForm({ medidor, consumidores, transformadores, alimentadores, subestacoes, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState<InstalacaoFormValues>({
    consumidorId: '',
    transformadorId: '',
    latitude: '',
    longitude: '',
    dataInstalacao: '',
    enderecoFornecimento: '',
  })
  const [erros, setErros] = useState<ErrosInstalacao>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const nomeAlimentador = (id: number) => alimentadores.find((a) => a.id === id)?.nome ?? `Alimentador #${id}`
  const nomeSubestacao = (id: number) => subestacoes.find((s) => s.id === id)?.nome ?? `Subestação #${id}`

  const opcoesConsumidor = consumidores.map((c) => ({ valor: String(c.id), label: c.nomeRazaoSocial }))
  const opcoesTransformador = transformadores.map((t) => ({
    valor: String(t.id),
    label: `${t.nome} (${nomeAlimentador(t.alimentadorId)})`,
  }))
  const transformadorSelecionado = transformadores.find((t) => String(t.id) === dados.transformadorId)

  function alterar(campo: keyof InstalacaoFormValues) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const normalizados = normalizarInstalacao(dados)
    const errosLocais = validarInstalacao(normalizados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(await instalarMedidor(medidor.id, paraRequisicaoInstalacao(normalizados)))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal
      titulo={`Instalar ${medidor.nome}`}
      erro={erro}
      enviando={enviando}
      aoFechar={aoFechar}
      aoEnviar={handleSubmit}
    >
      <CampoSelect
        id="consumidorId"
        label="Consumidor titular"
        className="sm:col-span-2"
        placeholder={consumidores.length ? 'Selecione' : 'Cadastre um consumidor antes'}
        opcoes={opcoesConsumidor}
        valor={dados.consumidorId}
        aoAlterar={alterar('consumidorId')}
        erro={erros.consumidorId}
      />
      <div className="sm:col-span-2">
        <CampoSelect
          id="transformadorId"
          label="Transformador"
          placeholder={transformadores.length ? 'Selecione' : 'Cadastre um transformador antes'}
          opcoes={opcoesTransformador}
          valor={dados.transformadorId}
          aoAlterar={alterar('transformadorId')}
          erro={erros.transformadorId}
        />

        {transformadorSelecionado && (
          <p className="mt-1 text-xs text-slate-400">
            Alimentador: {nomeAlimentador(transformadorSelecionado.alimentadorId)} · Subestação:{' '}
            {nomeSubestacao(transformadorSelecionado.subestacaoId)}
          </p>
        )}
      </div>
      <CampoTexto id="latitude" label="Latitude" decimal placeholder="-25.4284001" valor={dados.latitude} aoAlterar={alterar('latitude')} erro={erros.latitude} />
      <CampoTexto id="longitude" label="Longitude" decimal placeholder="-49.2733001" valor={dados.longitude} aoAlterar={alterar('longitude')} erro={erros.longitude} />
      <div>
        <label htmlFor="dataInstalacao" className="mb-2 block text-sm font-medium text-slate-300">
          Data de instalação
        </label>
        <input
          id="dataInstalacao"
          type="date"
          max={hojeLocal()}
          value={dados.dataInstalacao}
          onChange={(e) => alterar('dataInstalacao')(e.target.value)}
          className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
        />
        {erros.dataInstalacao && <p className="mt-1 text-xs text-red-400">{erros.dataInstalacao}</p>}
      </div>
      <CampoTexto
        id="enderecoFornecimento"
        label="Endereço de fornecimento (opcional)"
        valor={dados.enderecoFornecimento}
        aoAlterar={alterar('enderecoFornecimento')}
        erro={erros.enderecoFornecimento}
      />
      <p className="text-xs text-slate-500 sm:col-span-2">
        A instalação é definitiva neste recorte: transferências e reinstalações dependem de histórico ainda não
        implementado.
      </p>
    </FormularioModal>
  )
}

export default InstalacaoForm
