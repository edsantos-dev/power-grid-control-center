import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import axios from 'axios'
import 'leaflet/dist/leaflet.css'
import { extrairErroApi } from '../../services/api'
import { consultarMapa, LIMITE_FEATURES } from '../../services/mapa'
import type { AreaMapa, ColecaoMapa, FeatureMapa, TipoAtivoMapa } from '../../types'
import { ESTADO_STYLES } from '../../utils/constants'
import { areaDosLimites, camada, CAMADAS, MUNDO } from './mapa'

function AcompanharArea({ aoMudar }: { aoMudar: (area: AreaMapa) => void }) {
  const mapa = useMapEvents({
    moveend: () => {
      const limites = mapa.getBounds()
      aoMudar(areaDosLimites(limites.getSouth(), limites.getWest(), limites.getNorth(), limites.getEast()))
    },
  })

  // A primeira consulta cobre o mundo para enquadrar todos os ativos com coordenadas
  useEffect(() => {
    aoMudar(MUNDO)
  }, [aoMudar])

  return null
}

function Enquadrar({ features }: { features: FeatureMapa[] }) {
  const mapa = useMap()
  const enquadrado = useRef(false)

  useEffect(() => {
    if (enquadrado.current || features.length === 0) return
    const pontos = features.flatMap((f) =>
      f.geometry.type === 'Point' ? [f.geometry.coordinates] : f.geometry.coordinates
    )
    enquadrado.current = true
    mapa.fitBounds(pontos.map(([lng, lat]) => [lat, lng] as [number, number]), { padding: [32, 32], maxZoom: 13 })
  }, [features, mapa])

  return null
}

