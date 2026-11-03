import { useEffect, useState, type FormEvent } from 'react'
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react'
import { CampoSelect } from '../../components/formulario'
import { useAuth } from '../../hooks/useAuth'
import { calcularPerdasTecnicas, definirPercentualPerda, listarAlimentadores } from '../../services/alimentadores'
import { extrairErroApi } from '../../services/api'
import type { Alimentador, PerdaTecnica } from '../../types'
import { formatarDataHora, formatarNumero, formatarPercentual, inicioDoDiaLocal, inicioDoDiaSeguinteLocal } from '../../utils/formatacao'

const SITUACAO = {
  OK: { rotulo: 'Calculada', classe: 'text-green-400', icone: CheckCircle2 },
  DADOS_INSUFICIENTES: { rotulo: 'Dados insuficientes', classe: 'text-yellow-400', icone: AlertTriangle },
  SEM_ENERGIA_INJETADA: { rotulo: 'Sem energia injetada', classe: 'text-slate-300', icone: Info },
}

function Indicador({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-950 p-4">
      <p className="text-xs text-slate-400">{rotulo}</p>
      <p className="mt-1 text-xl font-semibold text-white">{valor}</p>
    </div>
  )
}

function ParametroPerda({ alimentadorId }: { alimentadorId: number }) {
  const [valor, setValor] = useState('')
  const [mensagem, setMensagem] = useState('')
  const [erro, setErro] = useState('')

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')
    setMensagem('')
    const normalizado = valor.trim().replace(',', '.')

    if (!/^\d{1,3}(\.\d{1,6})?$/.test(normalizado) || Number(normalizado) >= 100) {
      setErro('Informe um percentual de 0 a menos de 100, com até seis casas decimais.')
      return
    }

    try {
      const resposta = await definirPercentualPerda(alimentadorId, normalizado)
      setMensagem(`Percentual de estimativa definido em ${formatarPercentual(resposta.percentualPerdaTecnica)}. Vale para as próximas medições.`)
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="mt-4 flex flex-wrap items-end gap-3 rounded-lg border border-slate-800 p-3">
      <div>
        <label htmlFor="percentual-perda" className="mb-1 block text-xs text-slate-400">
          Percentual de estimativa do alimentador (%)
        </label>
        <input
          id="percentual-perda"
          inputMode="decimal"
          value={valor}
          onChange={(e) => setValor(e.target.value)}
          className="w-40 rounded-lg border border-slate-700 bg-slate-800 px-3 py-2 text-sm text-white outline-none focus:border-blue-500"
        />
      </div>
      <button type="submit" className="rounded-lg border border-slate-700 px-3 py-2 text-sm text-slate-300 transition hover:bg-slate-800">
        Definir
      </button>
      {mensagem && <p className="text-xs text-green-400" role="status">{mensagem}</p>}
      {erro && <p className="text-xs text-red-400">{erro}</p>}
    </form>
  )
}

