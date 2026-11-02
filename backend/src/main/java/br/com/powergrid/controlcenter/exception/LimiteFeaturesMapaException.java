package br.com.powergrid.controlcenter.exception;

public class LimiteFeaturesMapaException extends RuntimeException {
    public LimiteFeaturesMapaException(int limite) {
        super("O mapa excede o limite de " + limite + " features. Reduza a área geográfica ou os filtros de camada/estado.");
    }
}
