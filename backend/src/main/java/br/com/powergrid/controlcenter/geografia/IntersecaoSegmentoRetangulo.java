package br.com.powergrid.controlcenter.geografia;

import java.math.BigDecimal;

public final class IntersecaoSegmentoRetangulo {
    private IntersecaoSegmentoRetangulo() { }

    public static boolean intersecta(BoundingBox caixa, CoordenadaGeografica origem, CoordenadaGeografica destino) {
        if (caixa.contem(origem) || caixa.contem(destino)) return true;
        var inferiorEsquerdo = new CoordenadaGeografica(caixa.minLat(), caixa.minLng());
        var inferiorDireito = new CoordenadaGeografica(caixa.minLat(), caixa.maxLng());
        var superiorDireito = new CoordenadaGeografica(caixa.maxLat(), caixa.maxLng());
        var superiorEsquerdo = new CoordenadaGeografica(caixa.maxLat(), caixa.minLng());
        return segmentosIntersectam(origem, destino, inferiorEsquerdo, inferiorDireito)
                || segmentosIntersectam(origem, destino, inferiorDireito, superiorDireito)
                || segmentosIntersectam(origem, destino, superiorDireito, superiorEsquerdo)
                || segmentosIntersectam(origem, destino, superiorEsquerdo, inferiorEsquerdo);
    }

    private static boolean segmentosIntersectam(CoordenadaGeografica a, CoordenadaGeografica b,
            CoordenadaGeografica c, CoordenadaGeografica d) {
        int abc = orientacao(a, b, c), abd = orientacao(a, b, d);
        int cda = orientacao(c, d, a), cdb = orientacao(c, d, b);
        if (abc == 0 && noSegmento(a, b, c)) return true;
        if (abd == 0 && noSegmento(a, b, d)) return true;
        if (cda == 0 && noSegmento(c, d, a)) return true;
        if (cdb == 0 && noSegmento(c, d, b)) return true;
        return abc * abd < 0 && cda * cdb < 0;
    }

    private static int orientacao(CoordenadaGeografica a, CoordenadaGeografica b, CoordenadaGeografica c) {
        BigDecimal produto = b.longitude().subtract(a.longitude()).multiply(c.latitude().subtract(a.latitude()))
                .subtract(b.latitude().subtract(a.latitude()).multiply(c.longitude().subtract(a.longitude())));
        return produto.signum();
    }

    private static boolean noSegmento(CoordenadaGeografica a, CoordenadaGeografica b, CoordenadaGeografica p) {
        return p.latitude().compareTo(a.latitude().min(b.latitude())) >= 0 && p.latitude().compareTo(a.latitude().max(b.latitude())) <= 0
                && p.longitude().compareTo(a.longitude().min(b.longitude())) >= 0 && p.longitude().compareTo(a.longitude().max(b.longitude())) <= 0;
    }
}
