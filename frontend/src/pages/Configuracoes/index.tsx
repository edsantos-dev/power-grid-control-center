import { useState } from 'react'
import { User, Bell, Shield, Database, Save } from 'lucide-react'

function Configuracoes() {
  const [nome, setNome] = useState('Operador do Sistema')
  const [email, setEmail] = useState('operador@energia.com.br')
  const [cargo, setCargo] = useState('Operador de Centro de Controle')

  const [alertasCriticos, setAlertasCriticos] = useState(true)
  const [alertasAtencao, setAlertasAtencao] = useState(true)
  const [notificacoesEmail, setNotificacoesEmail] = useState(false)
  const [notificacoesSom, setNotificacoesSom] = useState(true)

  const [intervaloAtualizacao, setIntervaloAtualizacao] = useState('30')
  const [retencaoDados, setRetencaoDados] = useState('90')

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Configurações
        </h2>

        <p className="mt-2 text-slate-400">
          Preferências da conta, alertas e parâmetros do sistema.
        </p>
      </div>

      <div className="grid gap-6 xl:grid-cols-2">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <div className="flex items-center gap-2">
            <User className="h-4 w-4 text-blue-400" />
            <h3 className="text-lg font-semibold text-white">Perfil</h3>
          </div>

          <div className="mt-5 space-y-4">
            <div>
              <label className="text-xs text-slate-400">Nome</label>
              <input
                type="text"
                value={nome}
                onChange={(e) => setNome(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400">E-mail</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              />
            </div>

            <div>
              <label className="text-xs text-slate-400">Cargo</label>
              <input
                type="text"
                value={cargo}
                onChange={(e) => setCargo(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              />
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <div className="flex items-center gap-2">
            <Bell className="h-4 w-4 text-yellow-400" />
            <h3 className="text-lg font-semibold text-white">Notificações</h3>
          </div>

          <div className="mt-5 space-y-4">
            <label className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Alertas críticos</span>
              <input
                type="checkbox"
                checked={alertasCriticos}
                onChange={(e) => setAlertasCriticos(e.target.checked)}
                className="h-4 w-4 accent-blue-500"
              />
            </label>

            <label className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Alertas de atenção</span>
              <input
                type="checkbox"
                checked={alertasAtencao}
                onChange={(e) => setAlertasAtencao(e.target.checked)}
                className="h-4 w-4 accent-blue-500"
              />
            </label>

            <label className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Notificações por e-mail</span>
              <input
                type="checkbox"
                checked={notificacoesEmail}
                onChange={(e) => setNotificacoesEmail(e.target.checked)}
                className="h-4 w-4 accent-blue-500"
              />
            </label>

            <label className="flex items-center justify-between">
              <span className="text-sm text-slate-300">Som ao receber alerta</span>
              <input
                type="checkbox"
                checked={notificacoesSom}
                onChange={(e) => setNotificacoesSom(e.target.checked)}
                className="h-4 w-4 accent-blue-500"
              />
            </label>
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <div className="flex items-center gap-2">
            <Database className="h-4 w-4 text-green-400" />
            <h3 className="text-lg font-semibold text-white">Sistema</h3>
          </div>

          <div className="mt-5 space-y-4">
            <div>
              <label className="text-xs text-slate-400">Intervalo de atualização (segundos)</label>
              <select
                value={intervaloAtualizacao}
                onChange={(e) => setIntervaloAtualizacao(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="10">10 segundos</option>
                <option value="30">30 segundos</option>
                <option value="60">1 minuto</option>
                <option value="300">5 minutos</option>
              </select>
            </div>

            <div>
              <label className="text-xs text-slate-400">Retenção de dados históricos (dias)</label>
              <select
                value={retencaoDados}
                onChange={(e) => setRetencaoDados(e.target.value)}
                className="mt-1.5 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-200 focus:border-slate-600 focus:outline-none"
              >
                <option value="30">30 dias</option>
                <option value="90">90 dias</option>
                <option value="180">180 dias</option>
                <option value="365">1 ano</option>
              </select>
            </div>
          </div>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <div className="flex items-center gap-2">
            <Shield className="h-4 w-4 text-red-400" />
            <h3 className="text-lg font-semibold text-white">Segurança</h3>
          </div>

          <div className="mt-5 space-y-4">
            <button className="w-full rounded-lg border border-slate-700 px-4 py-2 text-left text-sm text-slate-300 transition hover:border-slate-600 hover:text-white">
              Alterar senha
            </button>

            <button className="w-full rounded-lg border border-slate-700 px-4 py-2 text-left text-sm text-slate-300 transition hover:border-slate-600 hover:text-white">
              Configurar autenticação em duas etapas
            </button>

            <button className="w-full rounded-lg border border-red-900 px-4 py-2 text-left text-sm text-red-400 transition hover:border-red-700 hover:text-red-300">
              Encerrar todas as sessões ativas
            </button>
          </div>
        </div>
      </div>

      <div className="mt-6 flex justify-end">
        <button className="flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white transition hover:bg-blue-500">
          <Save className="h-4 w-4" />
          Salvar alterações
        </button>
      </div>
    </div>
  )
}

export default Configuracoes