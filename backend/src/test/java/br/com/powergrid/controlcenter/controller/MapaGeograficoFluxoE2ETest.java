package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class MapaGeograficoFluxoE2ETest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void operadorDeveConsultarMapaPersistidoComLinhaCruzandoAreaEAtivoSemGeometria() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var cenario = tx.execute(status -> {
            var isolada = subestacao("Subestação externa do E2E geográfico", "20", "20");
            var origem = subestacao("Origem externa do E2E geográfico", "5", "-5");
            var destino = subestacao("Destino externo do E2E geográfico", "5", "15");
            var alimentador = new Alimentador();
            alimentador.setNome("Alimentador do E2E geográfico");
            alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            alimentador.setSubestacao(isolada);
            entityManager.persist(alimentador);
            var transformador = new Transformador();
            transformador.setNome("Transformador sem coordenadas do E2E geográfico");
            transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            transformador.setCapacidadeMva(new BigDecimal("50"));
            transformador.setAlimentador(alimentador);
            entityManager.persist(transformador);
            var usina = new UsinaGeradora();
            usina.setNome("Usina solar interna do E2E geográfico");
            usina.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            usina.setLatitude(new BigDecimal("5"));
            usina.setLongitude(new BigDecimal("6"));
            usina.setTipoGeracao(TipoGeracao.SOLAR);
            usina.setCapacidadeMaximaMw(new BigDecimal("10"));
            usina.setSubestacao(isolada);
            entityManager.persist(usina);
            var linha = new LinhaTransmissao();
            linha.setNome("Linha cruzando a BBOX do E2E geográfico");
            linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            linha.setSubestacaoOrigem(origem);
            linha.setSubestacaoDestino(destino);
            entityManager.persist(linha);
            entityManager.flush();
            return new Cenario(isolada.getId(), origem.getId(), destino.getId(), alimentador.getId(),
                    transformador.getId(), usina.getId(), linha.getId());
        });
        try {
            var inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
            var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
            var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
            mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", "operador@powergrid.local")
                    .param("password", "SenhaSegura123")).andExpect(status().isOk());

            var resposta = mockMvc.perform(get("/api/mapa/ativos").session(sessao)
                    .param("minLat", "0").param("maxLat", "10").param("minLng", "0").param("maxLng", "10"))
                    .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/geo+json"))
                    .andExpect(jsonPath("$.type").value("FeatureCollection"))
                    .andExpect(jsonPath("$.metadados.totalFeatures").value(2))
                    .andExpect(jsonPath("$.metadados.totalAtivosSemGeometria").value(1)).andReturn();
            var features = objectMapper.readTree(resposta.getResponse().getContentAsString()).get("features");
            assertThat(features.size()).isEqualTo(2);
            var ponto = features.get(0);
            var segmento = features.get(1);
            assertThat(ponto.get("properties").get("ativoId").asLong()).isEqualTo(cenario.usinaId());
            assertThat(ponto.get("properties").get("tipoAtivo").asText()).isEqualTo("USINA");
            assertThat(ponto.get("geometry").get("type").asText()).isEqualTo("Point");
            assertThat(ponto.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(6);
            assertThat(ponto.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(5);
            assertThat(segmento.get("properties").get("ativoId").asLong()).isEqualTo(cenario.linhaId());
            assertThat(segmento.get("properties").get("tipoAtivo").asText()).isEqualTo("LINHA");
            assertThat(segmento.get("properties").get("tipoRepresentacao").asText()).isEqualTo("CONEXAO_SIMPLIFICADA");
            assertThat(segmento.get("properties").get("subestacaoOrigemId").asLong()).isEqualTo(cenario.origemId());
            assertThat(segmento.get("properties").get("subestacaoDestinoId").asLong()).isEqualTo(cenario.destinoId());
            assertThat(segmento.get("geometry").get("type").asText()).isEqualTo("LineString");
            var coordenadas = segmento.get("geometry").get("coordinates");
            assertThat(coordenadas.size()).isEqualTo(2);
            assertThat(coordenadas.get(0).get(0).asDouble()).isEqualTo(-5);
            assertThat(coordenadas.get(0).get(1).asDouble()).isEqualTo(5);
            assertThat(coordenadas.get(1).get(0).asDouble()).isEqualTo(15);
            assertThat(coordenadas.get(1).get(1).asDouble()).isEqualTo(5);
            for (var feature : features) {
                assertThat(feature.get("properties").get("ativoId").asLong())
                        .isNotIn(cenario.isoladaId(), cenario.origemId(), cenario.destinoId(), cenario.transformadorId());
                String detalheUri = feature.get("properties").get("detalheUri").asText();
                mockMvc.perform(get(detalheUri).session(sessao)).andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(feature.get("properties").get("ativoId").asLong()));
            }
        } finally {
            tx.executeWithoutResult(status -> {
                removerAtivo("linha_transmissao", cenario.linhaId());
                removerAtivo("usina_geradora", cenario.usinaId());
                removerAtivo("transformador", cenario.transformadorId());
                removerAtivo("alimentador", cenario.alimentadorId());
                removerAtivo("subestacao", cenario.destinoId());
                removerAtivo("subestacao", cenario.origemId());
                removerAtivo("subestacao", cenario.isoladaId());
            });
        }
    }

    private Subestacao subestacao(String nome, String latitude, String longitude) {
        var subestacao = new Subestacao();
        subestacao.setNome(nome);
        subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        subestacao.setLatitude(new BigDecimal(latitude));
        subestacao.setLongitude(new BigDecimal(longitude));
        entityManager.persist(subestacao);
        return subestacao;
    }

    private void removerAtivo(String tabela, Long id) {
        jdbc.update("DELETE FROM " + tabela + " WHERE id = ?", id);
        jdbc.update("DELETE FROM ativo_rede WHERE id = ?", id);
    }

    private record Cenario(Long isoladaId, Long origemId, Long destinoId, Long alimentadorId,
            Long transformadorId, Long usinaId, Long linhaId) { }
}
