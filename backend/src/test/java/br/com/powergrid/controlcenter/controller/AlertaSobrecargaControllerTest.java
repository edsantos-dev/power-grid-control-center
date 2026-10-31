package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.MedicaoTransformadorRepository;
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
class AlertaSobrecargaControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private MedicaoTransformadorRepository repository;
    @Autowired private br.com.powergrid.controlcenter.repository.AlertaSobrecargaRepository alertas;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-29T12:00:00Z");
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private FonteMedicao fonte;
    private Long transformadorId;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        fonte = fonte();
        entityManager.persist(fonte);
        entityManager.flush();
        transformadorId = fonte.getAtivo().getId();
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



    @Test
    void deveConsultarDetalheEPreservarSnapshot() throws Exception {
        var alerta = gerar("10.8", fonte);
        entityManager.find(Transformador.class, transformadorId).setCapacidadeMva(new BigDecimal("20"));
        entityManager.flush(); entityManager.clear();
        mockMvc.perform(get("/api/alertas-sobrecarga/{id}", alerta.getId()).session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ativoId").value(transformadorId))
                .andExpect(jsonPath("$.medicaoId").value(alerta.getMedicao().getId()))
                .andExpect(jsonPath("$.cargaPercentual").value(108)).andExpect(jsonPath("$.limiarPercentual").value(100))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.reconhecidoEm").isEmpty()).andExpect(jsonPath("$.reconhecidoPorUsuarioId").isEmpty());
        mockMvc.perform(get("/api/alertas-sobrecarga/{id}", Long.MAX_VALUE).session(sessao))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void devePaginarOrdenarEFiltrarPorAtivoStatusEPeriodoDeGeracao() throws Exception {
        when(clock.instant()).thenReturn(AGORA.minusSeconds(10)); var antigo = gerar("10.8", fonte);
        when(clock.instant()).thenReturn(AGORA); var novo = gerar("11", fonte);
        var outro = gerar("12", outraFonte());
        novo.setStatus(StatusAlerta.RECONHECIDO); novo.setReconhecidoEm(AGORA.atOffset(ZoneOffset.UTC));
        novo.setReconhecidoPorUsuario(entityManager.createQuery("select u from Usuario u where u.email = :email", Usuario.class)
                .setParameter("email", "operador@powergrid.local").getSingleResult()); entityManager.flush();
        mockMvc.perform(get(base()).session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(outro.getId()))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true));
        mockMvc.perform(get(base()).session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(novo.getId()));
        mockMvc.perform(get(base()).session(sessao).param("ativoId", transformadorId.toString()).param("status", "ATIVO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(antigo.getId()));
        mockMvc.perform(get(base()).session(sessao).param("status", "RECONHECIDO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].reconhecidoPorUsuarioId").isNumber());
        mockMvc.perform(get(base()).session(sessao).param("dataInicio", AGORA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(base()).session(sessao).param("dataFim", AGORA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base()).session(sessao).param("ativoId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void deveRetornarVazioComPadroesEPaginaDistante() throws Exception {
        mockMvc.perform(get(base()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
        gerar("10.8", fonte);
        mockMvc.perform(get(base()).session(sessao).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "page|abc", "page|2147483648", "size|0", "size|101", "sort|geradoEm,asc",
            "ativoId|0", "ativoId|-1", "ativoId|abc", "ativoId|9223372036854775808", "status|OUTRO", "status|ativo",
            "dataInicio|invalido", "dataFim|2026-10-29T12:00:00"}, delimiter = '|')
    void deveRejeitarFiltrosInvalidos(String campo, String valor) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, valor))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "ativoId", "status", "dataInicio", "dataFim"})
    void deveRejeitarValoresVaziosEDuplicados(String campo) throws Exception {
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

    private String base() { return "/api/alertas-sobrecarga"; }
    private AlertaSobrecarga gerar(String potencia, FonteMedicao f) throws Exception {
        var req = Map.of("fonteId", f.getId(), "medidoEm", "2026-10-28T10:00:00-03:00", "potenciaAparenteMva", new BigDecimal(potencia));
        var result = mockMvc.perform(post("/api/transformadores/{id}/medicoes", f.getAtivo().getId()).session(sessao)
                .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated()).andReturn();
        var dto = objectMapper.readValue(result.getResponse().getContentAsString(), MedicaoTransformadorResponseDTO.class);
        return entityManager.createQuery("select a from AlertaSobrecarga a where a.medicao.id = :id", AlertaSobrecarga.class)
                .setParameter("id", dto.id()).getSingleResult();
    }
    private FonteMedicao outraFonte() {
        Transformador t = new Transformador(); t.setNome("Outro"); t.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        t.setCapacidadeMva(new BigDecimal("10"));
        t.setAlimentador(entityManager.find(Transformador.class, transformadorId).getAlimentador()); entityManager.persist(t);
        FonteMedicao f = new FonteMedicao(); f.setIdentificador("OUTRA-FONTE"); f.setOrigem(OrigemMedicao.SIMULADA);
        f.setAtivo(t); entityManager.persist(f); entityManager.flush(); return f;
    }
    private FonteMedicao fonte() {
        Subestacao subestacao = new Subestacao();
        subestacao.setNome("Subestação"); subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        entityManager.persist(subestacao);
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(subestacao); entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador"); transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setAlimentador(alimentador); transformador.setCapacidadeMva(new BigDecimal("10"));
        entityManager.persist(transformador);
        FonteMedicao fonte = new FonteMedicao(); fonte.setIdentificador("SIMULADOR-TRAFO");
        fonte.setOrigem(OrigemMedicao.SIMULADA); fonte.setAtivo(transformador);
        entityManager.persist(fonte); entityManager.flush(); return fonte;
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

}
