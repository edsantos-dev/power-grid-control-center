import { useState } from 'react'
import { Search, MapPin, Wifi, WifiOff } from 'lucide-react'

type StatusSmartmeter = 'online' | 'alerta' | 'offline'

interface Smartmeter {
  id: string
  codigo: string
  consumidor: string
  alimentador: string
  consumoAtual: number
  ultimaLeitura: string
  status: StatusSmartmeter
}

const smartmeters: Smartmeter[] = [
  { id: 'M-101', codigo: 'M-101', consumidor: 'Residencial - Rua das Palmeiras, 120', alimentador: 'Alimentador Norte-1', consumoAtual: 2.4, ultimaLeitura: 'há 2 min', status: 'online' },
  { id: 'M-102', codigo: 'M-102', consumidor: 'Comercial - Av. Central, 850', alimentador: 'Alimentador Central-1', consumoAtual: 18.7, ultimaLeitura: 'há 3 min', status: 'online' },
  { id: 'M-231', codigo: 'M-231', consumidor: 'Residencial - Rua Bela Vista, 45', alimentador: 'Alimentador Central-1', consumoAtual: 0, ultimaLeitura: 'há 41 min', status: 'offline' },
  { id: 'M-104', codigo: 'M-104', consumidor: 'Industrial - Distrito Sul, 12', alimentador: 'Alimentador Sul-1', consumoAtual: 145.2, ultimaLeitura: 'há 1 min', status: 'alerta' },
  { id: 'M-105', codigo: 'M-105', consumidor: 'Residencial - Rua Girassol, 78', alimentador: 'Alimentador Leste-1', consumoAtual: 1.8, ultimaLeitura: 'há 4 min', status: 'online' },
  { id: 'M-106', codigo: 'M-106', consumidor: 'Comercial - Praça Oeste, 200', alimentador: 'Alimentador Vale Verde-1', consumoAtual: 9.3, ultimaLeitura: 'há 2 min', status: 'online' },
  { id: 'M-107', codigo: 'M-107', consumidor: 'Residencial - Rua Porto Alto, 33', alimentador: 'Alimentador Porto Alto-1', consumoAtual: 0, ultimaLeitura: 'há 2h', status: 'offline' },
]

const statusStyles: Record<StatusSmartmeter, { dot: string; text: string; label: string; icon: typeof Wifi }> = {
  online: { dot: 'bg-green-500', text: 'text-green-400', label: 'Online', icon: Wifi },
  alerta: { dot: 'bg-yellow-500', text: 'text-yellow-400', label: 'Consumo elevado', icon: Wifi },
  offline: { dot: 'bg-red-500', text: 'text-red-400', label: 'Offline', icon: WifiOff },
}

function Smartmeters() {
  const [filtroStatus, setFiltroStatus] = useState<StatusSmartmeter | 'todos'>('todos')
  const [busca, setBusca] = useState('')

  const smartmetersFiltrados = smartmeters.filter((m) => {
    const passaStatus = filtroStatus === 'todos' || m.status === filtroStatus
    const passaBusca =
      m.codigo.toLowerCase().includes(busca.toLowerCase()) ||
      m.consumidor.toLowerCase().includes(busca.toLowerCase())
    return passaStatus && passaBusca
  })

  const totalOnline = smartmeters.filter((m) => m.status === 'online').length
  const totalAlerta = smartmeters.filter((m) => m.status === 'alerta').length
  const totalOffline = smartmeters.filter((m) => m.status === 'offline').length
  const consumoTotal = smartmeters.reduce((acc, m) => acc + m.consumoAtual, 0)

  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Smartmeters
        </h2>

        <p className="mt-2 text-slate-400">
          Medidores inteligentes instalados na rede de distribuição.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Online</p>
          <p className="mt-2 text-3xl font-bold text-green-400">{totalOnline}</p>
          <p className="mt-2 text-sm text-green-400">de {smartmeters.length} medidores</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Consumo elevado</p>
          <p className="mt-2 text-3xl font-bold text-yellow-400">{totalAlerta}</p>
          <p className="mt-2 text-sm text-yellow-400">Acima do esperado</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Offline</p>
          <p className="mt-2 text-3xl font-bold text-red-500">{totalOffline}</p>
          <p className="mt-2 text-sm text-red-400">Perda de comunicação</p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6 transition hover:border-slate-700">
          <p className="text-sm text-slate-400">Consumo Total</p>
          <p className="mt-2 text-3xl font-bold text-white">
            {consumoTotal.toFixed(1)} <span className="text-lg font-normal text-slate-400">kWh</span>
          </p>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="relative w-full sm:max-w-xs">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
            <input
              type="text"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Buscar medidor ou consumidor..."
              className="w-full rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 placeholder:text-slate-500 focus:border-slate-600 focus:outline-none"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            {(['todos', 'online', 'alerta', 'offline'] as const).map((status) => (
              <button
                key={status}
                onClick={() => setFiltroStatus(status)}
                className={`rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                  filtroStatus === status
                    ? 'border-slate-600 bg-slate-800 text-white'
                    : 'border-slate-800 text-slate-400 hover:border-slate-700'
                }`}
              >
                {status === 'todos' ? 'Todos' : statusStyles[status].label}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-6 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-800 text-left text-slate-400">
                <th className="pb-3 font-normal">Medidor</th>
                <th className="pb-3 font-normal">Consumidor</th>
                <th className="pb-3 font-normal">Alimentador</th>
                <th className="pb-3 font-normal">Consumo Atual</th>
                <th className="pb-3 font-normal">Última Leitura</th>
                <th className="pb-3 font-normal">Status</th>
              </tr>
            </thead>
            <tbody>
              {smartmetersFiltrados.map((m) => {
                const status = statusStyles[m.status]
                const Icon = status.icon

                return (
                  <tr key={m.id} className="border-b border-slate-800/60 last:border-0">
                    <td className="py-3 pr-4 text-slate-200">{m.codigo}</td>
                    <td className="py-3 text-slate-400">
                      <span className="flex items-center gap-1.5">
                        <MapPin className="h-3.5 w-3.5 text-slate-500" />
                        {m.consumidor}
                      </span>
                    </td>
                    <td className="py-3 text-slate-300">{m.alimentador}</td>
                    <td className="py-3 text-slate-300">{m.consumoAtual} kWh</td>
                    <td className="py-3 text-slate-400">{m.ultimaLeitura}</td>
                    <td className="py-3">
                      <span className={`flex items-center gap-1.5 text-xs font-medium ${status.text}`}>
                        <Icon className="h-3.5 w-3.5" />
                        {status.label}
                      </span>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>

          {smartmetersFiltrados.length === 0 && (
            <div className="flex flex-col items-center gap-2 py-10 text-slate-500">
              <p className="text-sm">Nenhum medidor encontrado.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

export default Smartmeters