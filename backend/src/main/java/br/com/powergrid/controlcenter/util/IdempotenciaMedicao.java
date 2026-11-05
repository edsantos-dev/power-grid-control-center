package br.com.powergrid.controlcenter.util;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class IdempotenciaMedicao {
    private IdempotenciaMedicao() {
    }

    public static UUID parseChave(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            UUID chave = UUID.fromString(valor);
            if (chave.toString().equalsIgnoreCase(valor)) {
                return chave;
            }
        } catch (IllegalArgumentException ignored) {
            // O cliente recebe a mesma mensagem para qualquer valor inválido.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key deve ser um UUID válido.");
    }

    public static String hash(Instant medidoEm, BigDecimal... grandezas) {
        var canonico = new StringBuilder("v1|").append(medidoEm);
        for (BigDecimal grandeza : grandezas) {
            canonico.append('|').append(grandeza == null ? "null" : grandeza.stripTrailingZeros().toString());
        }
        try {
            var sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(canonico.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponível.", ex);
        }
    }

    public static void validarReplay(String hash, String hashPersistido) {
        if (!hash.equals(hashPersistido)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key já usada com outro conteúdo.");
        }
    }
}
