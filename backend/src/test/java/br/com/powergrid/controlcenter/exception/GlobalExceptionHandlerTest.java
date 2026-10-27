package br.com.powergrid.controlcenter.exception;

import java.time.Instant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new ErrorController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"bad,400,Bad Request", "missing,404,Not Found", "conflict,409,Conflict",
            "integrity,409,Conflict", "unexpected,500,Internal Server Error",
            "internal,500,Internal Server Error", "denied,403,Forbidden"})
    void devePadronizarSemExporDetalhesInternos(String tipo, int statusCode, String error) throws Exception {
        String body = mvc.perform(get("/teste/erro/{tipo}", tipo).queryParam("segredo", "oculto"))
                .andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(statusCode))
                .andExpect(jsonPath("$.error").value(error))
                .andExpect(jsonPath("$.path").value("/teste/erro/" + tipo))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT", "senha", "oculto", "stackTrace");
        String timestamp = new ObjectMapper().readTree(body).get("timestamp").asString();
        assertThat(Instant.parse(timestamp)).isBeforeOrEqualTo(Instant.now());
        if (tipo.equals("missing")) assertThat(body).contains("Ativo não encontrado.");
    }

    @Test
    void deveDetalharCamposInvalidosSemRetornarValorRejeitado() throws Exception {
        mvc.perform(post("/teste/dados").contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nome"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void deveManter400ParaJsonMalformado() throws Exception {
        mvc.perform(post("/teste/dados").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deveManter400ParaIdInvalido() throws Exception {
        mvc.perform(get("/teste/id/abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deveManter405ECabecalhoAllow() throws Exception {
        mvc.perform(delete("/teste/dados"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405));
    }

    @RestController
    static class ErrorController {
        @GetMapping("/teste/erro/{tipo}")
        void erro(@PathVariable String tipo) {
            switch (tipo) {
                case "bad" -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dados inválidos.");
                case "missing" -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
                case "conflict" -> throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro duplicado.");
                case "integrity" -> throw new DataIntegrityViolationException("SELECT senha FROM usuario");
                case "internal" -> throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "senha interna");
                case "denied" -> throw new AccessDeniedException("senha interna");
                default -> throw new IllegalStateException("senha interna");
            }
        }

        @PostMapping("/teste/dados")
        void dados(@Valid @RequestBody Dados dados) {}

        @GetMapping("/teste/id/{id}")
        void id(@PathVariable Long id) {}
    }

    record Dados(@NotBlank String nome) {}
}
