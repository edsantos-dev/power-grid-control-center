import { useEffect, useState } from 'react'
import { ArrowRight, MapPin } from 'lucide-react'
import Modal from '../../components/Modal'
import { listarAlimentadores } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import { listarMedidoresDoConsumidor } from '../../services/consumidores'
import { listarSubestacoes } from '../../services/subestacoes'
import { listarTransformadores } from '../../services/transformadores'
import type { Consumidor, Medidor } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { formatarData } from '../../utils/formatacao'

type Props = {
  consumidor: Consumidor
  aoFechar: () => void
}

type Nomes = Record<'transformadores' | 'alimentadores' | 'subestacoes', Map<number, string>>

function porId(lista: { id: number; nome: string }[]) {
  return new Map(lista.map((item) => [item.id, item.nome]))
}

function Fornecimento({ medidor }: { medidor: Medidor }) {
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

function MedidoresDoConsumidor({ consumidor, aoFechar }: Props) {
  const [medidores, setMedidores] = useState<Medidor[]>([])
  const [nomes, setNomes] = useState<Nomes | null>(null)
  const [carregando, setCarregando] = useState(true)
  const [erro, setErro] = useState('')
  const [tentativa, setTentativa] = useState(0)

  useEffect(() => {
    let ativo = true
    setCarregando(true)
    setErro('')

    Promise.all([
      listarMedidoresDoConsumidor(consumidor.id),
      listarTransformadores(),
      listarAlimentadores(),
      listarSubestacoes(),
    ])
      .then(([lista, transformadores, alimentadores, subestacoes]) => {
        if (!ativo) return
        setMedidores(lista)
        setNomes({
          transformadores: porId(transformadores),
          alimentadores: porId(alimentadores),
          subestacoes: porId(subestacoes),
        })
      })
      .catch((error) => ativo && setErro(extrairErroApi(error).mensagem))
      .finally(() => ativo && setCarregando(false))

    // Ignora a resposta se o painel for fechado antes de ela chegar
    return () => {
      ativo = false
    }
  }, [consumidor.id, tentativa])

  function nome(tipo: keyof Nomes, id: number) {
    return nomes?.[tipo].get(id) ?? `#${id}`
  }

  return (
    <Modal titulo={`Medidores de ${consumidor.nomeRazaoSocial}`} aoFechar={aoFechar} largura="max-w-4xl">
      <p className="mb-4 text-xs text-slate-500">
        O fornecimento é representado pela localização atual de cada medidor. Os vínculos seguem a cadeia cadastral
        atual, sem histórico de titularidade ou instalações anteriores.
      </p>

      {carregando && (
        <p className="py-8 text-center text-sm text-slate-500">Carregando medidores...</p>
      )}

      {!carregando && erro && (
        <div className="flex flex-col items-center gap-3 py-8">
          <p className="text-sm text-red-400">{erro}</p>
          <button
            type="button"
            onClick={() => setTentativa((atual) => atual + 1)}
            className="rounded-lg border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:bg-slate-800"
          >
            Tentar novamente
          </button>
        </div>
      )}

      {!carregando && !erro && medidores.length === 0 && (
        <p className="py-8 text-center text-sm text-slate-500">Nenhum medidor vinculado a este consumidor.</p>
      )}

      {!carregando && !erro && medidores.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Medidor</th>
                <th className="pb-3 font-normal">Fornecimento</th>
                <th className="pb-3 font-normal">Vínculo na rede</th>
                <th className="pb-3 font-normal">Instalação</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {medidores.map((m) => {
                const status = ESTADO_STYLES[m.estadoOperacional]

                return (
                  <tr key={m.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4">
                      <p className="text-slate-200">{m.nome}</p>
                      <p className="mt-1 text-xs text-slate-500">Série {m.numeroSerie}</p>
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      <Fornecimento medidor={m} />
                    </td>
                    <td className="py-3 pr-4 text-slate-400">
                      {m.transformadorId === null ? (
                        <span className="text-slate-500">Sem transformador</span>
                      ) : (
                        <span className="flex flex-wrap items-center gap-1.5">
                          {nome('transformadores', m.transformadorId)}
                          <ArrowRight aria-label="alimentado por" className="h-3.5 w-3.5 text-slate-500" />
                          {m.alimentadorId !== null && nome('alimentadores', m.alimentadorId)}
                          <ArrowRight aria-label="na subestação" className="h-3.5 w-3.5 text-slate-500" />
                          {m.subestacaoId !== null && nome('subestacoes', m.subestacaoId)}
                        </span>
                      )}
                    </td>
                    <td className="py-3 text-slate-300">{m.dataInstalacao ? formatarData(m.dataInstalacao) : '—'}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <span className={`h-1.5 w-1.5 rounded-full ${status.dot}`} />
                        {status.label}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  )
}

export default MedidoresDoConsumidor
