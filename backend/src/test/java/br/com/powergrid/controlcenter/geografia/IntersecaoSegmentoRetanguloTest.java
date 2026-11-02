package br.com.powergrid.controlcenter.geografia;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IntersecaoSegmentoRetanguloTest {
    private static final BoundingBox CAIXA = new BoundingBox(n("0"), n("10"), n("0"), n("10"));

    @ParameterizedTest
    @CsvSource({"5,-5,5,15,true", "-5,5,15,5,true", "-5,-5,15,15,true", "-5,15,15,-5,true",
            "5,5,15,15,true", "2,2,8,8,true", "-5,0,15,0,true", "0,-5,0,15,true",
            "10,-5,10,15,true", "-5,10,15,10,true", "-5,-5,0,0,true", "-5,5,5,-5,true",
            "-5,15,5,5,true", "0,10,0,15,true", "0,-5,0,-1,false", "10,11,10,15,false",
            "11,-5,11,15,false", "-1,-5,-1,15,false", "-5,-1,15,-1,false", "-5,11,15,11,false",
            "-5,9,9,20,false", "5,5,5,5,true", "11,11,11,11,false", "0,0,0,0,true", "10,10,10,10,true",
            "-0.0000001,0,-0.0000001,10,false", "0.0000001,-5,0.0000001,15,true",
            "-5,4.9999999,5,-5.0000001,false", "-5,5.0000001,5,-4.9999999,true", "90,180,-90,-180,true"})
    void deveTratarCruzamentosBordasTangenciasESentidoInverso(String latA, String lngA, String latB, String lngB, boolean esperado) {
        var a = ponto(latA, lngA); var b = ponto(latB, lngB);
        assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA, a, b)).isEqualTo(esperado);
        assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA, b, a)).isEqualTo(esperado);
    }

    @Test
    void deveConferirGradeExaustivaDeSegmentosHorizontaisEVerticais() {
        for (int fixo = -2; fixo <= 12; fixo++) {
            for (int a = -2; a <= 12; a++) {
                for (int b = -2; b <= 12; b++) {
                    boolean esperado = fixo >= 0 && fixo <= 10 && Math.min(a, b) <= 10 && Math.max(a, b) >= 0;
                    assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA,
                            ponto("" + fixo, "" + a), ponto("" + fixo, "" + b))).isEqualTo(esperado);
                    assertThat(IntersecaoSegmentoRetangulo.intersecta(CAIXA,
                            ponto("" + a, "" + fixo), ponto("" + b, "" + fixo))).isEqualTo(esperado);
                }
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0,true", "0,10,true", "10,0,true", "10,10,true", "5,5,true", "-0.0000001,5,false", "5,10.0000001,false"})
    void deveIncluirBordasNaContencaoDePontos(String latitude, String longitude, boolean esperado) {
        assertThat(CAIXA.contem(ponto(latitude, longitude))).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"10,0,0,10", "0,0,0,10", "0,10,10,0", "0,10,0,0", "-91,10,0,10", "0,91,0,10", "0,10,-181,10", "0,10,0,181", "-10,10,170,-170"})
    void deveRejeitarCaixaInvalida(String minLat, String maxLat, String minLng, String maxLng) {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(n(minLat), n(maxLat), n(minLng), n(maxLng)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minLat", "maxLat", "minLng", "maxLng"})
    void deveRejeitarLimiteAusente(String campo) {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(campo.equals("minLat") ? null : n("0"),
                campo.equals("maxLat") ? null : n("10"), campo.equals("minLng") ? null : n("0"), campo.equals("maxLng") ? null : n("10")));
    }

    @ParameterizedTest
    @CsvSource({"-90.0000001,0", "90.0000001,0", "0,-180.0000001", "0,180.0000001"})
    void deveRejeitarCoordenadaForaDoDominio(String latitude, String longitude) {
        assertThrows(IllegalArgumentException.class, () -> ponto(latitude, longitude));
    }

    @Test
    void deveValidarCoordenadasAusentesELimitesMundiais() {
        assertThrows(IllegalArgumentException.class, () -> new CoordenadaGeografica(null, n("0")));
        assertThrows(IllegalArgumentException.class, () -> new CoordenadaGeografica(n("0"), null));
        var mundo = new BoundingBox(n("-90"), n("90"), n("-180"), n("180"));
        assertThat(mundo.contem(ponto("-90", "-180"))).isTrue();
        assertThat(mundo.contem(ponto("90", "180"))).isTrue();
    }

    private static BigDecimal n(String valor) { return new BigDecimal(valor); }
    private static CoordenadaGeografica ponto(String latitude, String longitude) { return new CoordenadaGeografica(n(latitude), n(longitude)); }
}
