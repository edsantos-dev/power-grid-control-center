package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
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
class FontesConsultasControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private MedicaoLinhaRepository repository;
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
        linhaId = fonte.getLinha().getId();
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
    @ValueSource(strings = {"REAL", "SIMULADA"})
    void deveCadastrarFonte(String origem) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("identificador", "  NOVA-01  ", "origem", origem));
        mockMvc.perform(post(fontesUrl(linhaId)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.identificador").value("NOVA-01"))
                .andExpect(jsonPath("$.linhaId").value(linhaId)).andExpect(jsonPath("$.origem").value(origem));
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.createQuery("select count(f) from FonteMedicao f where f.identificador = 'NOVA-01'", Long.class)
                .getSingleResult()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"vazio", "longo", "semOrigem", "origemInvalida", "semIdentificador"})
    void deveRejeitarFonteInvalida(String caso) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("identificador", "NOVA"); req.put("origem", "REAL");
        switch (caso) {
            case "vazio" -> req.put("identificador", "  ");
            case "longo" -> req.put("identificador", "A".repeat(129));
            case "semOrigem" -> req.put("origem", null);
            case "origemInvalida" -> req.put("origem", "OUTRA");
            default -> req.put("identificador", null);
        }
        fontePost(linhaId, req, 400);
    }

    @Test
    void deveRejeitarDuplicidadeGlobalELinhaInexistente() throws Exception {
        var req = Map.of("identificador", "  SENSOR-01  ", "origem", "REAL");
        fontePost(linhaId, req, 409);
        fontePost(linha().getId(), req, 409);
        fontePost(Long.MAX_VALUE, Map.of("identificador", "NOVA", "origem", "REAL"), 404);
    }

    @Test
    void devePaginarOrdenarEDistinguirLinhas() throws Exception {
        Long antigo = medicao(fonte, "2026-10-28T10:00:00Z");
        Long primeiro = medicao(fonte, "2026-10-28T11:00:00Z");
        Long segundo = medicao(fonte, "2026-10-28T11:00:00Z");
        FonteMedicao outra = fonte(); outra.setIdentificador("OUTRA"); entityManager.persist(outra);
        medicao(outra, "2026-10-28T12:00:00Z");
        mockMvc.perform(get(url()).session(sessao).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(segundo)).andExpect(jsonPath("$.content[1].id").value(primeiro))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(false));
        mockMvc.perform(get(url()).session(sessao).param("size", "2").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(antigo))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get(url()).session(sessao).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void deveRetornarVazioComPadroesE404() throws Exception {
        mockMvc.perform(get(url()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/linhas-transmissao/{id}/medicoes", Long.MAX_VALUE).session(sessao))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(url()).session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get(url()).session(sessao).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "page|abc", "page|1.5", "page|2147483648", "size|0", "size|101",
            "size|-1", "size|abc", "size|1.5", "sort|medidoEm,desc", "dataInicio|invalido",
            "dataFim|2026-10-28T10:00:00"}, delimiter = '|')
    void deveRejeitarParametrosInvalidos(String campo, String valor) throws Exception {
        mockMvc.perform(get(url()).session(sessao).param(campo, valor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "dataInicio", "dataFim"})
    void deveRejeitarParametrosVaziosEDuplicados(String campo) throws Exception {
        mockMvc.perform(get(url()).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mockMvc.perform(get(url()).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveFiltrarInicioInclusivoFimExclusivoEOffsets() throws Exception {
        medicao(fonte, "2026-10-28T09:00:00Z");
        Long dentro = medicao(fonte, "2026-10-28T10:00:00Z");
        medicao(fonte, "2026-10-28T11:00:00Z");
        mockMvc.perform(get(url()).session(sessao).param("dataInicio", "2026-10-28T07:00:00-03:00")
                .param("dataFim", "2026-10-28T11:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(dentro));
        mockMvc.perform(get(url()).session(sessao).param("dataInicio", "2026-10-28T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(url()).session(sessao).param("dataFim", "2026-10-28T10:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-28T10:00:00Z", "2026-10-28T09:00:00Z"})
    void deveRejeitarPeriodoIgualOuInvertido(String fim) throws Exception {
        mockMvc.perform(get(url()).session(sessao).param("dataInicio", "2026-10-28T10:00:00Z").param("dataFim", fim))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveExigirAutenticacaoECsrf() throws Exception {
        mockMvc.perform(get(url())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(fontesUrl(linhaId)).session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("identificador", "NOVA", "origem", "REAL"))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"origem", "destino"})
    void deveCadastrarFonteComTerminalDeReferencia(String terminal) throws Exception {
        Long referencia = terminal.equals("origem") ? fonte.getLinha().getSubestacaoOrigem().getId()
                : fonte.getLinha().getSubestacaoDestino().getId();
        var request = Map.of("identificador", "COM-REFERENCIA", "origem", "REAL", "subestacaoReferenciaId", referencia);
        mockMvc.perform(post(fontesUrl(linhaId)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.subestacaoReferenciaId").value(referencia));
        entityManager.flush(); entityManager.clear();
        var salvo = entityManager.createQuery("select f from FonteMedicao f where f.identificador = :id", FonteMedicao.class)
                .setParameter("id", "COM-REFERENCIA").getSingleResult();
        assertThat(salvo.getSubestacaoReferencia().getId()).isEqualTo(referencia);
    }

    @Test
    void deveRejeitarReferenciaInexistenteOuForaDaLinha() throws Exception {
        fontePost(linhaId, Map.of("identificador", "NOVA", "origem", "REAL", "subestacaoReferenciaId", Long.MAX_VALUE), 404);
        fontePost(linhaId, Map.of("identificador", "NOVA", "origem", "REAL", "subestacaoReferenciaId", linha().getSubestacaoOrigem().getId()), 400);
        fontePost(linhaId, Map.of("identificador", "NOVA", "origem", "REAL", "subestacaoReferenciaId", 0), 400);
        fontePost(linhaId, Map.of("identificador", "NOVA", "origem", "REAL", "subestacaoReferenciaId", -1), 400);
    }

    private String url() { return "/api/linhas-transmissao/" + linhaId + "/medicoes"; }
    private String fontesUrl(Long id) { return "/api/linhas-transmissao/" + id + "/fontes"; }
    private void fontePost(Long id, Map<String, ?> req, int esperado) throws Exception {
        mockMvc.perform(post(fontesUrl(id)).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(esperado)).andExpect(jsonPath("$.status").value(esperado));
    }
    private Long medicao(FonteMedicao fonte, String instante) {
        MedicaoLinha m = new MedicaoLinha(); m.setFonte(fonte);
        m.setMedidoEm(OffsetDateTime.parse(instante)); m.setRecebidoEm(AGORA.atOffset(ZoneOffset.UTC));
        m.setTensaoKv(new BigDecimal("138"));
        return repository.saveAndFlush(m).getId();
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
        linha.setTensaoOperacaoKv(new BigDecimal("138"));
        entityManager.persist(linha);
        entityManager.flush();
        return linha;
    }

    private FonteMedicao fonte() {
        FonteMedicao fonte = new FonteMedicao();
        fonte.setIdentificador("SENSOR-01");
        fonte.setLinha(linha());
        fonte.setOrigem(OrigemMedicao.REAL);
        return fonte;
    }

}
