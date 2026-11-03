import type { ReactNode } from 'react'
import { X } from 'lucide-react'

type Props = {
  titulo: string
  aoFechar: () => void
  largura?: string
  children: ReactNode
}

function Modal({ titulo, aoFechar, largura = 'max-w-2xl', children }: Props) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/80 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        className={`w-full ${largura} rounded-xl border border-slate-800 bg-slate-900 p-6`}
      >
        <div className="mb-6 flex items-center justify-between gap-4">
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

        {children}
      </div>
    </div>
  )
}

export default Modal
