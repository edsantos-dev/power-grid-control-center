import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import { registrarFalha } from '../../services/eventos'
import type { Criticidade, FalhaEquipamento } from '../../types'
import { CRITICIDADE_STYLES, CRITICIDADES } from '../../utils/constants'
import { CAMPO_OBRIGATORIO, removerCamposValidos } from '../../utils/validacao'
import CampoResponsavel from './CampoResponsavel'
import { idDoResponsavel, validarResponsavel, type Responsavel } from './responsavel'

type Props = {
  ativos: AtivoCatalogo[]
  aoFechar: () => void
  aoSalvar: (falha: FalhaEquipamento) => void
}

type Valores = {
  ativoId: string
  descricao: string
  criticidade: Criticidade | ''
}

type Erros = Partial<Record<keyof Valores | 'responsavel', string>>

function FalhaForm({ ativos, aoFechar, aoSalvar }: Props) {
  const { usuario } = useAuth()
  const [dados, setDados] = useState<Valores>({ ativoId: '', descricao: '', criticidade: '' })
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

    const errosLocais = removerCamposValidos<Erros>({
      ativoId: dados.ativoId ? undefined : CAMPO_OBRIGATORIO,
      descricao: dados.descricao.trim() ? undefined : CAMPO_OBRIGATORIO,
      responsavel: validarResponsavel(responsavel),
    })
    setErros(errosLocais)

    if (Object.keys(errosLocais).length > 0) {
      return
    }

    setEnviando(true)

    try {
      aoSalvar(
        await registrarFalha({
          ativoId: Number(dados.ativoId),
          responsavelUsuarioId: idDoResponsavel(responsavel, usuario?.id) as number,
          descricao: dados.descricao.trim(),
          criticidade: dados.criticidade || null,
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
    <FormularioModal titulo="Registrar falha de equipamento" erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoSelect
        id="ativoId"
        label="Ativo com falha"
        className="sm:col-span-2"
        placeholder={ativos.length ? 'Selecione' : 'Nenhum ativo cadastrado'}
        opcoes={opcoesAtivo}
        valor={dados.ativoId}
        aoAlterar={alterar('ativoId')}
        erro={erros.ativoId}
      />
      <CampoTexto id="descricao" label="Descrição" className="sm:col-span-2" placeholder="Disjuntor não abre por comando remoto" valor={dados.descricao} aoAlterar={alterar('descricao')} erro={erros.descricao} />
      <CampoResponsavel valor={responsavel} aoAlterar={setResponsavel} erro={erros.responsavel} />
      <CampoSelect
        id="criticidade"
        label="Criticidade (opcional)"
        placeholder="Não informada"
        opcoes={CRITICIDADES.map((c) => ({ valor: c, label: CRITICIDADE_STYLES[c].label }))}
        valor={dados.criticidade}
        aoAlterar={alterar('criticidade')}
        erro={erros.criticidade}
      />
      <p className="text-xs text-slate-500 sm:col-span-2">
        O ativo passa a FALHA e o estado atual é guardado para consulta. Enquanto a falha não for concluída, o ativo não recebe manobras,
        mudanças de topologia nem instalação de medidores.
      </p>
    </FormularioModal>
  )
}

export default FalhaForm
