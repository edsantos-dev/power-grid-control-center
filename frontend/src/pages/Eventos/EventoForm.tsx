import { useState, type FormEvent } from 'react'
import { CampoSelect, CampoTexto, FormularioModal } from '../../components/formulario'
import { useAuth } from '../../hooks/useAuth'
import { extrairErroApi } from '../../services/api'
import type { AtivoCatalogo } from '../../services/ativos'
import { criarEvento } from '../../services/eventos'
import type { CategoriaEvento, EventoOperacional } from '../../types'
import { instanteDeDataHoraLocal } from '../../utils/formatacao'
import { CAMPO_OBRIGATORIO, removerCamposValidos } from '../../utils/validacao'
import CampoResponsavel from './CampoResponsavel'
import { idDoResponsavel, validarResponsavel, type Responsavel } from './responsavel'
import { CATEGORIA_LABEL } from './constantes'

type Props = {
  ativos: AtivoCatalogo[]
  categorias: CategoriaEvento[]
  aoFechar: () => void
  aoSalvar: (evento: EventoOperacional) => void
}

type Valores = {
  ativoId: string
  categoria: CategoriaEvento | ''
  descricao: string
  inicioPrevisto: string
  terminoPrevisto: string
  criticidade: string
}

type Erros = Partial<Record<keyof Valores | 'responsavel', string>>

function validar(dados: Valores, responsavel: Responsavel) {
  return removerCamposValidos<Erros>({
    ativoId: dados.ativoId ? undefined : CAMPO_OBRIGATORIO,
    categoria: dados.categoria ? undefined : CAMPO_OBRIGATORIO,
    descricao: dados.descricao.trim() ? undefined : CAMPO_OBRIGATORIO,
    inicioPrevisto: dados.inicioPrevisto ? undefined : CAMPO_OBRIGATORIO,
    // Comparação entre valores datetime-local do mesmo fuso, sem conversão
    terminoPrevisto:
      dados.terminoPrevisto && dados.inicioPrevisto && dados.terminoPrevisto < dados.inicioPrevisto
        ? 'O término previsto não pode ser anterior ao início.'
        : undefined,
    criticidade: dados.criticidade.trim().length > 32 ? 'Informe até 32 caracteres.' : undefined,
    responsavel: validarResponsavel(responsavel),
  })
}

function EventoForm({ ativos, categorias, aoFechar, aoSalvar }: Props) {
  const { usuario } = useAuth()
  const [dados, setDados] = useState<Valores>({
    ativoId: '',
    categoria: categorias.length === 1 ? categorias[0] : '',
    descricao: '',
    inicioPrevisto: '',
    terminoPrevisto: '',
    criticidade: '',
  })
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
        await criarEvento({
          ativoId: Number(dados.ativoId),
          categoria: dados.categoria as CategoriaEvento,
          descricao: dados.descricao.trim(),
          responsavelUsuarioId: idDoResponsavel(responsavel, usuario?.id) as number,
          inicioPrevisto: instanteDeDataHoraLocal(dados.inicioPrevisto),
          terminoPrevisto: dados.terminoPrevisto ? instanteDeDataHoraLocal(dados.terminoPrevisto) : null,
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
    <FormularioModal titulo="Registrar evento programado" erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoSelect
        id="categoria"
        label="Categoria"
        placeholder="Selecione"
        opcoes={categorias.map((c) => ({ valor: c, label: CATEGORIA_LABEL[c] }))}
        valor={dados.categoria}
        aoAlterar={alterar('categoria')}
        erro={erros.categoria}
      />
      <CampoSelect
        id="ativoId"
        label="Ativo"
        placeholder={ativos.length ? 'Selecione' : 'Nenhum ativo cadastrado'}
        opcoes={opcoesAtivo}
        valor={dados.ativoId}
        aoAlterar={alterar('ativoId')}
        erro={erros.ativoId}
      />
      <CampoTexto id="descricao" label="Descrição" className="sm:col-span-2" valor={dados.descricao} aoAlterar={alterar('descricao')} erro={erros.descricao} />
      <CampoTexto id="inicioPrevisto" label="Início previsto" type="datetime-local" valor={dados.inicioPrevisto} aoAlterar={alterar('inicioPrevisto')} erro={erros.inicioPrevisto} />
      <CampoTexto id="terminoPrevisto" label="Término previsto (opcional)" type="datetime-local" valor={dados.terminoPrevisto} aoAlterar={alterar('terminoPrevisto')} erro={erros.terminoPrevisto} />
      <CampoResponsavel valor={responsavel} aoAlterar={setResponsavel} erro={erros.responsavel} />
      <CampoTexto id="criticidade" label="Criticidade (opcional)" placeholder="Texto livre, até 32 caracteres" valor={dados.criticidade} aoAlterar={alterar('criticidade')} erro={erros.criticidade} />
      <p className="text-xs text-slate-500 sm:col-span-2">
        O evento é criado como aberto. Você fica registrado como autor do registro.
      </p>
    </FormularioModal>
  )
}

export default EventoForm
