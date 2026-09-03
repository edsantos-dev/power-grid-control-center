import { BrowserRouter, Route, Routes } from 'react-router-dom'
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

function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/login" element={<Login />} />

        <Route element={<MainLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/monitoramento" element={<Monitoramento />} />
          <Route path="/alertas" element={<Alertas />} />
          <Route path="/eventos" element={<Eventos />} />
          <Route path="/usinas" element={<Usinas />} />
          <Route path="/subestacoes" element={<Subestacoes />} />
          <Route path="/transformadores" element={<Transformadores />} />
          <Route path="/linhas" element={<Linhas />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default AppRoutes