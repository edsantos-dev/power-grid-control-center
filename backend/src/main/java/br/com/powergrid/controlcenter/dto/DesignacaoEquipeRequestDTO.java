package br.com.powergrid.controlcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record DesignacaoEquipeRequestDTO(
        @Positive @JsonDeserialize(using = EquipeIdDeserializer.class) Long equipeId,
        @NotNull @PositiveOrZero @JsonDeserialize(using = VersaoDeserializer.class) Integer versaoDesignacaoEsperada,
        @NotBlank @Size(max = 1000) String comentario) {
    public static class VersaoDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return context.reportInputMismatch(Integer.class, "versaoDesignacaoEsperada deve ser um número inteiro.");
            }
            return parser.getIntValue();
        }
    }

    public static class EquipeIdDeserializer extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                return context.reportInputMismatch(Long.class, "equipeId deve ser um número inteiro ou nulo.");
            }
            return parser.getLongValue();
        }
    }
}
