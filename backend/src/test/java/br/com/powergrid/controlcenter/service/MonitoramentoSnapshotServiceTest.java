package br.com.powergrid.controlcenter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.LinhaSnapshotDTO;
import br.com.powergrid.controlcenter.dto.TransformadorSnapshotDTO;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.model.LinhaTransmissao;
import br.com.powergrid.controlcenter.model.OrigemMedicao;
import br.com.powergrid.controlcenter.model.SituacaoFatorPotencia;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.Transformador;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MonitoramentoSnapshotServiceTest {
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private static final Instant T0 = AGORA.minusSeconds(120);
    private static final Instant T1 = AGORA.minusSeconds(60);

    @Autowired private MonitoramentoSnapshotService service;
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private Clock clock;
    @MockitoSpyBean private JdbcClient jdbcClient;

    private Subestacao origem;
    private Subestacao destino;

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(AGORA);
        origem = salvar(new Subestacao(), "Origem");
        destino = salvar(new Subestacao(), "Destino");
    }

    @Test
    void selecionaUltimaAmostraPorGrandezaESoCombinaPotenciasDaMesmaLeitura() {
        var linha = linha();
        Long fonteA = fonte(linha);
        Long fonteB = fonte(linha);
        leituraLinha(fonteA, T0, "100", "20", "60", "3", "4");
        leituraLinha(fonteB, T1, "115", null, null, null, null);
        leituraLinha(fonteA, T1, "116", null, null, "8", null);
        leituraLinha(fonteB, AGORA.plusSeconds(1), "999", "99", null, null, "9");

        var filtros = new LinkedMultiValueMap<String, String>();
        filtros.add("ativoId", linha.getId().toString());
        var resposta = service.consultar(filtros);
        assertThat(resposta.consultadoEm()).isEqualTo(AGORA);
        assertThat(resposta.pagina().content()).hasSize(1);
        var snapshot = (LinhaSnapshotDTO) resposta.pagina().content().getFirst();
        assertThat(snapshot.tensaoKv().valor()).isEqualByComparingTo("116");
        assertThat(snapshot.tensaoKv().medidoEm()).isEqualTo(T1);
        assertThat(snapshot.tensaoKv().fonteId()).isEqualTo(fonteA);
        assertThat(snapshot.correnteA().valor()).isEqualByComparingTo("20");
        assertThat(snapshot.correnteA().medidoEm()).isEqualTo(T0);
        assertThat(snapshot.correnteA().recebidoEm()).isEqualTo(T0.plusSeconds(1));
        assertThat(snapshot.frequenciaHz().valor()).isEqualByComparingTo("60");
        assertThat(snapshot.potenciaAtivaMw().valor()).isEqualByComparingTo("8");
        assertThat(snapshot.potenciaReativaMvar().valor()).isEqualByComparingTo("4");
        assertThat(snapshot.fatorPotencia().valor()).isEqualByComparingTo("0.600000");
        assertThat(snapshot.fatorPotencia().medidoEm()).isEqualTo(T0);
        assertThat(snapshot.fatorPotencia().fonteId()).isEqualTo(fonteA);
        assertThat(snapshot.situacaoFatorPotencia()).isEqualTo(SituacaoFatorPotencia.CALCULADO);
    }

    @Test
    void calculaCargaPelaCapacidadeDaLeituraEFiltraTopologia() {
        var transformador = transformador();
        Long fonte = fonte(transformador);
        leituraTransformador(fonte, T0, "8", "10");
        leituraTransformador(fonte, T1, "12", "10");
        var semMedicoes = linha();

        var filtros = new LinkedMultiValueMap<String, String>();
        filtros.add("subestacaoId", origem.getId().toString());
        filtros.add("tipos", "TRANSFORMADOR,LINHA");
        var pagina = service.consultar(filtros).pagina();
        assertThat(pagina.totalElements()).isEqualTo(2);
        var carga = (TransformadorSnapshotDTO) pagina.content().getFirst();
        assertThat(carga.ativoId()).isEqualTo(transformador.getId());
        assertThat(carga.subestacaoId()).isEqualTo(origem.getId());
        assertThat(carga.potenciaAparenteMva().valor()).isEqualByComparingTo("12");
        assertThat(carga.capacidadeReferenciaMva().valor()).isEqualByComparingTo("10");
        assertThat(carga.cargaPercentual().valor()).isEqualByComparingTo("120.000000");
        assertThat(carga.cargaPercentual().fonteId()).isEqualTo(fonte);
        assertThat(carga.cargaPercentual().medidoEm()).isEqualTo(T1);

        filtros.set("subestacaoId", destino.getId().toString());
        var filtrados = service.consultar(filtros).pagina().content();
        assertThat(filtrados).hasSize(1);
        var linha = (LinhaSnapshotDTO) filtrados.getFirst();
        assertThat(linha.ativoId()).isEqualTo(semMedicoes.getId());
        assertThat(linha.tensaoKv()).isNull();
        assertThat(linha.fatorPotencia()).isNull();
        assertThat(linha.situacaoFatorPotencia()).isEqualTo(SituacaoFatorPotencia.DADOS_INSUFICIENTES);
    }

    @Test
    void numeroDeConsultasIndependeDaQuantidadeDeAtivosNaPagina() {
        for (int i = 0; i < 5; i++) {
            leituraLinha(fonte(linha()), T0, "100", null, null, null, null);
            leituraTransformador(fonte(transformador()), T0, "8", "10");
        }
        var filtros = new LinkedMultiValueMap<String, String>();
        for (int tamanho : new int[] {2, 10}) {
            filtros.set("size", Integer.toString(tamanho));
            clearInvocations(jdbcClient);
            var pagina = service.consultar(filtros).pagina();
            assertThat(pagina.content()).hasSize(tamanho);
            assertThat(pagina.totalElements()).isEqualTo(10);
            verify(jdbcClient, times(4)).sql(anyString());
        }
    }

    @Test
    void serializaPaginaNoTopoEValidaFiltrosHttp() throws Exception {
        var linha = linha();
        var fonte = fonte(linha);
        leituraLinha(fonte, T0, "100", null, null, null, null);
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        var sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = json.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "operador@powergrid.local").param("password", "SenhaSegura123"))
                .andExpect(status().isOk());

        String rota = "/api/monitoramento/snapshot";
        mvc.perform(get(rota)).andExpect(status().isUnauthorized());
        mvc.perform(get(rota).session(sessao).param("tipos", "LINHA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultadoEm").value(AGORA.toString()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].tipoAtivo").value("LINHA"))
                .andExpect(jsonPath("$.content[0].tensaoKv.fonteId").value(fonte.intValue()))
                .andExpect(jsonPath("$.content[0].tensaoKv.medidoEm").value(T0.toString()));
        mvc.perform(get(rota).session(sessao).param("sort", "id")).andExpect(status().isBadRequest());
        mvc.perform(get(rota).session(sessao).param("tipos", "SUBESTACAO")).andExpect(status().isBadRequest());
        mvc.perform(get(rota).session(sessao).param("size", "101")).andExpect(status().isBadRequest());
    }

    private LinhaTransmissao linha() {
        var linha = new LinhaTransmissao();
        linha.setSubestacaoOrigem(origem);
        linha.setSubestacaoDestino(destino);
        linha.setExtensaoKm(BigDecimal.TEN);
        linha.setTensaoOperacaoKv(BigDecimal.TEN);
        return salvar(linha, "Linha");
    }

    private Transformador transformador() {
        var alimentador = new Alimentador();
        alimentador.setSubestacao(origem);
        salvar(alimentador, "Alimentador");
        var transformador = new Transformador();
        transformador.setAlimentador(alimentador);
        transformador.setCapacidadeMva(new BigDecimal("30"));
        return salvar(transformador, "Transformador");
    }

    private <T extends AtivoRede> T salvar(T ativo, String nome) {
        ativo.setNome(nome);
        ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        em.persist(ativo);
        return ativo;
    }

    private Long fonte(AtivoRede ativo) {
        var fonte = new FonteMedicao();
        fonte.setAtivo(ativo);
        fonte.setOrigem(OrigemMedicao.SIMULADA);
        fonte.setIdentificador(UUID.randomUUID().toString());
        fonte.setMonitoradaDesde(utc(T0));
        em.persist(fonte);
        em.flush();
        return fonte.getId();
    }

    private void leituraLinha(Long fonte, Instant medido, String tensao, String corrente, String frequencia,
            String ativa, String reativa) {
        jdbc.update("""
                INSERT INTO medicao_linha (fonte_id, medido_em, recebido_em, tensao_kv, corrente_a,
                    frequencia_hz, potencia_ativa_mw, potencia_reativa_mvar)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, fonte, utc(medido), utc(medido.plusSeconds(1)), decimal(tensao), decimal(corrente),
                decimal(frequencia), decimal(ativa), decimal(reativa));
    }

    private void leituraTransformador(Long fonte, Instant medido, String potencia, String capacidade) {
        jdbc.update("""
                INSERT INTO medicao_transformador (fonte_id, medido_em, recebido_em,
                    potencia_aparente_mva, capacidade_referencia_mva) VALUES (?, ?, ?, ?, ?)
                """, fonte, utc(medido), utc(medido.plusSeconds(1)), decimal(potencia), decimal(capacidade));
    }

    private OffsetDateTime utc(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }

    private BigDecimal decimal(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }
}
