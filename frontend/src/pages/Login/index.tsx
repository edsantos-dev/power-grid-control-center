import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import axios from 'axios'
import { Zap } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'

function Login() {
  const navigate = useNavigate()
  const { usuario, login } = useAuth()

  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [enviando, setEnviando] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setErro('')

    // Validação simples de e-mail
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (!email.trim() || !senha.trim()) {
      setErro('Preencha e-mail e senha.')
      return
    }

    if (!emailRegex.test(email.trim())) {
      setErro('Informe um e-mail válido.')
      return
    }

    setEnviando(true)

    try {
      await login(email, senha)
      navigate('/dashboard', { replace: true })
    } catch (error) {
      setErro(
        axios.isAxiosError(error) && error.response?.status === 401
          ? 'E-mail ou senha inválidos.'
          : 'Não foi possível conectar ao servidor. Tente novamente.'
      )
    } finally {
      setEnviando(false)
    }
  }

  if (usuario) {
    return <Navigate to="/dashboard" replace />
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950 px-4">
      <div className="w-full max-w-md rounded-2xl border border-slate-800 bg-slate-900 p-8 shadow-2xl">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-xl bg-blue-600">
            <Zap className="h-7 w-7 text-white" />
          </div>

          <h1 className="text-2xl font-bold text-white">
            Power Grid
          </h1>

          <p className="mt-2 text-sm text-slate-400">
            Control Center
          </p>
        </div>

        <form className="space-y-5" onSubmit={handleSubmit} noValidate>
          <div>
            <label className="mb-2 block text-sm font-medium text-slate-300">
              E-mail
            </label>

            <input
              type="email"
              placeholder="operador@empresa.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-white outline-none placeholder:text-slate-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label className="mb-2 block text-sm font-medium text-slate-300">
              Senha
            </label>

            <input
              type="password"
              placeholder="Digite sua senha"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-800 px-4 py-3 text-white outline-none placeholder:text-slate-500 focus:border-blue-500"
            />
          </div>

          {erro && (
            <p className="text-sm text-red-400">
              {erro}
            </p>
          )}

          <button
            type="submit"
            disabled={enviando}
            className="w-full rounded-lg bg-blue-600 px-4 py-3 font-semibold text-white transition hover:bg-blue-500 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {enviando ? 'Entrando...' : 'Entrar no sistema'}
          </button>
        </form>

        <p className="mt-6 text-center text-xs text-slate-600">
          Acesso restrito aos operadores autorizados
        </p>
      </div>
    </main>
  )
}

export default Login