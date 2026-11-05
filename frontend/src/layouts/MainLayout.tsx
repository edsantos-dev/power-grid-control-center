// src/layouts/MainLayout.tsx
import { Outlet } from 'react-router-dom'
import Header from '../components/layout/Header'
import Sidebar from '../components/layout/Sidebar'

function MainLayout() {
  return (
    // Só o conteúdo rola: menu e cabeçalho ficam fixos na altura da janela
    <div className="flex h-screen overflow-hidden bg-slate-950 text-white">
      <Sidebar />

      <div className="flex min-w-0 flex-1 flex-col">
        <Header />

        <main className="min-h-0 flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default MainLayout