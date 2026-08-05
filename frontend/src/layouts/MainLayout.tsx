// src/layouts/MainLayout.tsx
import { Outlet } from 'react-router-dom'
import Sidebar from '../components/layout/Sidebar'

function MainLayout() {
  return (
    <div className="flex min-h-screen bg-slate-950 text-white">
      <Sidebar />

      <div className="flex flex-1 flex-col">
        <header className="border-b border-slate-800 bg-slate-900 px-6 py-4">
          <h1 className="text-xl font-bold">
            Power Grid Control Center
          </h1>
        </header>

        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default MainLayout