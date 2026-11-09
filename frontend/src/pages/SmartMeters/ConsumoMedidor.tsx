import { useState, type FormEvent } from 'react'
import { Search } from 'lucide-react'
import Modal from '../../components/Modal'
import { extrairErroApi } from '../../services/api'
import { consultarMedicaoConsumo } from '../../services/medidores'
import type { Consumidor, MedicaoConsumo, Medidor } from '../../types'
import { formatarDataHora, formatarNumero } from '../../utils/formatacao'
import type { NomesRede } from '../../utils/rede'

type Props = {
  medidor: Medidor
  consumidores: Consumidor[]
  nomes: NomesRede
  aoFechar: () => void
}

// Os vínculos da medição são o retrato do momento da leitura: mostram o nome atual ou, na falta dele, o identificador
function descreverVinculo(id: number | null, nome: string | undefined, rotulo: string) {
  if (id === null) return '—'
  return nome ?? `${rotulo} #${id}`
}

function ConsumoMedidor({ medidor, consumidores, nomes, aoFechar }: Props) {
  const [codigo, setCodigo] = useState('')
  const [erroCampo, setErroCampo] = useState('')
  const [erro, setErro] = useState('')
  const [medicao, setMedicao] = useState<MedicaoConsumo | null>(null)
  const [consultando, setConsultando] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')
    setMedicao(null)

    const texto = codigo.trim()
    if (!/^[1-9]\d{0,17}$/.test(texto)) {
      setErroCampo('Informe o número da medição, um inteiro maior que zero.')
      return
    }

    setErroCampo('')
    setConsultando(true)

    try {
      setMedicao(await consultarMedicaoConsumo(medidor.id, Number(texto)))
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setConsultando(false)
    }
  }

  const consumidor = medicao?.consumidorId == null ? undefined : consumidores.find((c) => c.id === medicao.consumidorId)?.nomeRazaoSocial

  return (
    <Modal titulo={`Consumo de ${medidor.nome}`} aoFechar={aoFechar}>
      <form onSubmit={handleSubmit} noValidate className="flex flex-wrap items-end gap-3">
        <div className="min-w-48 flex-1">
          <label htmlFor="medicao-consumo" className="mb-2 block text-sm font-medium text-slate-300">
            Número da medição
          </label>
          <input
            id="medicao-consumo"
            inputMode="numeric"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value)}
            className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500"
          />
        </div>

        <button
          type="submit"
          disabled={consultando}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <Search className="h-4 w-4" />
          {consultando ? 'Consultando...' : 'Consultar'}
        </button>
      </form>

      {erroCampo && <p className="mt-2 text-xs text-red-400">{erroCampo}</p>}
      {erro && <p className="mt-4 text-sm text-red-400">{erro}</p>}

      {medicao && (
        <dl className="mt-6 grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2" aria-label="Medição de consumo">
          <div className="sm:col-span-2">
            <dt className="text-xs text-slate-500">Consumo no período</dt>
            <dd className="text-2xl font-bold text-white">
              {formatarNumero(medicao.consumoKwh)} <span className="text-sm font-normal text-slate-400">kWh</span>
            </dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Período</dt>
            <dd className="text-slate-300">
              {formatarDataHora(medicao.inicioPeriodo)} a {formatarDataHora(medicao.fimPeriodo)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Origem e recebimento</dt>
            <dd className="text-slate-300">
              {medicao.origem === 'SIMULADA' ? 'Simulada' : 'Real'} · recebida em {formatarDataHora(medicao.recebidoEm)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Consumidor na leitura</dt>
            <dd className="text-slate-300">{descreverVinculo(medicao.consumidorId, consumidor, 'Consumidor')}</dd>
          </div>
          <div>
            <dt className="text-xs text-slate-500">Rede na leitura</dt>
            <dd className="text-slate-300">
              {descreverVinculo(medicao.transformadorId, nomes.transformadores.get(medicao.transformadorId ?? -1), 'Transformador')}
              {' → '}
              {descreverVinculo(medicao.alimentadorId, nomes.alimentadores.get(medicao.alimentadorId ?? -1), 'Alimentador')}
              {' → '}
              {descreverVinculo(medicao.subestacaoId, nomes.subestacoes.get(medicao.subestacaoId ?? -1), 'Subestação')}
            </dd>
          </div>
        </dl>
      )}

      <p className="mt-6 text-xs text-slate-500">
        A API consulta uma medição pelo seu número, informado na ingestão ou pelo simulador. A listagem de medições por medidor ainda não existe.
      </p>
    </Modal>
  )
}

export default ConsumoMedidor