function PerdasTecnicas() {
  const { usuario } = useAuth()
  const [alimentadores, setAlimentadores] = useState<Alimentador[]>([])
  const [alimentadorId, setAlimentadorId] = useState('')
  const [de, setDe] = useState('')
  const [ate, setAte] = useState('')
  const [resultado, setResultado] = useState<PerdaTecnica | null>(null)
  const [erro, setErro] = useState('')
  const [calculando, setCalculando] = useState(false)

  useEffect(() => {
    listarAlimentadores()
      .then((lista) => setAlimentadores([...lista].sort((a, b) => a.nome.localeCompare(b.nome))))
      .catch((error) => setErro(extrairErroApi(error).mensagem))
  }, [])

  async function handleCalcular(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setResultado(null)

    if (!alimentadorId || !de || !ate) {
      setErro('Selecione o alimentador e o período.')
      return
    }
    if (ate < de) {
      setErro('A data final deve ser igual ou posterior à inicial.')
      return
    }

    setErro('')
    setCalculando(true)

    try {
      // Período por dia local: do início do primeiro dia ao início do dia seguinte ao último
      setResultado(await calcularPerdasTecnicas(Number(alimentadorId), inicioDoDiaLocal(de), inicioDoDiaSeguinteLocal(ate)))
    } catch (error) {
      setErro(extrairErroApi(error).mensagem)
    } finally {
      setCalculando(false)
    }
  }

  const situacao = resultado ? SITUACAO[resultado.situacao] : null
  const mwh = (valor: number | null) => (valor === null ? '—' : `${formatarNumero(valor)} MWh`)

  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-perdas">
      <h3 id="titulo-perdas" className="text-lg font-semibold text-white">Perdas técnicas por alimentador</h3>
      <p className="mt-1 text-xs text-slate-500">
        Estimativa: energia injetada × percentual de referência registrado em cada medição. O percentual do período é ponderado pela energia e exige medições cobrindo todo o intervalo.
      </p>

      <form onSubmit={handleCalcular} noValidate className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <CampoSelect
          id="perdas-alimentador"
          label="Alimentador"
          placeholder={alimentadores.length ? 'Selecione' : 'Nenhum alimentador cadastrado'}
          opcoes={alimentadores.map((a) => ({ valor: String(a.id), label: a.nome }))}
          valor={alimentadorId}
          aoAlterar={(valor) => {
            setAlimentadorId(valor)
            setResultado(null)
          }}
        />
        <div>
          <label htmlFor="perdas-de" className="mb-2 block text-sm font-medium text-slate-300">De</label>
          <input id="perdas-de" type="date" value={de} onChange={(e) => setDe(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
        <div>
          <label htmlFor="perdas-ate" className="mb-2 block text-sm font-medium text-slate-300">Até</label>
          <input id="perdas-ate" type="date" value={ate} onChange={(e) => setAte(e.target.value)} className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none focus:border-blue-500" />
        </div>
        <div className="flex items-end">
          <button
            type="submit"
            disabled={calculando}
            className="w-full rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:opacity-60"
          >
            {calculando ? 'Calculando...' : 'Calcular perdas'}
          </button>
        </div>
      </form>

      {erro && <p className="mt-3 text-sm text-red-400">{erro}</p>}

      {usuario?.perfil === 'GERENTE_OPERACOES' && alimentadorId && <ParametroPerda alimentadorId={Number(alimentadorId)} />}

      {resultado && situacao && (
        <div className="mt-6" aria-label="Resultado das perdas técnicas">
          <p className={`flex items-center gap-1.5 text-sm font-medium ${situacao.classe}`}>
            <situacao.icone className="h-4 w-4" />
            {situacao.rotulo}
            {resultado.situacao === 'DADOS_INSUFICIENTES' && (
              <span className="font-normal text-slate-400">
                · as medições cobrem {formatarPercentual(resultado.percentualCobertura)} do período, com lacuna ou sobreposição
              </span>
            )}
          </p>

          <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Indicador rotulo="Energia injetada" valor={mwh(resultado.totalEnergiaInjetadaMwh)} />
            <Indicador rotulo="Perda estimada" valor={mwh(resultado.totalPerdaEstimadaMwh)} />
            <Indicador rotulo="Percentual ponderado" valor={resultado.percentualPonderado === null ? '—' : formatarPercentual(resultado.percentualPonderado)} />
            <Indicador rotulo="Cobertura do período" valor={formatarPercentual(resultado.percentualCobertura)} />
          </div>

          <h4 className="mt-6 text-sm font-medium text-slate-200">Medições consideradas</h4>
          {resultado.medicoesConsideradas.length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">Nenhuma medição de energia no período.</p>
          ) : (
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-800 text-left text-slate-400">
                    <th className="pb-2 font-normal">Intervalo</th>
                    <th className="pb-2 font-normal">Fonte</th>
                    <th className="pb-2 font-normal">Energia injetada</th>
                    <th className="pb-2 font-normal">Percentual de referência</th>
                    <th className="pb-2 font-normal">Recebida em</th>
                  </tr>
                </thead>
                <tbody>
                  {resultado.medicoesConsideradas.map((m) => (
                    <tr key={m.id} className="border-b border-slate-800/60 last:border-0">
                      <td className="py-2 pr-4 text-slate-300">{formatarDataHora(m.inicioPeriodo)} a {formatarDataHora(m.fimPeriodo)}</td>
                      <td className="py-2 pr-4 text-slate-400">#{m.fonteId} · {m.origem === 'SIMULADA' ? 'Simulada' : 'Real'}</td>
                      <td className="py-2 pr-4 text-slate-300">{formatarNumero(m.energiaInjetadaMwh)} MWh</td>
                      <td className="py-2 pr-4 text-slate-300">{formatarPercentual(m.percentualPerdaReferencia)}</td>
                      <td className="py-2 text-slate-400">{formatarDataHora(m.recebidoEm)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}

export default PerdasTecnicas
