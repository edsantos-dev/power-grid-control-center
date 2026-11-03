import { ArrowRight, MapPin } from 'lucide-react'
import type { Medidor } from '../types'
import type { NomesRede } from '../utils/rede'

// O fornecimento é representado pela localização atual do medidor
export function Fornecimento({ medidor }: { medidor: Medidor }) {
  if (medidor.enderecoFornecimento) {
    return <span>{medidor.enderecoFornecimento}</span>
  }

  if (medidor.latitude !== null && medidor.longitude !== null) {
    return (
      <span className="flex items-center gap-1.5">
        <MapPin className="h-3.5 w-3.5 text-slate-500" />
        {medidor.latitude}, {medidor.longitude}
      </span>
    )
  }

  return <span className="text-slate-500">Não informado</span>
}

export function CadeiaRede({ medidor, nomes }: { medidor: Medidor; nomes: NomesRede | null }) {
  if (medidor.transformadorId === null) {
    return <span className="text-slate-500">Sem transformador</span>
  }

  const nome = (tipo: keyof NomesRede, id: number | null) => (id === null ? '—' : nomes?.[tipo].get(id) ?? `#${id}`)

  return (
    <span className="flex flex-wrap items-center gap-1.5">
      {nome('transformadores', medidor.transformadorId)}
      <ArrowRight aria-label="alimentado por" className="h-3.5 w-3.5 text-slate-500" />
      {nome('alimentadores', medidor.alimentadorId)}
      <ArrowRight aria-label="na subestação" className="h-3.5 w-3.5 text-slate-500" />
      {nome('subestacoes', medidor.subestacaoId)}
    </span>
  )
}
