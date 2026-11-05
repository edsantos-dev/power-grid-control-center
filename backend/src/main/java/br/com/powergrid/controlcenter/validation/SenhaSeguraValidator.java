package br.com.powergrid.controlcenter.validation;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SenhaSeguraValidator implements ConstraintValidator<SenhaSegura, String> {
    private static final Pattern CARACTERE_ESPECIAL = Pattern.compile("[\\p{P}\\p{S}]");

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext context) {
        if (senha == null || senha.isBlank()) {
            return invalida(context, "Informe a senha.");
        }
        if (senha.codePointCount(0, senha.length()) < 12) {
            return invalida(context, "A senha deve ter pelo menos 12 caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            return invalida(context, "A senha deve ter no máximo 72 bytes em UTF-8.");
        }
        if (senha.codePoints().noneMatch(Character::isUpperCase)
                || senha.codePoints().noneMatch(Character::isLowerCase)
                || senha.codePoints().noneMatch(Character::isDigit)
                || !CARACTERE_ESPECIAL.matcher(senha).find()) {
            return invalida(context, "A senha deve conter uma letra maiúscula, uma minúscula, um número e um caractere especial.");
        }
        return true;
    }

    private boolean invalida(ConstraintValidatorContext context, String mensagem) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(mensagem).addConstraintViolation();
        return false;
    }
}
