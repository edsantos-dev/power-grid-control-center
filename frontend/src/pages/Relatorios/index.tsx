import PerdasTecnicas from './PerdasTecnicas'

function Relatorios() {
  return (
    <div>
      <div className="mb-8">
        <h2 className="text-3xl font-bold text-white">
          Relatórios
        </h2>

        <p className="mt-2 text-slate-400">
          Consultas calculadas a partir das medições registradas.
        </p>
      </div>

      <PerdasTecnicas />
    </div>
  )
}

export default Relatorios
