package br.com.powergrid.controlcenter.util;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

public record ParametrosConsultaMedicao(Pageable pageable, OffsetDateTime inicio, OffsetDateTime fim) {
    public static ParametrosConsultaMedicao from(MultiValueMap<String, String> params) {
        if (params.containsKey("sort")) throw invalido("Ordenação personalizada não é permitida.");
        int page = inteiro(params, "page", 0);
        int size = inteiro(params, "size", 20);
        if (page < 0 || size < 1 || size > 100) throw invalido("Página deve ser não negativa e tamanho entre 1 e 100.");
        OffsetDateTime inicio = instante(params, "dataInicio");
        OffsetDateTime fim = instante(params, "dataFim");
        if (inicio != null && fim != null && !inicio.toInstant().isBefore(fim.toInstant())) {
            throw invalido("dataInicio deve ser anterior a dataFim.");
        }
        return new ParametrosConsultaMedicao(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "medidoEm", "id")), inicio, fim);
    }
    private static String parametro(MultiValueMap<String, String> params, String nome) {
        if (!params.containsKey(nome)) return null;
        var values = params.get(nome);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
            throw invalido("Parâmetro inválido: " + nome);
        }
        return values.getFirst();
    }

    private static int inteiro(MultiValueMap<String, String> params, String nome, int padrao) {
        String valor = parametro(params, nome);
        if (valor == null) return padrao;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            throw invalido("Parâmetro inteiro inválido: " + nome);
        }
    }

    private static OffsetDateTime instante(MultiValueMap<String, String> params, String nome) {
        String valor = parametro(params, nome);
        if (valor == null) return null;
        try {
            return OffsetDateTime.parse(valor).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw invalido("Informe " + nome + " em ISO 8601 com offset.");
        }
    }


    private static ResponseStatusException invalido(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
