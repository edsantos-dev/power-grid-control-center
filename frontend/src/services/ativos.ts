import { listarAlimentadores } from './alimentadores'
import { listarLinhas } from './linhas'
import { listarMedidores } from './medidores'
import { listarSubestacoes } from './subestacoes'
import { listarTransformadores } from './transformadores'

export type TipoAtivo = 'Subestação' | 'Alimentador' | 'Linha' | 'Transformador' | 'Medidor'

export type AtivoCatalogo = {
  id: number
  nome: string
  tipo: TipoAtivo
}

const fontes: [TipoAtivo, () => Promise<{ id: number; nome: string }[]>][] = [
  ['Subestação', listarSubestacoes],
  ['Alimentador', listarAlimentadores],
  ['Linha', listarLinhas],
  ['Transformador', listarTransformadores],
  ['Medidor', listarMedidores],
]

// Os ativos compartilham o ID de AtivoRede, então um único mapa identifica qualquer tipo.
// Uma lista que falhe não impede as demais: o ativo correspondente fica sem nome.
export async function listarCatalogoAtivos() {
  const resultados = await Promise.allSettled(fontes.map(([, listar]) => listar()))

  return resultados.flatMap((resultado, indice) =>
    resultado.status === 'fulfilled'
      ? resultado.value.map(({ id, nome }) => ({ id, nome, tipo: fontes[indice][0] }))
      : []
  )
}
