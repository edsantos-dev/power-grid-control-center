import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import RotaPorPerfil from './RotaPorPerfil'
import RotaPrivada from './RotaPrivada'
import Login from '../pages/Login'
import Dashboard from '../pages/Dashboard'
import Monitoramento from '../pages/Monitoramento'
import MainLayout from '../layouts/MainLayout'
import Alertas from '../pages/Alertas'
import Eventos from '../pages/Eventos'
import Usinas from '../pages/Usinas'
import Subestacoes from '../pages/Subestacoes'
import Transformadores from '../pages/Transformadores'
import Linhas from '../pages/Linhas'
import Alimentadores from '../pages/Alimentadores'
import SmartMeters from '../pages/SmartMeters'
import Consumidores from '../pages/Consumidores'
import Equipes from '../pages/Equipes'
import Relatorios from '../pages/Relatorios'

function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/login" element={<Login />} />

        <Route element={<RotaPrivada />}>
          <Route element={<MainLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/monitoramento" element={<Monitoramento />} />
            <Route path="/alertas" element={<Alertas />} />
            <Route path="/eventos" element={<Eventos />} />
            <Route path="/subestacoes" element={<Subestacoes />} />
            <Route path="/transformadores" element={<Transformadores />} />
            <Route path="/linhas" element={<Linhas />} />
            <Route path="/alimentadores" element={<Alimentadores />} />
            <Route path="/smartmeters" element={<SmartMeters />} />
            <Route path="/consumidores" element={<Consumidores />} />
            <Route path="/equipes" element={<Equipes />} />

            {/* Telas restritas ao gerente: os demais perfis voltam ao dashboard */}
            <Route element={<RotaPorPerfil />}>
              <Route path="/usinas" element={<Usinas />} />
              <Route path="/relatorios" element={<Relatorios />} />
            </Route>
          </Route>
        </Route>

        {/* Rotas desconhecidas, como a antiga /configuracoes, voltam ao início */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default AppRoutes