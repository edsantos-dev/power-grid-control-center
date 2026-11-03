import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { criarAlerta } from '../../services/alertas'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import type { AlertaRequest, Criticidade, EscopoAlerta } from '../../types'
import { CRITICIDADE_STYLES, CRITICIDADES } from '../../utils/constants'
import { CAMPO_OBRIGATORIO, removerCamposValidos } from '../../utils/validacao'

type Props = {
  ativos: AtivoCatalogo[]
  aoFechar: () => void
  aoSalvar: () => void
}

type Valores = {
  tipo: string
  criticidade: Criticidade | ''
  escopo: EscopoAlerta
  ativoId: string
}

type Erros = Partial<Record<keyof Valores, string>>

const opcoesCriticidade = CRITICIDADES.map((valor) => ({ valor, label: CRITICIDADE_STYLES[valor].label }))

const opcoesEscopo = [
  { valor: 'ATIVO', label: 'Ativo específico' },
  { valor: 'REDE_MONITORADA', label: 'Rede monitorada' },
]

function validar(dados: Valores) {
  const tipo = dados.tipo.trim()

  return removerCamposValidos<Erros>({
    tipo: !tipo ? CAMPO_OBRIGATORIO : tipo.length > 128 ? 'Informe até 128 caracteres.' : undefined,
    criticidade: dados.criticidade ? undefined : CAMPO_OBRIGATORIO,
    ativoId: dados.escopo === 'ATIVO' && !dados.ativoId ? 'Selecione o ativo afetado.' : undefined,
  })
}

// REDE_MONITORADA não referencia ativo: o backend recusa ativoId nesse escopo
function paraRequisicao(dados: Valores): AlertaRequest {
  return {
    tipo: dados.tipo.trim(),
    criticidade: dados.criticidade as Criticidade,
    escopo: dados.escopo,
    ativoId: dados.escopo === 'ATIVO' ? Number(dados.ativoId) : null,
  }
}

function AlertaForm({ ativos, aoFechar, aoSalvar }: Props) {
  const [dados, setDados] = useState<Valores>({ tipo: '', criticidade: '', escopo: 'ATIVO', ativoId: '' })
  const [erros, setErros] = useState<Erros>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  const opcoesAtivo = [...ativos]
    .sort((a, b) => a.tipo.localeCompare(b.tipo) || a.nome.localeCompare(b.nome))
    .map((a) => ({ valor: String(a.id), label: `${a.tipo} · ${a.nome}` }))

  function alterar(campo: keyof Valores) {
    return (valor: string) => setDados((atuais) => ({ ...atuais, [campo]: valor }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const errosLocais = validar(dados)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      await criarAlerta(paraRequisicao(dados))
      aoSalvar()
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal titulo="Registrar alerta" erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoTexto id="tipo" label="Tipo" className="sm:col-span-2" placeholder="Oscilação de tensão" valor={dados.tipo} aoAlterar={alterar('tipo')} erro={erros.tipo} />
      <CampoSelect id="criticidade" label="Criticidade" placeholder="Selecione" opcoes={opcoesCriticidade} valor={dados.criticidade} aoAlterar={alterar('criticidade')} erro={erros.criticidade} />
      <CampoSelect
        id="escopo"
        label="Escopo"
        opcoes={opcoesEscopo}
        valor={dados.escopo}
        aoAlterar={(valor) => setDados((atuais) => ({ ...atuais, escopo: valor as EscopoAlerta, ativoId: '' }))}
      />
      {dados.escopo === 'ATIVO' && (
        <CampoSelect
          id="ativoId"
          label="Ativo afetado"
          className="sm:col-span-2"
          placeholder={ativos.length ? 'Selecione' : 'Nenhum ativo cadastrado'}
          opcoes={opcoesAtivo}
          valor={dados.ativoId}
          aoAlterar={alterar('ativoId')}
          erro={erros.ativoId}
        />
      )}
      <p className="text-xs text-slate-500 sm:col-span-2">
        O alerta é registrado como aberto, com o instante definido pelo servidor.
      </p>
    </FormularioModal>
  )
}

export default AlertaForm
