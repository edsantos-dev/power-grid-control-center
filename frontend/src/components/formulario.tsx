import type { FormEvent, ReactNode } from 'react'
import { X } from 'lucide-react'

type FormularioModalProps = {
  titulo: string
  erro: string
  enviando: boolean
  aoFechar: () => void
  aoEnviar: (e: FormEvent<HTMLFormElement>) => void
  children: ReactNode
}

type CampoProps = {
  id: string
  label: string
  valor: string
  aoAlterar: (valor: string) => void
  erro?: string
  className?: string
}

type CampoTextoProps = CampoProps & {
  type?: string
  decimal?: boolean
  placeholder?: string
}

type CampoSelectProps = CampoProps & {
  opcoes: { valor: string; label: string }[]
  placeholder?: string
}

const classeCampo =
  'w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-2.5 text-white outline-none placeholder:text-slate-500 focus:border-blue-500'

export function FormularioModal({ titulo, erro, enviando, aoFechar, aoEnviar, children }: FormularioModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/80 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className="w-full max-w-2xl rounded-xl border border-slate-800 bg-slate-900 p-6"
      >
        <div className="mb-6 flex items-center justify-between">
          <h3 className="text-xl font-bold text-white">{titulo}</h3>

          <button
            type="button"
            onClick={aoFechar}
            aria-label="Fechar"
            className="rounded-lg p-1 text-slate-400 transition hover:bg-slate-800 hover:text-white"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={aoEnviar} noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            {children}
          </div>

          {erro && (
            <p className="mt-4 text-sm text-red-400">{erro}</p>
          )}

          <div className="mt-6 flex justify-end gap-3">
            <button
              type="button"
              onClick={aoFechar}
              className="rounded-lg border border-slate-700 px-4 py-2 text-sm text-slate-300 transition hover:bg-slate-800 hover:text-white"
            >
              Cancelar
            </button>

            <button
              type="submit"
              disabled={enviando}
              className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {enviando ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export function CampoTexto({ id, label, valor, aoAlterar, erro, className, type = 'text', decimal, placeholder }: CampoTextoProps) {
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-2 block text-sm font-medium text-slate-300">
        {label}
      </label>

      <input
        id={id}
        type={type}
        inputMode={decimal ? 'decimal' : undefined}
        placeholder={placeholder}
        value={valor}
        onChange={(e) => aoAlterar(e.target.value)}
        className={classeCampo}
      />

      {erro && (
        <p className="mt-1 text-xs text-red-400">{erro}</p>
      )}
    </div>
  )
}

export function CampoSelect({ id, label, valor, aoAlterar, erro, className, opcoes, placeholder }: CampoSelectProps) {
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-2 block text-sm font-medium text-slate-300">
        {label}
      </label>

      <select id={id} value={valor} onChange={(e) => aoAlterar(e.target.value)} className={classeCampo}>
        {placeholder && <option value="">{placeholder}</option>}
        {opcoes.map((opcao) => (
          <option key={opcao.valor} value={opcao.valor}>{opcao.label}</option>
        ))}
      </select>

      {erro && (
        <p className="mt-1 text-xs text-red-400">{erro}</p>
      )}
    </div>
  )
}
