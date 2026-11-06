package br.com.powergrid.controlcenter.dto;

public record ResultadoIngestao<T>(T medicao, boolean replay) {
}
