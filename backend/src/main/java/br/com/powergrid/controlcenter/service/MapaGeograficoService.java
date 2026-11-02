package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.util.*;
import br.com.powergrid.controlcenter.dto.geojson.*;
import br.com.powergrid.controlcenter.exception.LimiteFeaturesMapaException;
import br.com.powergrid.controlcenter.geografia.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.AtivoRedeRepository;
import br.com.powergrid.controlcenter.repository.LinhaTransmissaoRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MapaGeograficoService {
    private static final int TAMANHO_LOTE = 200;
    private static final Set<String> PARAMETROS = Set.of("minLat", "maxLat", "minLng", "maxLng", "tipos", "estados", "limite");
    private final AtivoRedeRepository ativos;
    private final LinhaTransmissaoRepository linhas;

    public MapaGeograficoService(AtivoRedeRepository ativos, LinhaTransmissaoRepository linhas) {
        this.ativos = ativos; this.linhas = linhas;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public GeoJsonFeatureCollection consultar(MultiValueMap<String, String> params) {
        for (var nome : params.keySet()) if (!PARAMETROS.contains(nome)) throw invalido("Parâmetro não suportado: " + nome);
        BoundingBox caixa;
        try { caixa = new BoundingBox(numero(params, "minLat"), numero(params, "maxLat"), numero(params, "minLng"), numero(params, "maxLng")); }
        catch (IllegalArgumentException ex) { throw invalido("BBOX inválida: confira limites geográficos, ordem e presença das quatro coordenadas."); }
        var tipos = enumerados(params, "tipos", TipoAtivoMapa.class);
        var estados = enumerados(params, "estados", EstadoOperacional.class);
        int limite = limite(params);
        var features = new ArrayList<GeoJsonFeature>();
        long apos = 0;
        while (true) {
            var lote = ativos.buscarPontos(caixa, tipos, estados, apos, PageRequest.of(0, TAMANHO_LOTE));
            for (var ponto : lote) {
                var tipo = Arrays.stream(TipoAtivoMapa.values()).filter(t -> t.classe().equals(ponto.classeAtivo())).findFirst().orElseThrow();
                var properties = new GeoJsonProperties(ponto.ativoId(), tipo, ponto.nome(), ponto.estadoOperacional(),
                        detalheUri(tipo, ponto.ativoId()), null, null, null);
                adicionar(features, new GeoJsonFeature(new GeoJsonPoint(ponto.longitude(), ponto.latitude()), properties), limite);
            }
            if (lote.size() < TAMANHO_LOTE) break;
            apos = lote.getLast().ativoId();
        }
        if (tipos.contains(TipoAtivoMapa.LINHA)) {
            apos = 0;
            while (true) {
                var lote = linhas.buscarCandidatas(caixa, estados, apos, PageRequest.of(0, TAMANHO_LOTE));
                for (var linha : lote) {
                    if (!IntersecaoSegmentoRetangulo.intersecta(caixa, linha.origem(), linha.destino())) continue;
                    var properties = new GeoJsonProperties(linha.ativoId(), TipoAtivoMapa.LINHA, linha.nome(), linha.estadoOperacional(),
                            detalheUri(TipoAtivoMapa.LINHA, linha.ativoId()), linha.subestacaoOrigemId(), linha.subestacaoDestinoId(), "CONEXAO_SIMPLIFICADA");
                    adicionar(features, new GeoJsonFeature(new GeoJsonLineString(linha.longitudeOrigem(), linha.latitudeOrigem(),
                            linha.longitudeDestino(), linha.latitudeDestino()), properties), limite);
                }
                if (lote.size() < TAMANHO_LOTE) break;
                apos = lote.getLast().ativoId();
            }
        }
        features.sort(Comparator.comparing(f -> f.properties().ativoId()));
        long semGeometria = ativos.contarSemGeometria(tipos, estados)
                + (tipos.contains(TipoAtivoMapa.LINHA) ? linhas.contarSemGeometria(estados) : 0);
        return new GeoJsonFeatureCollection(features, semGeometria);
    }

    private void adicionar(List<GeoJsonFeature> features, GeoJsonFeature feature, int limite) {
        features.add(feature);
        if (features.size() > limite) throw new LimiteFeaturesMapaException(limite);
    }

    private BigDecimal numero(MultiValueMap<String, String> params, String nome) {
        String valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) throw invalido("Parâmetro obrigatório: " + nome);
        try { return new BigDecimal(valor.trim()); }
        catch (NumberFormatException ex) { throw invalido("Coordenada não numérica: " + nome); }
    }

    private <E extends Enum<E>> Set<E> enumerados(MultiValueMap<String, String> params, String nome, Class<E> tipo) {
        String valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return EnumSet.allOf(tipo);
        var resultado = EnumSet.noneOf(tipo);
        try {
            for (String item : valor.split(",", -1)) resultado.add(Enum.valueOf(tipo, item.trim()));
        } catch (IllegalArgumentException ex) { throw invalido("Lista inválida para " + nome + "."); }
        return resultado;
    }

    private int limite(MultiValueMap<String, String> params) {
        String valor = ParametrosConsultaMedicao.parametro(params, "limite");
        if (valor == null) return 1000;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            int limite = Integer.parseInt(valor);
            if (limite < 1 || limite > 5000) throw new NumberFormatException();
            return limite;
        } catch (NumberFormatException ex) { throw invalido("limite deve ser um inteiro entre 1 e 5000."); }
    }

    private String detalheUri(TipoAtivoMapa tipo, Long id) {
        String recurso = switch (tipo) {
            case SUBESTACAO -> "subestacoes";
            case USINA -> "usinas";
            case TRANSFORMADOR -> "transformadores";
            case LINHA -> "linhas-transmissao";
        };
        return "/api/" + recurso + "/" + id;
    }

    private ResponseStatusException invalido(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
}
