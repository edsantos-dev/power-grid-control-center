package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import br.com.powergrid.controlcenter.dto.AlertaResponseDTO;
import br.com.powergrid.controlcenter.repository.AlertaRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.model.Alerta;
import br.com.powergrid.controlcenter.model.Criticidade;
import br.com.powergrid.controlcenter.model.EscopoAlerta;
import br.com.powergrid.controlcenter.model.SituacaoAlerta;
import org.junit.jupiter.params.provider.EnumSource;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.TransformadorRepository;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;



import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;

import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AlertaControllerTest {

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private TransformadorRepository transformadorRepository;
	@Autowired private AlimentadorRepository alimentadorRepository;
	@Autowired private SubestacaoRepository subestacaoRepository;
	@Autowired private EntityManager entityManager;


	@Autowired private AlertaRepository repository;

	private MockHttpSession sessao;
	private CsrfResponse csrf;
	private Long alimentadorId;
	private Long subestacaoId;

	@BeforeEach
	void preparar() throws Exception {
		alimentadorId = salvarAlimentador();
		subestacaoId = alimentadorRepository.findById(alimentadorId).orElseThrow().getSubestacao().getId();
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
    @EnumSource(Criticidade.class)
    void deveCriarAlertaDeRedeComHorarioUtcESituacaoInicial(Criticidade criticidade) throws Exception {
        Map<String, Object> request = requestValido();
        request.put("criticidade", criticidade);
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);
        AlertaResponseDTO response = resposta(enviar(request, 201));
        OffsetDateTime depois = OffsetDateTime.now(ZoneOffset.UTC);
        assertThat(response.id()).isPositive();
        assertThat(response.tipo()).isEqualTo("Falha de comunicação");
        assertThat(response.criticidade()).isEqualTo(criticidade);
        assertThat(response.escopo()).isEqualTo(EscopoAlerta.REDE_MONITORADA);
        assertThat(response.ativoId()).isNull();
        assertThat(response.situacao()).isEqualTo(SituacaoAlerta.ABERTO);
        assertThat(response.geradoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(response.geradoEm().toInstant()).isBetween(antes.toInstant(), depois.toInstant());
        entityManager.flush();
        entityManager.clear();
        Alerta salvo = repository.findById(response.id()).orElseThrow();
        assertThat(salvo.getTipo()).isEqualTo(response.tipo());
        assertThat(salvo.getAtivo()).isNull();
        mockMvc.perform(get("/api/alertas").session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(response.id()))
                .andExpect(jsonPath("$[0].tipo").value(response.tipo()))
                .andExpect(jsonPath("$[0].ativo").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR"})
    void deveAceitarAtivosDosTresSubtipos(String tipo) throws Exception {
        Long id = tipo.equals("SUBESTACAO") ? subestacaoId : alimentadorId;
        if (tipo.equals("TRANSFORMADOR")) {
            var transformador = new br.com.powergrid.controlcenter.model.Transformador();
            transformador.setNome("Transformador");
            transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            transformador.setCapacidadeMva(new BigDecimal("5"));
            transformador.setAlimentador(alimentadorRepository.findById(alimentadorId).orElseThrow());
            id = transformadorRepository.saveAndFlush(transformador).getId();
        }
        Map<String, Object> request = requestValido();
        request.put("escopo", "ATIVO");
        request.put("ativoId", id);
        AlertaResponseDTO response = resposta(enviar(request, 201));
        assertThat(response.ativoId()).isEqualTo(id);
        assertThat(response.escopo()).isEqualTo(EscopoAlerta.ATIVO);
        entityManager.flush();
        entityManager.clear();
        assertThat(repository.findById(response.id()).orElseThrow().getAtivo().getId()).isEqualTo(id);
    }

    @Test
    void deveRejeitarAtivoSemId() throws Exception {
        Map<String, Object> request = requestValido();
        request.put("escopo", "ATIVO");
        rejeitar(request, 400);
        request.put("ativoId", null);
        rejeitar(request, 400);
    }

    @Test
    void deveRejeitarRedeComIdMesmoSeInexistente() throws Exception {
        Map<String, Object> request = requestValido();
        request.put("ativoId", alimentadorId);
        rejeitar(request, 400);
        request.put("ativoId", Long.MAX_VALUE);
        rejeitar(request, 400);
    }

    @Test
    void deveRetornar404ParaAtivoInexistente() throws Exception {
        Map<String, Object> request = requestValido();
        request.put("escopo", "ATIVO");
        request.put("ativoId", Long.MAX_VALUE);
        rejeitar(request, 404);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void deveRejeitarIdNaoPositivo(Long id) throws Exception {
        Map<String, Object> request = requestValido();
        request.put("escopo", "ATIVO");
        request.put("ativoId", id);
        rejeitar(request, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tipo", "criticidade", "escopo"})
    void deveRejeitarObrigatoriosNulos(String campo) throws Exception {
        Map<String, Object> request = requestValido();
        request.put(campo, null);
        rejeitar(request, 400);
    }

    @Test
    void deveRejeitarTipoVazioOuLongo() throws Exception {
        for (String tipo : new String[] {"   ", "A".repeat(129)}) {
            Map<String, Object> request = requestValido();
            request.put("tipo", tipo);
            rejeitar(request, 400);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"criticidade", "escopo"})
    void deveRejeitarEnumDesconhecido(String campo) throws Exception {
        Map<String, Object> request = requestValido();
        request.put(campo, "INVALIDO");
        rejeitar(request, 400);
    }

    @Test
    void deveIgnorarCamposGeradosEnviadosPeloCliente() throws Exception {
        Map<String, Object> request = requestValido();
        request.put("id", Long.MAX_VALUE);
        request.put("geradoEm", "2000-01-01T00:00:00Z");
        request.put("situacao", "RESOLVIDO");
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);
        AlertaResponseDTO response = resposta(enviar(request, 201));
        assertThat(response.id()).isNotEqualTo(Long.MAX_VALUE);
        assertThat(response.situacao()).isEqualTo(SituacaoAlerta.ABERTO);
        assertThat(response.geradoEm().toInstant()).isAfterOrEqualTo(antes.toInstant());
    }

    @Test
    void deveListarSomenteAtivosNaOrdemDePrioridadeRecenciaEId() throws Exception {
        mockMvc.perform(get("/api/alertas").session(sessao))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        Alerta baixa = salvar(Criticidade.BAIXA, SituacaoAlerta.ABERTO, "2026-10-26T15:00:00Z");
        Alerta criticaAntiga = salvar(Criticidade.CRITICA, SituacaoAlerta.RECONHECIDO, "2026-10-24T13:00:00Z");
        Alerta alta = salvar(Criticidade.ALTA, SituacaoAlerta.ABERTO, "2026-10-26T14:00:00Z");
        Alerta criticaNova = salvar(Criticidade.CRITICA, SituacaoAlerta.ABERTO, "2026-10-25T10:00:00-03:00");
        Alerta media = salvar(Criticidade.MEDIA, SituacaoAlerta.RECONHECIDO, "2026-10-26T15:00:00Z");
        Alerta criticaEmpatada = salvar(Criticidade.CRITICA, SituacaoAlerta.RECONHECIDO, "2026-10-25T13:00:00Z");
        alta.setEscopo(EscopoAlerta.ATIVO);
        alta.setAtivo(alimentadorRepository.findById(alimentadorId).orElseThrow());
        salvar(Criticidade.CRITICA, SituacaoAlerta.RESOLVIDO, "2026-10-27T13:00:00Z");
        entityManager.flush();
        entityManager.clear();
        MvcResult result = mockMvc.perform(get("/api/alertas").session(sessao))
                .andExpect(status().isOk()).andReturn();
        AlertaResponseDTO[] lista = objectMapper.readValue(result.getResponse().getContentAsString(), AlertaResponseDTO[].class);
        assertThat(lista).extracting(AlertaResponseDTO::id).containsExactly(
                criticaEmpatada.getId(), criticaNova.getId(), criticaAntiga.getId(),
                alta.getId(), media.getId(), baixa.getId());
        assertThat(lista[3].ativoId()).isEqualTo(alimentadorId);
        assertThat(lista[0].ativoId()).isNull();
    }

    @Test
    void deveRetornarListaVaziaQuandoTodosResolvidos() throws Exception {
        salvar(Criticidade.CRITICA, SituacaoAlerta.RESOLVIDO, "2026-10-25T13:00:00Z");
        mockMvc.perform(get("/api/alertas").session(sessao))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void deveExigirSessaoECsrf() throws Exception {
        mockMvc.perform(get("/api/alertas")).andExpect(status().isUnauthorized());
        MvcResult inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        MockHttpSession anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        CsrfResponse token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/alertas").session(anonima).header(token.headerName(), token.token())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/alertas").session(sessao)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/alertas").session(sessao).header(csrf.headerName(), "invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(requestValido())))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    private Alerta salvar(Criticidade criticidade, SituacaoAlerta situacao, String instante) {
        Alerta alerta = new Alerta();
        alerta.setTipo("Falha de comunicação");
        alerta.setCriticidade(criticidade);
        alerta.setSituacao(situacao);
        alerta.setEscopo(EscopoAlerta.REDE_MONITORADA);
        alerta.setGeradoEm(OffsetDateTime.parse(instante));
        return repository.saveAndFlush(alerta);
    }

    private void rejeitar(Map<String, Object> request, int esperado) throws Exception {
        enviar(request, esperado);
        assertThat(repository.count()).isZero();
    }

    private MvcResult enviar(Map<String, Object> request, int esperado) throws Exception {
        return mockMvc.perform(post("/api/alertas").session(sessao).header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(esperado)).andReturn();
    }

    private AlertaResponseDTO resposta(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), AlertaResponseDTO.class);
    }

    private Map<String, Object> requestValido() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("tipo", "  Falha de comunicação  ");
        request.put("criticidade", "ALTA");
        request.put("escopo", "REDE_MONITORADA");
        return request;
    }
	private Long salvarAlimentador() {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome("Subestação Central");
		subestacao.setLatitude(new BigDecimal("-25.4284001"));
		subestacao.setLongitude(new BigDecimal("-49.2733001"));
		subestacao.setTensaoNominalKv(new BigDecimal("138"));
		subestacao.setCapacidadeMva(new BigDecimal("100"));
		subestacao.setDataInstalacao(LocalDate.of(2099, 1, 1));
		subestacao.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
		subestacaoRepository.saveAndFlush(subestacao);

		Alimentador alimentador = new Alimentador();
		alimentador.setNome("Alimentador Central");
		alimentador.setLatitude(new BigDecimal("-25.4284002"));
		alimentador.setLongitude(new BigDecimal("-49.2733002"));
		alimentador.setTensaoNominalKv(new BigDecimal("13.8"));
		alimentador.setDataInstalacao(LocalDate.of(2099, 1, 2));
		alimentador.setEstadoOperacional(EstadoOperacional.MANUTENCAO);
		alimentador.setSubestacao(subestacao);
		return alimentadorRepository.saveAndFlush(alimentador).getId();
	}

}
