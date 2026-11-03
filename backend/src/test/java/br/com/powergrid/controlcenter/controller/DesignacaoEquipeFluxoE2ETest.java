package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class DesignacaoEquipeFluxoE2ETest {
    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager manager;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @Test
    void gerenteDeveDespacharAlfaSubstituirPorBetaERetirarPreservandoHistoricoPaginado() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        Long[] ids = new Long[2];
        var equipes = new ArrayList<Long>();
        try {
            String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = 'GERENTE_OPERACOES'", String.class);
            var inicial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                    .andExpect(status().isOk());
            var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
            csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
            var consultaUsuario = mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isOk()).andReturn();
            var usuario = mapper.readValue(consultaUsuario.getResponse().getContentAsString(), UsuarioAutenticadoResponse.class);

            var sub = cadastrar("/api/subestacoes", Map.of("nome", "Subestação do despacho E2E", "latitude", "-23.5",
                    "longitude", "-46.6", "tensaoNominalKv", "138", "capacidadeMva", "100", "dataInstalacao", "2025-01-01"), SubestacaoResponseDTO.class);
            ids[0] = sub.id();
            var evento = cadastrar("/api/eventos-operacionais", Map.of("ativoId", sub.id(), "categoria", "MANUTENCAO",
                    "descricao", "Manutenção com despacho de equipes", "inicioPrevisto", "2026-11-04T12:00:00Z",
                    "responsavelUsuarioId", usuario.id()), EventoOperacionalResponseDTO.class);
            ids[1] = evento.id();
            assertThat(evento.versaoDesignacao()).isZero();
            assertThat(evento.equipeDesignadaId()).isNull();

            for (String nome : List.of("Alfa", "Beta")) {
                var equipe = cadastrar("/api/equipes", Map.of("nome", nome), EquipeResponseDTO.class);
                equipes.add(equipe.id());
                assertThat(equipe.situacao()).isEqualTo(br.com.powergrid.controlcenter.model.SituacaoEquipe.FORA_DE_SERVICO);
                mvc.perform(put("/api/equipes/{id}/integrantes/{usuarioId}", equipe.id(), usuario.id()).session(sessao)
                        .header(csrf.headerName(), csrf.token())).andExpect(status().isCreated());
                mvc.perform(patch("/api/equipes/{id}/situacao", equipe.id()).session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("situacao", "DISPONIVEL"))))
                        .andExpect(status().isOk());
            }

            String base = "/api/eventos-operacionais/" + evento.id();
            var alfa = designar(base, Map.of("equipeId", equipes.get(0), "versaoDesignacaoEsperada", 0, "comentario", "Despacho Alfa"));
            assertThat(alfa.equipeDesignadaId()).isEqualTo(equipes.get(0)); assertThat(alfa.versaoDesignacao()).isEqualTo(1);
            when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
            var beta = designar(base, Map.of("equipeId", equipes.get(1), "versaoDesignacaoEsperada", 1, "comentario", "Substituição Beta"));
            assertThat(beta.equipeDesignadaId()).isEqualTo(equipes.get(1)); assertThat(beta.versaoDesignacao()).isEqualTo(2);
            when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
            var retirada = designar(base, Map.of("versaoDesignacaoEsperada", 2, "comentario", "Retirada da equipe"));
            assertThat(retirada.equipeDesignadaId()).isNull(); assertThat(retirada.versaoDesignacao()).isEqualTo(3);

            var consulta = mvc.perform(get(base + "/designacoes").session(sessao)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].versaoDesignacao").value(3))
                    .andExpect(jsonPath("$.content[1].versaoDesignacao").value(2))
                    .andExpect(jsonPath("$.content[2].versaoDesignacao").value(1)).andReturn();
            var conteudo = mapper.readTree(consulta.getResponse().getContentAsString()).get("content");
            var registros = new ArrayList<HistoricoDesignacaoResponseDTO>();
            for (var item : conteudo) registros.add(mapper.readValue(item.toString(), HistoricoDesignacaoResponseDTO.class));
            assertThat(registros).extracting(HistoricoDesignacaoResponseDTO::usuarioId).containsOnly(usuario.id());
            assertThat(registros).extracting(HistoricoDesignacaoResponseDTO::eventoOperacionalId).containsOnly(evento.id());
            assertThat(registros).extracting(HistoricoDesignacaoResponseDTO::comentario)
                    .containsExactly("Retirada da equipe", "Substituição Beta", "Despacho Alfa");
            assertThat(registros.get(0).equipeAnteriorId()).isEqualTo(equipes.get(1)); assertThat(registros.get(0).equipeNovaId()).isNull();
            assertThat(registros.get(1).equipeAnteriorId()).isEqualTo(equipes.get(0)); assertThat(registros.get(1).equipeNovaId()).isEqualTo(equipes.get(1));
            assertThat(registros.get(2).equipeAnteriorId()).isNull(); assertThat(registros.get(2).equipeNovaId()).isEqualTo(equipes.get(0));
            for (int i = 0; i < 3; i++) {
                assertThat(registros.get(i).registradoEm().toInstant()).isEqualTo(AGORA.plusSeconds((2 - i) * 60L));
                assertThat(registros.get(i).registradoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
            }
            mvc.perform(get(base + "/designacoes").session(sessao).param("size", "2"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(false))
                    .andExpect(jsonPath("$.content[0].id").value(registros.get(0).id()))
                    .andExpect(jsonPath("$.content[1].id").value(registros.get(1).id()));
            mvc.perform(get(base + "/designacoes").session(sessao).param("size", "2").param("page", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.last").value(true))
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(registros.get(2).id()));
            mvc.perform(get(base).session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.equipeDesignadaId").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.versaoDesignacao").value(3))
                    .andExpect(jsonPath("$.situacao").value("ABERTO"))
                    .andExpect(jsonPath("$.inicioEfetivo").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.terminoEfetivo").value(org.hamcrest.Matchers.nullValue()));
            mvc.perform(get(base + "/transicoes").session(sessao)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            assertThat(jdbc.queryForObject("SELECT versao_designacao FROM evento_operacional WHERE id = ?", Integer.class, evento.id())).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT equipe_designada_id FROM evento_operacional WHERE id = ?", Long.class, evento.id())).isNull();
            assertThat(jdbc.queryForList("SELECT situacao FROM equipe_manutencao WHERE id IN (?, ?) ORDER BY id", String.class,
                    equipes.get(0), equipes.get(1))).containsExactly("DISPONIVEL", "DISPONIVEL");
        } finally {
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                if (ids[1] != null) {
                    jdbc.update("DELETE FROM historico_designacao_equipe WHERE evento_operacional_id = ?", ids[1]);
                    jdbc.update("DELETE FROM historico_evento_operacional WHERE evento_id = ?", ids[1]);
                    jdbc.update("DELETE FROM evento_operacional WHERE id = ?", ids[1]);
                }
                for (Long id : equipes) {
                    jdbc.update("DELETE FROM vinculo_equipe WHERE equipe_id = ?", id);
                    jdbc.update("DELETE FROM equipe_manutencao WHERE id = ?", id);
                }
                if (ids[0] != null) {
                    jdbc.update("DELETE FROM subestacao WHERE id = ?", ids[0]);
                    jdbc.update("DELETE FROM ativo_rede WHERE id = ?", ids[0]);
                }
            });
        }
    }

    private EventoOperacionalResponseDTO designar(String base, Map<String, ?> corpo) throws Exception {
        var resposta = mvc.perform(put(base + "/equipe-designada").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isOk()).andReturn();
        return mapper.readValue(resposta.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
    }

    private <T> T cadastrar(String uri, Map<String, ?> corpo, Class<T> tipo) throws Exception {
        var resposta = mvc.perform(post(uri).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readValue(resposta.getResponse().getContentAsString(), tipo);
    }
}
