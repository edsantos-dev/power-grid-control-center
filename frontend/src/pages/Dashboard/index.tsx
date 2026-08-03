function Dashboard() {
  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold">
          Dashboard
        </h2>

        <p className="mt-2 text-slate-400">
          Visão geral da operação da rede elétrica.
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <p className="text-sm text-slate-400">
            Subestações
          </p>

          <p className="mt-2 text-3xl font-bold">
            24
          </p>

          <p className="mt-2 text-sm text-green-400">
            Operacionais
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <p className="text-sm text-slate-400">
            Transformadores
          </p>

          <p className="mt-2 text-3xl font-bold">
            48
          </p>

          <p className="mt-2 text-sm text-green-400">
            Funcionamento normal
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <p className="text-sm text-slate-400">
            Alertas ativos
          </p>

          <p className="mt-2 text-3xl font-bold text-yellow-400">
            12
          </p>

          <p className="mt-2 text-sm text-yellow-400">
            Requer atenção
          </p>
        </div>

        <div className="rounded-xl border border-slate-800 bg-slate-900 p-6">
          <p className="text-sm text-slate-400">
            Falhas críticas
          </p>

          <p className="mt-2 text-3xl font-bold text-red-500">
            2
          </p>

          <p className="mt-2 text-sm text-red-400">
            Ação necessária
          </p>
        </div>
      </div>

      <div className="mt-6 rounded-xl border border-slate-800 bg-slate-900 p-6">
        <h3 className="text-lg font-semibold">
          Estado geral da rede
        </h3>

        <div className="mt-6 flex items-center gap-3">
          <div className="h-3 w-3 rounded-full bg-green-500" />

          <span className="text-slate-300">
            Sistema operacional
          </span>
        </div>
      </div>
    </div>
  )
}

export default Dashboard