function MapaAtivos() {
  const [tipos, setTipos] = useState<TipoAtivoMapa[]>(CAMADAS.map((c) => c.tipo))
  const [area, setArea] = useState<AreaMapa | null>(null)
  const [colecao, setColecao] = useState<ColecaoMapa | null>(null)
  const [selecionado, setSelecionado] = useState<FeatureMapa | null>(null)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const aoMudarArea = useCallback((nova: AreaMapa) => setArea(nova), [])

  useEffect(() => {
    if (!area) return
    if (tipos.length === 0) {
      setColecao({ type: 'FeatureCollection', features: [], metadados: { totalFeatures: 0, totalAtivosSemGeometria: 0 } })
      return
    }

    let ativo = true
    // Aguarda o fim do arraste antes de consultar
    const espera = setTimeout(() => {
      setCarregando(true)
      consultarMapa(area, tipos)
        .then((resposta) => {
          if (!ativo) return
          setColecao(resposta)
          setErro('')
        })
        .catch((error) => {
          if (!ativo) return
          setColecao(null)
          setErro(
            axios.isAxiosError(error) && error.response?.status === 422
              ? `Há mais de ${LIMITE_FEATURES} ativos nesta área. Aproxime o mapa ou desmarque camadas.`
              : extrairErroApi(error).mensagem
          )
        })
        .finally(() => ativo && setCarregando(false))
    }, 250)

    return () => {
      ativo = false
      clearTimeout(espera)
    }
  }, [area, tipos])

  function alternar(tipo: TipoAtivoMapa) {
    setTipos((atuais) => (atuais.includes(tipo) ? atuais.filter((t) => t !== tipo) : [...atuais, tipo]))
    setSelecionado(null)
  }

  const features = colecao?.features ?? []
  const linhas = features.filter((f) => f.geometry.type === 'LineString')
  const pontos = features.filter((f) => f.geometry.type === 'Point')

  return (
    <section className="rounded-xl border border-slate-800 bg-slate-900 p-6" aria-labelledby="titulo-mapa">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h3 id="titulo-mapa" className="text-lg font-semibold text-white">Mapa de ativos</h3>
          <p className="mt-1 text-xs text-slate-500">
            Posições cadastradas. Linhas ligam as subestações de origem e destino em linha reta, sem traçado real.
          </p>
        </div>

        <fieldset className="flex flex-wrap gap-3 text-xs text-slate-300">
          <legend className="sr-only">Camadas do mapa</legend>
          {CAMADAS.map((c) => (
            <label key={c.tipo} className="flex items-center gap-1.5">
              <input type="checkbox" checked={tipos.includes(c.tipo)} onChange={() => alternar(c.tipo)} />
              <span
                aria-hidden="true"
                className={c.tipo === 'LINHA' ? 'h-0 w-4 border-t-2 border-dashed' : 'h-2.5 w-2.5 rounded-full'}
                style={c.tipo === 'LINHA' ? { borderColor: c.cor } : { backgroundColor: c.cor }}
              />
              {c.rotulo}
            </label>
          ))}
        </fieldset>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_18rem]">
        <div className="relative h-[28rem] overflow-hidden rounded-lg border border-slate-800">
          <MapContainer center={[-15.8, -47.9]} zoom={4} className="h-full w-full" style={{ background: '#0f172a' }}>
            {/* Tiles do OpenStreetMap, sem chave, escurecidos por CSS para manter a superfície escura */}
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
              className="mapa-escuro"
            />
            <AcompanharArea aoMudar={aoMudarArea} />
            <Enquadrar features={features} />

            {linhas.map((f) => (
              <Polyline
                key={`${f.properties.tipoAtivo}-${f.id}`}
                positions={(f.geometry.coordinates as [number, number][]).map(([lng, lat]) => [lat, lng])}
                pathOptions={{ color: camada('LINHA').cor, weight: selecionado?.id === f.id ? 4 : 2, dashArray: '6 6' }}
                eventHandlers={{ click: () => setSelecionado(f) }}
              >
                <Tooltip sticky>{f.properties.nome}</Tooltip>
              </Polyline>
            ))}

            {pontos.map((f) => {
              const [lng, lat] = f.geometry.coordinates as [number, number]
              return (
                <CircleMarker
                  key={`${f.properties.tipoAtivo}-${f.id}`}
                  center={[lat, lng]}
                  radius={selecionado?.id === f.id ? 9 : 6}
                  pathOptions={{ color: '#0f172a', weight: 2, fillColor: camada(f.properties.tipoAtivo).cor, fillOpacity: 1 }}
                  eventHandlers={{ click: () => setSelecionado(f) }}
                >
                  <Tooltip>{f.properties.nome}</Tooltip>
                </CircleMarker>
              )
            })}
          </MapContainer>

          {carregando && (
            <p className="absolute right-2 top-2 z-[1000] rounded bg-slate-900/90 px-2 py-1 text-xs text-slate-300">Carregando...</p>
          )}
        </div>

        <aside className="text-sm" aria-label="Ativo selecionado">
          {selecionado ? (
            <div className="rounded-lg border border-slate-800 p-4">
              <p className="text-xs text-slate-500">{camada(selecionado.properties.tipoAtivo).rotulo} · #{selecionado.properties.ativoId}</p>
              <p className="mt-1 text-base font-medium text-white">{selecionado.properties.nome}</p>
              <p className={`mt-1 text-xs ${ESTADO_STYLES[selecionado.properties.estadoOperacional].text}`}>
                {ESTADO_STYLES[selecionado.properties.estadoOperacional].label}
              </p>
              {selecionado.properties.tipoRepresentacao === 'CONEXAO_SIMPLIFICADA' && (
                <p className="mt-2 text-xs text-slate-400">
                  Conexão simplificada entre as subestações #{selecionado.properties.subestacaoOrigemId} e #{selecionado.properties.subestacaoDestinoId}.
                </p>
              )}
              <p className="mt-2 break-all text-xs text-slate-500">{selecionado.properties.detalheUri}</p>
              <Link to={camada(selecionado.properties.tipoAtivo).rota} className="mt-3 inline-block text-xs text-blue-400 hover:underline">
                Abrir cadastro de {camada(selecionado.properties.tipoAtivo).rotulo.toLowerCase().replace(' (conexão simplificada)', '')}
              </Link>
            </div>
          ) : (
            <p className="rounded-lg border border-dashed border-slate-800 p-4 text-xs text-slate-500">
              Selecione um ponto ou linha no mapa para identificar o ativo.
            </p>
          )}

          {erro && <p className="mt-3 text-xs text-red-400">{erro}</p>}

          {colecao && (
            <p className="mt-3 text-xs text-slate-500" role="status">
              {colecao.metadados.totalFeatures} ativo(s) na área visível
              {colecao.metadados.totalAtivosSemGeometria > 0 &&
                ` · ${colecao.metadados.totalAtivosSemGeometria} sem coordenadas não aparecem no mapa`}
            </p>
          )}
        </aside>
      </div>
    </section>
  )
}

export default MapaAtivos
