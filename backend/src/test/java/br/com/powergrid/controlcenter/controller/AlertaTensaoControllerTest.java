package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.MedicaoLinhaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AlertaTensaoControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private MedicaoLinhaRepository repository;
    @Autowired private br.com.powergrid.controlcenter.repository.AlertaTensaoRepository alertas;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private FonteMedicao fonte;
    private Long linhaId;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        fonte = fonte();
        entityManager.persist(fonte);
        entityManager.flush();
        linhaId = fonte.getAtivo().getId();
		MvcResult inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
		sessao = (MockHttpSession) inicial.getRequest().getSession(false);
		CsrfResponse token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
		mockMvc.perform(post("/api/auth/login").session(sessao)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.header(token.headerName(), token.token())
				.param("email", "operador@powergrid.local").param("password", "SenhaSegura123"))
				.andExpect(status().isOk());
		MvcResult renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
		csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }


    @ParameterizedTest
    @CsvSource({"0,SUBTENSAO", "90,SUBTENSAO", "95,SUBTENSAO", "105,SOBRETENSAO", "110,SOBRETENSAO"})
    void deveGerarInclusiveNosLimites(String medida, String tipo) throws Exception {
        var medicao = ingerir(medida, 201);
        var alerta = unico();
        assertThat(alerta.getTipo()).isEqualTo(TipoAlertaTensao.valueOf(tipo));
        assertThat(alerta.getMedicao().getId()).isEqualTo(medicao.id());
        assertThat(alerta.getAtivo().getId()).isEqualTo(linhaId);
        assertThat(alerta.getTensaoMedidaKv()).isEqualByComparingTo(medida);
        assertThat(alerta.getTensaoReferenciaKv()).isEqualByComparingTo("100");
        assertThat(alerta.getLimiteInferiorKv()).isEqualByComparingTo("95");
        assertThat(alerta.getLimiteSuperiorKv()).isEqualByComparingTo("105");
        assertThat(alerta.getGeradoEm().toInstant()).isEqualTo(AGORA);
        assertThat(alerta.getStatus()).isEqualTo(StatusAlerta.ATIVO);
    }

    @ParameterizedTest
    @ValueSource(strings = {"95.000001", "100", "104.999999"})
    void deveAceitarFaixaNormalSemAlerta(String valor) throws Exception {
        ingerir(valor, 201); assertThat(alertas.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nula", "zero", "negativa"})
    void deveRejeitarReferenciaInvalidaSemPersistir(String caso) throws Exception {
        var linha = entityManager.find(LinhaTransmissao.class, linhaId);
        linha.setTensaoOperacaoKv(caso.equals("nula") ? null : new BigDecimal(caso.equals("zero") ? "0" : "-1"));
        ingerir("100", 400); assertThat(repository.count()).isZero(); assertThat(alertas.count()).isZero();
    }

    @Test
    void deveIgnorarLeituraSemTensaoMesmoSemReferencia() throws Exception {
        entityManager.find(LinhaTransmissao.class, linhaId).setTensaoOperacaoKv(null);
        var req = Map.of("fonteId", fonte.getId(), "medidoEm", "2026-10-28T13:00:00Z", "correnteA", 10);
        mockMvc.perform(post(ingestao()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
        assertThat(alertas.count()).isZero();
    }

    @Test
    void devePreservarSnapshotsEPrecisaoDosLimites() throws Exception {
        entityManager.find(LinhaTransmissao.class, linhaId).setTensaoOperacaoKv(new BigDecimal("138.123457"));
        ingerir("150", 201); var a = unico();
        assertThat(a.getLimiteInferiorKv()).isEqualByComparingTo("131.21728415");
        assertThat(a.getLimiteSuperiorKv()).isEqualByComparingTo("145.02962985");
        Long id = a.getId();
        entityManager.find(LinhaTransmissao.class, linhaId).setTensaoOperacaoKv(new BigDecimal("200"));
        entityManager.flush(); entityManager.clear();
        mockMvc.perform(get(base() + "/" + id).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("SOBRETENSAO"));
        var salvo = alertas.findById(id).orElseThrow();
        assertThat(salvo.getTensaoReferenciaKv()).isEqualByComparingTo("138.123457");
    }

    @Test
    void deveConsultarDetalheVazioEPaginas() throws Exception {
        mockMvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.size").value(20));
        ingerir("90", 201); var primeiro = unico();
        ingerir("110", 201);
        mockMvc.perform(get(base()).session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].tipo").value("SOBRETENSAO"));
        mockMvc.perform(get(base()).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(primeiro.getId()));
        mockMvc.perform(get(base() + "/" + primeiro.getId()).session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("SUBTENSAO"))
                .andExpect(jsonPath("$.reconhecidoPorUsuarioId").isEmpty());
        mockMvc.perform(get(base() + "/" + Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        mockMvc.perform(get(base()).session(sessao).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void deveFiltrarAtivoTipoStatusEPeriodoDeGeracao() throws Exception {
        when(clock.instant()).thenReturn(AGORA.minusSeconds(10)); ingerir("90", 201); var antigo = unico();
        when(clock.instant()).thenReturn(AGORA); ingerir("110", 201);
        var novo = entityManager.createQuery("select a from AlertaTensao a where a.tipo = :tipo", AlertaTensao.class)
                .setParameter("tipo", TipoAlertaTensao.SOBRETENSAO).getSingleResult();
        var usuario = entityManager.createQuery("select u from Usuario u where u.email = :email", Usuario.class)
                .setParameter("email", "operador@powergrid.local").getSingleResult();
        novo.setStatus(StatusAlerta.RECONHECIDO); novo.setReconhecidoEm(AGORA.atOffset(ZoneOffset.UTC)); novo.setReconhecidoPorUsuario(usuario);
        LinhaTransmissao outra = linha(); FonteMedicao outraFonte = new FonteMedicao(); outraFonte.setIdentificador("OUTRA");
        outraFonte.setOrigem(OrigemMedicao.SIMULADA); outraFonte.setAtivo(outra); entityManager.persist(outraFonte); entityManager.flush();
        var req = Map.of("fonteId", outraFonte.getId(), "medidoEm", "2026-10-28T13:00:00Z", "tensaoKv", 90);
        mockMvc.perform(post("/api/linhas-transmissao/{id}/medicoes", outra.getId()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
        mockMvc.perform(get(base()).session(sessao).param("ativoId", linhaId.toString()).param("tipo", "SUBTENSAO").param("status", "ATIVO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(antigo.getId()));
        mockMvc.perform(get(base()).session(sessao).param("tipo", "SOBRETENSAO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base()).session(sessao).param("status", "RECONHECIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reconhecidoPorUsuarioId").isNumber());
        mockMvc.perform(get(base()).session(sessao).param("dataInicio", "2026-10-29T09:00:00-03:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(base()).session(sessao).param("dataFim", AGORA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base()).session(sessao).param("ativoId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "size|101", "size|0", "sort|geradoEm,desc", "ativoId|0", "ativoId|abc",
            "tipo|OUTRO", "status|OUTRO", "dataInicio|invalido", "dataFim|2026-10-29T12:00:00"}, delimiter = '|')
    void deveRejeitarParametrosInvalidos(String campo, String valor) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, valor)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "ativoId", "tipo", "status", "dataInicio", "dataFim"})
    void deveRejeitarVaziosEDuplicados(String campo) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base()).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarPeriodoInvalidoEExigirSessao() throws Exception {
        mockMvc.perform(get(base()).session(sessao).param("dataInicio", AGORA.toString()).param("dataFim", AGORA.toString()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base() + "/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void deveReconhecerSemCorpoComUsuarioDaSessao() throws Exception {
        var alerta = gerarParaReconhecimento();
        Long usuarioId = entityManager.createQuery("select u.id from Usuario u where u.email = :email", Long.class)
                .setParameter("email", "operador@powergrid.local").getSingleResult();
        when(clock.instant()).thenReturn(AGORA.plusSeconds(30));
        var result = mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(sessao)
                .header(csrf.headerName(), csrf.token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONHECIDO"))
                .andExpect(jsonPath("$.reconhecidoPorUsuarioId").value(usuarioId)).andReturn();
        var dto = objectMapper.readValue(result.getResponse().getContentAsString(),
                br.com.powergrid.controlcenter.dto.AlertaTensaoResponseDTO.class);
        assertThat(dto.reconhecidoEm().toInstant()).isEqualTo(AGORA.plusSeconds(30));
        assertThat(dto.reconhecidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        entityManager.flush(); entityManager.clear();
        var salvo = alertas.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAlerta.RECONHECIDO);
        assertThat(salvo.getReconhecidoPorUsuario().getId()).isEqualTo(usuarioId);
        assertThat(salvo.getReconhecidoEm().toInstant()).isEqualTo(dto.reconhecidoEm().toInstant());
        mockMvc.perform(get(base()).session(sessao).param("status", "ATIVO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(base()).session(sessao).param("status", "RECONHECIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(alerta.getId()));
    }

    @Test
    void devePreservarPrimeiroAutorEHorarioEmRepeticoes() throws Exception {
        var alerta = gerarParaReconhecimento();
        when(clock.instant()).thenReturn(AGORA.plusNanos(123456789));
        String primeira = mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(sessao)
                .header(csrf.headerName(), csrf.token())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        when(clock.instant()).thenReturn(AGORA.plusSeconds(120));
        mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isOk()).andExpect(content().json(primeira));
        autenticarGerente();
        mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(content().json(primeira));
        entityManager.flush(); entityManager.clear();
        mockMvc.perform(get(base() + "/" + alerta.getId()).session(sessao))
                .andExpect(status().isOk()).andExpect(content().json(primeira));
    }

    @Test
    void naoDeveAceitarAutoriaOuHorarioDoCorpo() throws Exception {
        var alerta = gerarParaReconhecimento();
        Long operador = entityManager.createQuery("select u.id from Usuario u where u.email = :email", Long.class)
                .setParameter("email", "operador@powergrid.local").getSingleResult();
        var payload = Map.of("reconhecidoPorUsuarioId", Long.MAX_VALUE, "reconhecidoEm", "2000-01-01T00:00:00Z");
        var result = mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(sessao)
                .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reconhecidoPorUsuarioId").value(operador)).andReturn();
        var dto = objectMapper.readValue(result.getResponse().getContentAsString(),
                br.com.powergrid.controlcenter.dto.AlertaTensaoResponseDTO.class);
        assertThat(dto.reconhecidoEm().toInstant()).isEqualTo(AGORA);
    }

    @Test
    void deveRetornar404AoReconhecerAlertaInexistente() throws Exception {
        mockMvc.perform(patch(reconhecimentoUrl(Long.MAX_VALUE)).session(sessao).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deveExigirSessaoECsrfNoReconhecimento() throws Exception {
        var alerta = gerarParaReconhecimento();
        mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(sessao)).andExpect(status().isForbidden());
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(patch(reconhecimentoUrl(alerta.getId())).session(anonima).header(token.headerName(), token.token()))
                .andExpect(status().isUnauthorized());
        entityManager.flush(); entityManager.clear();
        var salvo = alertas.findById(alerta.getId()).orElseThrow();
        assertThat(salvo.getStatus()).isEqualTo(StatusAlerta.ATIVO);
        assertThat(salvo.getReconhecidoPorUsuario()).isNull(); assertThat(salvo.getReconhecidoEm()).isNull();
    }

    private String reconhecimentoUrl(Long id) { return base() + "/" + id + "/reconhecimento"; }

    private void autenticarGerente() throws Exception {
        String email = entityManager.createQuery("select u.email from Usuario u where u.perfil = :perfil", String.class)
                .setParameter("perfil", PerfilAcesso.GERENTE_OPERACOES).getSingleResult();
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andExpect(status().isOk()).andReturn();
        csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }

    private AlertaTensao gerarParaReconhecimento() throws Exception {
        ingerir("90", 201);
        return unico();
    }

    private String base() { return "/api/alertas-tensao"; }
    private String ingestao() { return "/api/linhas-transmissao/" + linhaId + "/medicoes"; }
    private AlertaTensao unico() { return alertas.findAll().getFirst(); }
    private MedicaoLinhaResponseDTO ingerir(String tensao, int esperado) throws Exception {
        var req = Map.of("fonteId", fonte.getId(), "medidoEm", "2026-10-28T13:00:00Z", "tensaoKv", new BigDecimal(tensao));
        var result = mockMvc.perform(post(ingestao()).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(esperado)).andReturn();
        return esperado == 201 ? objectMapper.readValue(result.getResponse().getContentAsString(), MedicaoLinhaResponseDTO.class) : null;
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

    private FonteMedicao fonte() {
        FonteMedicao fonte = new FonteMedicao();
        fonte.setIdentificador("SENSOR-01");
        fonte.setAtivo(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }

}
