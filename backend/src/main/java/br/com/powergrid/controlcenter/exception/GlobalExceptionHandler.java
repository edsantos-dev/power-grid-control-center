package br.com.powergrid.controlcenter.exception;

import java.time.Instant;
import java.util.List;

import br.com.powergrid.controlcenter.dto.ApiError;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return resposta(status, headers, "Existem campos inválidos na requisição.", fields, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (request instanceof ServletWebRequest servlet && servlet.getResponse() != null
                && servlet.getResponse().isCommitted()) {
            return null;
        }
        String message = status.is5xxServerError()
                ? "Ocorreu um erro interno. Tente novamente mais tarde."
                : "Não foi possível processar a requisição. Verifique os dados enviados.";
        if (ex instanceof ResponseStatusException response && !status.is5xxServerError()
                && response.getReason() != null) {
            message = response.getReason();
        }
        return resposta(status, headers, message, List.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        return resposta(HttpStatus.CONFLICT, new HttpHeaders(),
                "A operação conflita com os dados existentes ou com uma restrição de integridade.",
                List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return resposta(HttpStatus.FORBIDDEN, new HttpHeaders(),
                "Você não possui permissão para esta operação.", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(),
                "Ocorreu um erro interno. Tente novamente mais tarde.", List.of(), request);
    }

    private ResponseEntity<Object> resposta(HttpStatusCode status, HttpHeaders headers, String message,
            List<ApiError.FieldError> fields, WebRequest request) {
        HttpStatus httpStatus = HttpStatus.resolve(status.value());
        String error = httpStatus == null ? "HTTP Error" : httpStatus.getReasonPhrase();
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        return new ResponseEntity<>(new ApiError(Instant.now(), status.value(), error, message, path, fields),
                headers, status);
    }
}
