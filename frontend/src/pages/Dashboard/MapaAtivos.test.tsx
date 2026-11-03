import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as mapaApi from '../../services/mapa'
import type { ColecaoMapa } from '../../types'
import MapaAtivos from './MapaAtivos'
import { areaDosLimites, MUNDO } from './mapa'

vi.mock('../../services/mapa', async (original) => ({ ...(await original<typeof mapaApi>()), consultarMapa: vi.fn() }))
vi.mock('leaflet/dist/leaflet.css', () => ({}))

const ajustar = vi.fn()
let moveend: (() => void) | undefined

// O Leaflet depende de layout real; os componentes viram elementos simples e clicáveis
vi.mock('react-leaflet', () => ({
  MapContainer: ({ children }: { children: ReactNode }) => <div data-testid="mapa">{children}</div>,
  TileLayer: () => null,
  Tooltip: () => null,
  CircleMarker: ({ children, eventHandlers, pathOptions }: { children: ReactNode; eventHandlers: { click: () => void }; pathOptions: { fillColor: string } }) => (
    <button type="button" data-cor={pathOptions.fillColor} onClick={eventHandlers.click}>ponto{children}</button>
  ),
  Polyline: ({ eventHandlers, pathOptions }: { eventHandlers: { click: () => void }; pathOptions: { dashArray: string } }) => (
    <button type="button" data-tracejado={pathOptions.dashArray} onClick={eventHandlers.click}>linha</button>
  ),
  useMap: () => ({ fitBounds: ajustar }),
  useMapEvents: (eventos: { moveend: () => void }) => {
    moveend = eventos.moveend
    return { getBounds: () => ({ getSouth: () => -26, getWest: () => -50, getNorth: () => -25, getEast: () => -49 }) }
  },
}))

const colecao: ColecaoMapa = {
  type: 'FeatureCollection',
  features: [
    { type: 'Feature', id: '1', geometry: { type: 'Point', coordinates: [-49.27, -25.43] }, properties: { ativoId: 1, tipoAtivo: 'SUBESTACAO', nome: 'SE Norte', estadoOperacional: 'OPERACIONAL', detalheUri: '/api/subestacoes/1' } },
    { type: 'Feature', id: '3', geometry: { type: 'Point', coordinates: [-49.3, -25.5] }, properties: { ativoId: 3, tipoAtivo: 'TRANSFORMADOR', nome: 'T-01', estadoOperacional: 'FALHA', detalheUri: '/api/transformadores/3' } },
    {
      type: 'Feature',
      id: '5',
      geometry: { type: 'LineString', coordinates: [[-49.27, -25.43], [-49.4, -25.6]] },
      properties: { ativoId: 5, tipoAtivo: 'LINHA', nome: 'LT Norte-Sul', estadoOperacional: 'OPERACIONAL', detalheUri: '/api/linhas-transmissao/5', subestacaoOrigemId: 1, subestacaoDestinoId: 2, tipoRepresentacao: 'CONEXAO_SIMPLIFICADA' },
    },
  ],
  metadados: { totalFeatures: 3, totalAtivosSemGeometria: 2 },
}

function renderizar() {
  render(
    <MemoryRouter>
      <MapaAtivos />
    </MemoryRouter>
  )
}

const avancar = () => act(async () => vi.advanceTimersByTime(300))

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers({ shouldAdvanceTime: true })
  vi.mocked(mapaApi.consultarMapa).mockResolvedValue(colecao)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('mapa de ativos', () => {
  it('consulta o mundo primeiro com todas as camadas e enquadra os ativos', async () => {
    renderizar()
    await avancar()

    expect(await screen.findByRole('status')).toHaveTextContent('3 ativo(s) na área visível · 2 sem coordenadas não aparecem no mapa')
    expect(mapaApi.consultarMapa).toHaveBeenCalledWith(MUNDO, ['SUBESTACAO', 'TRANSFORMADOR', 'USINA', 'LINHA'])
    expect(ajustar).toHaveBeenCalledTimes(1)
  })

  it('representa pontos por tipo e linhas como conexao tracejada', async () => {
    renderizar()
    await avancar()

    const pontos = await screen.findAllByRole('button', { name: /ponto/ })
    expect(pontos.map((p) => p.dataset.cor)).toEqual(['#3987e5', '#d95926'])
    expect(screen.getByRole('button', { name: 'linha' }).dataset.tracejado).toBe('6 6')
  })

  it('identifica o ativo selecionado com tipo, ID, estado e cadastro', async () => {
    renderizar()
    await avancar()

    await userEvent.click((await screen.findAllByRole('button', { name: /ponto/ }))[1])
    const painel = screen.getByRole('complementary', { name: 'Ativo selecionado' })
    expect(painel).toHaveTextContent('Transformadores · #3')
    expect(painel).toHaveTextContent('T-01')
    expect(painel).toHaveTextContent('Falha')
    expect(painel).toHaveTextContent('/api/transformadores/3')
    expect(within(painel).getByRole('link', { name: 'Abrir cadastro de transformadores' })).toHaveAttribute('href', '/transformadores')

    await userEvent.click(screen.getByRole('button', { name: 'linha' }))
    expect(painel).toHaveTextContent('Conexão simplificada entre as subestações #1 e #2.')
  })

  it('consulta a area visivel ao mover e filtra camadas', async () => {
    renderizar()
    await avancar()
    await screen.findByRole('status')

    act(() => moveend?.())
    await avancar()
    expect(mapaApi.consultarMapa).toHaveBeenLastCalledWith(areaDosLimites(-26, -50, -25, -49), expect.any(Array))

    await userEvent.click(screen.getByLabelText('Transformadores'))
    await avancar()
    expect(vi.mocked(mapaApi.consultarMapa).mock.calls.at(-1)?.[1]).toEqual(['SUBESTACAO', 'USINA', 'LINHA'])
  })

  it('orienta quando a area excede o limite, sem truncar', async () => {
    const config = { headers: new AxiosHeaders() } as InternalAxiosRequestConfig
    vi.mocked(mapaApi.consultarMapa).mockRejectedValue(new AxiosError('erro', undefined, config, undefined, { config, status: 422, data: {}, statusText: '', headers: {} }))

    renderizar()
    await avancar()

    expect(await screen.findByText('Há mais de 1000 ativos nesta área. Aproxime o mapa ou desmarque camadas.')).toBeInTheDocument()
    expect(screen.queryAllByRole('button', { name: /ponto/ })).toHaveLength(0)
  })

  it('limita a area aos intervalos geograficos aceitos', () => {
    expect(areaDosLimites(-95, -200.1234567, 91, 190)).toEqual({ minLat: -90, maxLat: 90, minLng: -180, maxLng: 180 })
  })
})
