import api from './api'
import type { LinhaSnapshot, PageResponse, SnapshotMonitoramento, TransformadorSnapshot } from '../types'

type Pagina = PageResponse<LinhaSnapshot | TransformadorSnapshot> & { consultadoEm: string }

// Linhas e transformadores numa única consulta consistente, em páginas de até 100 ativos.
// O instante de referência é o da primeira página.
export async function consultarSnapshot(): Promise<SnapshotMonitoramento> {
  const ativos: Pagina['content'] = []
  let consultadoEm = ''

  for (let page = 0; ; page++) {
    const { data } = await api.get<Pagina>('/api/monitoramento/snapshot', { params: { page, size: 100 } })
    consultadoEm ||= data.consultadoEm
    ativos.push(...data.content)
    if (data.last) break
  }

  const porId = (a: { ativoId: number }, b: { ativoId: number }) => a.ativoId - b.ativoId

  return {
    consultadoEm,
    linhas: ativos.filter((a): a is LinhaSnapshot => a.tipoAtivo === 'LINHA').sort(porId),
    transformadores: ativos.filter((a): a is TransformadorSnapshot => a.tipoAtivo === 'TRANSFORMADOR').sort(porId),
  }
}
