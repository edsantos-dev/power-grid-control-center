import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import { registrarInterrupcao } from '../../services/eventos'
import type { EventoOperacional } from '../../types'
import { dataHoraLocalAtual, instanteDeDataHoraLocal } from '../../utils/formatacao'
import { CAMPO_OBRIGATORIO, removerCamposValidos } from '../../utils/validacao'
import CampoResponsavel from './CampoResponsavel'
import { idDoResponsavel, validarResponsavel, type Responsavel } from './responsavel'

type Props = {
  ativos: AtivoCatalogo[]
  aoFechar: () => void
  aoSalvar: (evento: EventoOperacional) => void
}

type Valores = {
  ativoId: string
  descricao: string
  inicioEfetivo: string
  criticidade: string
}

type Erros = Partial<Record<keyof Valores | 'responsavel', string>>

function validar(dados: Valores, responsavel: Responsavel, agora = dataHoraLocalAtual()) {
  return removerCamposValidos<Erros>({
    ativoId: dados.ativoId ? undefined : CAMPO_OBRIGATORIO,
    descricao: dados.descricao.trim() ? undefined : CAMPO_OBRIGATORIO,
    inicioEfetivo: !dados.inicioEfetivo
      ? CAMPO_OBRIGATORIO
      : dados.inicioEfetivo > agora
        ? 'O início da interrupção não pode estar no futuro.'
        : undefined,
    criticidade: dados.criticidade.trim().length > 32 ? 'Informe até 32 caracteres.' : undefined,
    responsavel: validarResponsavel(responsavel),
  })
}

function InterrupcaoForm({ ativos, aoFechar, aoSalvar }: Props) {
  const { usuario } = useAuth()
  const [dados, setDados] = useState<Valores>({ ativoId: '', descricao: '', inicioEfetivo: '', criticidade: '' })
  const [responsavel, setResponsavel] = useState<Responsavel>({ modo: 'eu', outroId: '' })
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

    const errosLocais = validar(dados, responsavel)
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(
        await registrarInterrupcao({
          ativoId: Number(dados.ativoId),
          descricao: dados.descricao.trim(),
          responsavelUsuarioId: idDoResponsavel(responsavel, usuario?.id) as number,
          inicioEfetivo: instanteDeDataHoraLocal(dados.inicioEfetivo),
          criticidade: dados.criticidade.trim() || null,
        })
      )
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal titulo="Registrar interrupção de fornecimento" erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoSelect
        id="ativoId"
        label="Ativo envolvido"
        className="sm:col-span-2"
        placeholder={ativos.length ? 'Selecione' : 'Nenhum ativo cadastrado'}
        opcoes={opcoesAtivo}
        valor={dados.ativoId}
        aoAlterar={alterar('ativoId')}
        erro={erros.ativoId}
      />
      <CampoTexto id="descricao" label="Descrição" className="sm:col-span-2" placeholder="Perda de fornecimento após descarga atmosférica" valor={dados.descricao} aoAlterar={alterar('descricao')} erro={erros.descricao} />
      <CampoTexto id="inicioEfetivo" label="Início da interrupção" type="datetime-local" valor={dados.inicioEfetivo} aoAlterar={alterar('inicioEfetivo')} erro={erros.inicioEfetivo} />
      <CampoResponsavel valor={responsavel} aoAlterar={setResponsavel} erro={erros.responsavel} />
      <CampoTexto id="criticidade" label="Criticidade (opcional)" placeholder="Texto livre, até 32 caracteres" valor={dados.criticidade} aoAlterar={alterar('criticidade')} erro={erros.criticidade} />
      <p className="text-xs text-slate-500 sm:col-span-2">
        Registra uma perda efetiva de fornecimento, distinta de uma ação programada. O término é informado na conclusão.
      </p>
    </FormularioModal>
  )
}

export default InterrupcaoForm
