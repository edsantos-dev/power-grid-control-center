import type { AreaMapa, TipoAtivoMapa } from '../../types'

export const MUNDO: AreaMapa = { minLat: -90, maxLat: 90, minLng: -180, maxLng: 180 }

// Slots categóricos 1-3 da paleta de referência, validados contra a superfície escura
export const CAMADAS: { tipo: TipoAtivoMapa; rotulo: string; cor: string; rota: string }[] = [
  { tipo: 'SUBESTACAO', rotulo: 'Subestações', cor: '#3987e5', rota: '/subestacoes' },
  { tipo: 'TRANSFORMADOR', rotulo: 'Transformadores', cor: '#d95926', rota: '/transformadores' },
  { tipo: 'USINA', rotulo: 'Usinas', cor: '#199e70', rota: '/usinas' },
  { tipo: 'LINHA', rotulo: 'Linhas (conexão simplificada)', cor: '#94a3b8', rota: '/linhas' },
]

export const camada = (tipo: TipoAtivoMapa) => CAMADAS.find((c) => c.tipo === tipo)!

const limitar = (valor: number, limite: number) => Math.max(-limite, Math.min(limite, Number(valor.toFixed(6))))

// O backend exige uma área válida; o mapa pode exibir além dos limites geográficos
export function areaDosLimites(sul: number, oeste: number, norte: number, leste: number): AreaMapa {
  return { minLat: limitar(sul, 90), maxLat: limitar(norte, 90), minLng: limitar(oeste, 180), maxLng: limitar(leste, 180) }
}
