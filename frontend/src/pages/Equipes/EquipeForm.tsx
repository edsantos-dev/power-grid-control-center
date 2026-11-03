import { useState, type FormEvent } from 'react'
import { CampoTexto, FormularioModal } from '../../components/formulario'
import { extrairErroApi } from '../../services/api'
import { atualizarEquipe, criarEquipe } from '../../services/equipes'
import type { Equipe } from '../../types'
import { validarNome } from '../../utils/validacao'

type Props = {
  equipe: Equipe | null
  aoFechar: () => void
  aoSalvar: (equipe: Equipe) => void
}

function EquipeForm({ equipe, aoFechar, aoSalvar }: Props) {
  const [nome, setNome] = useState(equipe?.nome ?? '')
  const [responsabilidades, setResponsabilidades] = useState(equipe?.responsabilidades ?? '')
  const [especialidades, setEspecialidades] = useState(equipe?.especialidades ?? '')
  const [erros, setErros] = useState<Record<string, string | undefined>>({})
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    const erroNome = validarNome(nome.trim())
    setErros({ nome: erroNome })
    if (erroNome) return

    setEnviando(true)

    try {
      // Textos opcionais vazios seguem como nulos
      const dados = { nome: nome.trim(), responsabilidades: responsabilidades.trim() || null, especialidades: especialidades.trim() || null }
      aoSalvar(equipe ? await atualizarEquipe(equipe.id, dados) : await criarEquipe(dados))
    } catch (error) {
      const { mensagem, campos } = extrairErroApi(error)
      setErro(mensagem)
      setErros(campos)
      setEnviando(false)
    }
  }

  return (
    <FormularioModal titulo={equipe ? 'Editar equipe' : 'Nova equipe'} erro={erro} enviando={enviando} aoFechar={aoFechar} aoEnviar={handleSubmit}>
      <CampoTexto id="nome" label="Nome" className="sm:col-span-2" placeholder="Equipe Norte" valor={nome} aoAlterar={setNome} erro={erros.nome} />
      <CampoTexto id="responsabilidades" label="Responsabilidades (opcional)" className="sm:col-span-2" placeholder="Manutenção preventiva de subestações" valor={responsabilidades} aoAlterar={setResponsabilidades} erro={erros.responsabilidades} />
      <CampoTexto id="especialidades" label="Especialidades (opcional)" className="sm:col-span-2" placeholder="Linhas energizadas, transformadores" valor={especialidades} aoAlterar={setEspecialidades} erro={erros.especialidades} />
      {!equipe && (
        <p className="text-xs text-slate-500 sm:col-span-2">
          A situação é definida pelo servidor. Inclua integrantes antes de marcar a equipe como disponível.
        </p>
      )}
    </FormularioModal>
  )
}

export default EquipeForm
