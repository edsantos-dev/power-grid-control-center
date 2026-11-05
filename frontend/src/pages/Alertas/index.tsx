import { useEffect, useState } from 'react'
import { Plus } from 'lucide-react'
import { listarCatalogoAtivos, type AtivoCatalogo } from '../../services/ativos'
import AlertaForm from './AlertaForm'
import AlertasSobrecarga from './AlertasSobrecarga'
import AlertasTensao from './AlertasTensao'
import AlertasComunicacao from './AlertasComunicacao'
import FilaAlertasAtivos from './FilaAlertasAtivos'
import HistoricoAlertas from './HistoricoAlertas'

function Alertas() {
  const [ativos, setAtivos] = useState<AtivoCatalogo[]>([])
  const [formularioAberto, setFormularioAberto] = useState(false)
  const [aba, setAba] = useState<'ativos' | 'historico'>('ativos')
  // A fila e as seções por origem mostram os mesmos alertas: uma ação em uma atualiza a outra
  const [versaoFila, setVersaoFila] = useState(0)
  const [versaoSecoes, setVersaoSecoes] = useState(0)
  const atualizarFila = () => setVersaoFila((atual) => atual + 1)

  // O catálogo só nomeia os ativos: uma falha nele não impede a fila
  useEffect(() => {
    listarCatalogoAtivos().then(setAtivos)
  }, [])

  function handleSalvar() {
    setFormularioAberto(false)
    // A fila busca o novo alerta na posição definida pelo backend
    atualizarFila()
  }

  return (
    <div>
      <div className="mb-8 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-white">
            Alertas
          </h2>

          <p className="mt-2 text-slate-400">
            Fila única dos alertas ativos de todas as origens, alertas automáticos por origem e histórico.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setFormularioAberto(true)}
          className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500"
        >
          <Plus className="h-4 w-4" />
          Registrar alerta
        </button>
      </div>

      <div className="mb-6 flex gap-2 border-b border-slate-800" role="tablist" aria-label="Visão dos alertas">
        {(['ativos', 'historico'] as const).map((valor) => (
          <button
            key={valor}
            type="button"
            role="tab"
            aria-selected={aba === valor}
            onClick={() => setAba(valor)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition ${
              aba === valor ? 'border-blue-500 text-white' : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            {valor === 'ativos' ? 'Ativos' : 'Histórico'}
          </button>
        ))}
      </div>

      {aba === 'historico' ? (
        <HistoricoAlertas ativos={ativos} />
      ) : (
        <>
          <FilaAlertasAtivos ativos={ativos} versao={versaoFila} aoAlterar={() => setVersaoSecoes((atual) => atual + 1)} />
          <div key={versaoSecoes}>
            <AlertasSobrecarga ativos={ativos} aoAlterar={atualizarFila} />
            <AlertasTensao ativos={ativos} aoAlterar={atualizarFila} />
            <AlertasComunicacao ativos={ativos} aoAlterar={atualizarFila} />
          </div>
        </>
      )}

      {formularioAberto && (
        <AlertaForm ativos={ativos} aoFechar={() => setFormularioAberto(false)} aoSalvar={handleSalvar} />
      )}
    </div>
  )
}

export default Alertas
