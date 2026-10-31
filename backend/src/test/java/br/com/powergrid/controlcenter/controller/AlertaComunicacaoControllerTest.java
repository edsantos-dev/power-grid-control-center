package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.AlertaComunicacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AlertaComunicacaoControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private AlertaComunicacaoRepository alertas;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-31T12:00:00Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        autenticar(PerfilAcesso.OPERADOR);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 299, 300})
    void naoDeveGerarDentroOuNoLimiteDaTolerancia(int segundos) throws Exception {
        fonte("NORMAL", AGORA.minusSeconds(segundos));
        var resumo = avaliar();
        assertThat(resumo.toleranciaSegundos()).isEqualTo(300);
        assertThat(resumo.fontesAvaliadas()).isEqualTo(1);
        assertThat(resumo.alertasCriados()).isZero();
        assertThat(alertas.count()).isZero();
    }

    @Test
    void deveGerarAposTimeoutEPreservarSnapshotsSemDuplicar() throws Exception {
        var fonte = fonte("ATRASADA", AGORA.minusSeconds(301));
        var primeiro = avaliar();
        assertThat(primeiro.avaliadoEm().toInstant()).isEqualTo(AGORA);
        assertThat(primeiro.avaliadoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(primeiro.alertasCriados()).isEqualTo(1);
        var alerta = alertas.findAll().getFirst();
        assertThat(alerta.getFonte().getId()).isEqualTo(fonte.getId());
        assertThat(alerta.getAtivo().getId()).isEqualTo(fonte.getAtivo().getId());
        assertThat(alerta.getMonitoradaDesde()).isEqualTo(fonte.getMonitoradaDesde());
        assertThat(alerta.getUltimaComunicacaoEm()).isEqualTo(fonte.getUltimaComunicacaoEm());
        assertThat(alerta.getDetectadoEm().toInstant()).isEqualTo(AGORA);
        assertThat(alerta.getStatus()).isEqualTo(StatusComunicacao.ATIVO);
        assertThat(alerta.getCriticidade()).isEqualTo(Criticidade.MEDIA);
        when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        var repetido = avaliar();
        assertThat(repetido.alertasCriados()).isZero();
        assertThat(repetido.alertasAtivosExistentes()).isEqualTo(1);
        entityManager.flush(); entityManager.clear();
        assertThat(alertas.count()).isEqualTo(1);
        assertThat(alertas.findById(alerta.getId()).orElseThrow().getDetectadoEm().toInstant()).isEqualTo(AGORA);
    }

    @ParameterizedTest
    @CsvSource({"299,0", "300,0", "301,1"})
    void deveUsarInicioQuandoNuncaHouveComunicacao(int segundos, long esperados) throws Exception {
        var fonte = fonte("SEM-LEITURA", null);
        fonte.setMonitoradaDesde(AGORA.minusSeconds(segundos).atOffset(ZoneOffset.UTC));
        entityManager.flush();
        assertThat(avaliar().alertasCriados()).isEqualTo(esperados);
        if (esperados > 0) assertThat(alertas.findAll().getFirst().getUltimaComunicacaoEm()).isNull();
    }

    @Test
    void deveAvaliarMaisDeUmLoteESuportarFontesDeTransformador() throws Exception {
        var inicial = fonte("FONTE-0", AGORA.minusSeconds(301));
        for (int i = 1; i < 102; i++) {
            var f = new FonteMedicao(); f.setAtivo(inicial.getAtivo());
            f.setIdentificador("FONTE-" + i); f.setOrigem(OrigemMedicao.SIMULADA);
            f.setMonitoradaDesde(inicial.getMonitoradaDesde());
            f.setUltimaComunicacaoEm(inicial.getUltimaComunicacaoEm()); entityManager.persist(f);
        }
        var alimentador = new Alimentador(); alimentador.setNome("Alimentador");
        alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(((LinhaTransmissao) inicial.getAtivo()).getSubestacaoOrigem()); entityManager.persist(alimentador);
        var trafo = new Transformador(); trafo.setNome("Transformador");
        trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); trafo.setAlimentador(alimentador);
        trafo.setCapacidadeMva(BigDecimal.TEN); entityManager.persist(trafo);
        var f = new FonteMedicao(); f.setAtivo(trafo); f.setIdentificador("TRAFO"); f.setOrigem(OrigemMedicao.SIMULADA);
        f.setMonitoradaDesde(inicial.getMonitoradaDesde()); f.setUltimaComunicacaoEm(inicial.getUltimaComunicacaoEm());
        entityManager.persist(f); entityManager.flush();
        var resumo = avaliar();
        assertThat(resumo.fontesAvaliadas()).isEqualTo(103);
        assertThat(resumo.alertasCriados()).isEqualTo(103);
        assertThat(alertas.findAll()).anySatisfy(a -> assertThat(a.getAtivo().getId()).isEqualTo(trafo.getId()));
    }

    @Test
    void deveRetornarResumoVazioSemFontes() throws Exception {
        var resumo = avaliar();
        assertThat(resumo.fontesAvaliadas()).isZero();
        assertThat(resumo.alertasCriados()).isZero();
        assertThat(resumo.alertasAtivosExistentes()).isZero();
    }

    @Test
    void deveConsultarDetalhePaginacaoOrdemEFiltros() throws Exception {
        var f1 = fonte("F1", AGORA.minusSeconds(301));
        var a1 = alerta(f1, AGORA.minusSeconds(10), StatusComunicacao.ATIVO);
        var a2 = alerta(f1, AGORA, StatusComunicacao.RESTABELECIDO);
        var f2 = fonte("F2", null);
        var a3 = alerta(f2, AGORA, StatusComunicacao.ATIVO);
        entityManager.flush(); entityManager.clear();
        mockMvc.perform(get(base()).session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.content[0].id").value(a3.getId()));
        mockMvc.perform(get(base()).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(a2.getId()));
        mockMvc.perform(get(base()).session(sessao).param("fonteId", f1.getId().toString())
                .param("ativoId", f1.getAtivo().getId().toString()).param("status", "ATIVO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a1.getId()));
        mockMvc.perform(get(base()).session(sessao).param("fonteId", f1.getId().toString()).param("ativoId", f2.getAtivo().getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(base()).session(sessao).param("dataInicio", "2026-10-31T09:00:00-03:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(base()).session(sessao).param("dataFim", AGORA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base()).session(sessao).param("status", "RESTABELECIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        var detalhe = mockMvc.perform(get(base() + "/" + a2.getId()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.fonteId").value(f1.getId())).andExpect(jsonPath("$.status").value("RESTABELECIDO")).andReturn();
        var dto = objectMapper.readValue(detalhe.getResponse().getContentAsString(), AlertaComunicacaoResponseDTO.class);
        assertThat(dto.detectadoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(dto.monitoradaDesde().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(dto.ultimaComunicacaoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(dto.restabelecidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        mockMvc.perform(get(base() + "/" + Long.MAX_VALUE).session(sessao))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deveRetornarListaVaziaEPaginaDistante() throws Exception {
        mockMvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        alerta(fonte("F", null), AGORA, StatusComunicacao.ATIVO);
        mockMvc.perform(get(base()).session(sessao).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "page|abc", "page|2147483648", "size|0", "size|101", "size|1.5", "sort|detectadoEm,asc",
            "fonteId|0", "fonteId|-1", "fonteId|abc", "fonteId|9223372036854775808", "ativoId|0", "ativoId|abc",
            "ativoId|9223372036854775808", "status|RECONHECIDO", "status|ativo", "dataInicio|invalido", "dataFim|2026-10-31T12:00:00"}, delimiter = '|')
    void deveRejeitarFiltrosInvalidos(String campo, String valor) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, valor)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "fonteId", "ativoId", "status", "dataInicio", "dataFim"})
    void deveRejeitarVaziosEDuplicados(String campo) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base()).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarPeriodoInvertidoOuIgual() throws Exception {
        for (var fim : new String[] {AGORA.toString(), AGORA.minusSeconds(1).toString()}) {
            mockMvc.perform(get(base()).session(sessao).param("dataInicio", AGORA.toString()).param("dataFim", fim))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void deveExigirSessaoECsrfEPermitirSomenteOperadorEGerenteNoJob() throws Exception {
        mockMvc.perform(post(job()).session(sessao)).andExpect(status().isForbidden());
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post(job()).session(anonima).header(token.headerName(), token.token()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(base())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base() + "/1")).andExpect(status().isUnauthorized());
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        mockMvc.perform(post(job()).session(sessao).header(csrf.headerName(), csrf.token())).andExpect(status().isForbidden());
        mockMvc.perform(get(base()).session(sessao)).andExpect(status().isOk());
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        assertThat(avaliar().fontesAvaliadas()).isZero();
    }

    private AvaliacaoComunicacaoResultDTO avaliar() throws Exception {
        var result = mockMvc.perform(post(job()).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), AvaliacaoComunicacaoResultDTO.class);
    }

    private AlertaComunicacao alerta(FonteMedicao fonte, Instant detectado, StatusComunicacao status) {
        var alerta = new AlertaComunicacao(); alerta.setFonte(fonte); alerta.setAtivo(fonte.getAtivo());
        alerta.setMonitoradaDesde(fonte.getMonitoradaDesde()); alerta.setUltimaComunicacaoEm(fonte.getUltimaComunicacaoEm());
        alerta.setToleranciaSegundos(300); alerta.setDetectadoEm(detectado.atOffset(ZoneOffset.UTC)); alerta.setStatus(status);
        if (status == StatusComunicacao.RESTABELECIDO) alerta.setRestabelecidoEm(detectado.plusSeconds(1).atOffset(ZoneOffset.UTC));
        return alertas.saveAndFlush(alerta);
    }

    private FonteMedicao fonte(String identificador, Instant contato) {
        var fonte = new FonteMedicao(); fonte.setIdentificador(identificador); fonte.setOrigem(OrigemMedicao.SIMULADA);
        fonte.setAtivo(linha()); fonte.setMonitoradaDesde(AGORA.minusSeconds(3600).atOffset(ZoneOffset.UTC));
        fonte.setUltimaComunicacaoEm(contato == null ? null : contato.atOffset(ZoneOffset.UTC));
        entityManager.persist(fonte); entityManager.flush(); return fonte;
    }

    private String base() { return "/api/alertas-comunicacao"; }
    private String job() { return "/api/jobs/avaliar-comunicacao"; }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        String email = entityManager.createQuery("select u.email from Usuario u where u.perfil = :perfil", String.class)
                .setParameter("perfil", perfil).getSingleResult();
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }

    private LinhaTransmissao linha() {
        Subestacao a = new Subestacao();
        a.setNome("Origem");
        a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(a);
        Subestacao b = new Subestacao();
        b.setNome("Destino");
        b.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(b);
        LinhaTransmissao linha = new LinhaTransmissao();
        linha.setNome("Linha");
        linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        linha.setSubestacaoOrigem(a);
        linha.setSubestacaoDestino(b);
        linha.setExtensaoKm(new BigDecimal("100"));
        linha.setTensaoOperacaoKv(new BigDecimal("100"));
        entityManager.persist(linha);
        entityManager.flush();
        return linha;
    }

}
