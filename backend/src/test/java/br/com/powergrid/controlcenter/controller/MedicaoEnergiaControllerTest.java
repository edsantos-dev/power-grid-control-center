package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.HashMap;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
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
class MedicaoEnergiaControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00.123456Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private MedicaoEnergiaAlimentadorRepository medicoes;
    @MockitoBean private Clock clock;
    private Alimentador alimentador;
    private FonteMedicao fonte;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        alimentador = alimentador(); fonte = fonte(alimentador); autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3.123456", "99.999999"})
    void deveConfigurarPercentualComPrecisaoIntegral(String valor) throws Exception {
        configurar(valor, 200); em.flush(); em.clear();
        assertThat(em.find(Alimentador.class, alimentador.getId()).getPercentualPerdaTecnica()).isEqualByComparingTo(valor);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveBloquearConfiguracaoParaOutrosPerfis(PerfilAcesso perfil) throws Exception {
        autenticar(perfil); configurar("3", 403); em.clear();
        assertThat(em.find(Alimentador.class, alimentador.getId()).getPercentualPerdaTecnica()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.000001", "100", "100.000001", "3.1234567"})
    void deveRejeitarPercentualForaDosLimitesOuPrecisao(String valor) throws Exception { configurar(valor, 400); }

    @Test
    void deveExigirPercentualNaConfiguracaoEConfiguracaoAntesDaIngestao() throws Exception {
        mvc.perform(patch(base() + "/parametros-perdas").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        enviar(fonte.getId(), corpo(), 409); assertThat(medicoes.count()).isZero();
        assertThat(em.find(FonteMedicao.class, fonte.getId()).getUltimaComunicacaoEm()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void deveIngerirComSnapshotEAtualizarComunicacaoNaMesmaTransacao(PerfilAcesso perfil) throws Exception {
        configurar("3.123456", 200); autenticar(perfil);
        var resultado = enviar(fonte.getId(), corpo(), 201);
        var dto = mapper.readValue(resultado.getResponse().getContentAsString(), MedicaoEnergiaResponseDTO.class);
        assertThat(dto.alimentadorId()).isEqualTo(alimentador.getId()); assertThat(dto.fonteId()).isEqualTo(fonte.getId());
        assertThat(dto.percentualPerdaReferencia()).isEqualByComparingTo("3.123456");
        assertThat(dto.energiaInjetadaMwh()).isEqualByComparingTo("100.123456");
        assertThat(dto.recebidoEm().toInstant()).isEqualTo(AGORA); assertThat(dto.recebidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        em.clear();
        assertThat(medicoes.findById(dto.id()).orElseThrow().getPercentualPerdaReferencia()).isEqualByComparingTo("3.123456");
        assertThat(em.find(FonteMedicao.class, fonte.getId()).getUltimaComunicacaoEm().toInstant()).isEqualTo(AGORA);
    }

    @Test
    void devePreservarSnapshotsAnterioresEPermitirIntervalosAdjacentes() throws Exception {
        configurar("3.123456", 200);
        var a = mapper.readValue(enviar(fonte.getId(), corpo(), 201).getResponse().getContentAsString(), MedicaoEnergiaResponseDTO.class);
        configurar("8.654321", 200);
        var proxima = corpo(); proxima.put("inicioPeriodo", AGORA.minusSeconds(3600).toString()); proxima.put("fimPeriodo", AGORA.toString());
        var b = mapper.readValue(enviar(fonte.getId(), proxima, 201).getResponse().getContentAsString(), MedicaoEnergiaResponseDTO.class);
        em.clear();
        assertThat(medicoes.findById(a.id()).orElseThrow().getPercentualPerdaReferencia()).isEqualByComparingTo("3.123456");
        assertThat(b.percentualPerdaReferencia()).isEqualByComparingTo("8.654321"); assertThat(medicoes.count()).isEqualTo(2);
    }

    @Test
    void deveRestabelecerAlertaDeComunicacaoComMesmoInstanteDoRecebimento() throws Exception {
        configurar("3", 200);
        var alerta = new AlertaComunicacao(); alerta.setFonte(fonte); alerta.setAtivo(alimentador);
        alerta.setMonitoradaDesde(AGORA.minusSeconds(1000).atOffset(ZoneOffset.UTC)); alerta.setToleranciaSegundos(300);
        alerta.setDetectadoEm(AGORA.minusSeconds(10).atOffset(ZoneOffset.UTC)); alerta.setStatus(StatusComunicacao.ATIVO);
        alerta.setCriticidade(Criticidade.MEDIA); em.persist(alerta); em.flush();
        enviar(fonte.getId(), corpo(), 201); em.clear();
        var salvo = em.find(AlertaComunicacao.class, alerta.getId());
        assertThat(salvo.getStatus()).isEqualTo(StatusComunicacao.RESTABELECIDO);
        assertThat(salvo.getRestabelecidoEm().toInstant()).isEqualTo(AGORA);
    }

    @Test
    void deveBloquearSobreposicaoInclusiveEntreFontesDiferentes() throws Exception {
        configurar("3", 200); enviar(fonte.getId(), corpo(), 201);
        var outraFonte = fonte(alimentador); enviar(outraFonte.getId(), corpo(), 409);
        enviar(fonte.getId(), corpo(), 409);
        assertThat(medicoes.count()).isEqualTo(1);
        assertThat(em.find(FonteMedicao.class, outraFonte.getId()).getUltimaComunicacaoEm()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"alimentador", "fonte", "vinculo"})
    void deveValidarReferenciasEVinculo(String caso) throws Exception {
        configurar("3", 200);
        long id = caso.equals("alimentador") ? Long.MAX_VALUE : alimentador.getId();
        long fonteId = caso.equals("fonte") ? Long.MAX_VALUE : fonte.getId();
        if (caso.equals("vinculo")) fonteId = fonte(alimentador()).getId();
        mvc.perform(post("/api/alimentadores/{id}/fontes/{fonteId}/medicoes-energia", id, fonteId).session(sessao)
                .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo())))
                .andExpect(status().is(caso.equals("vinculo") ? 400 : 404));
        assertThat(medicoes.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inicioPeriodo", "fimPeriodo", "energiaInjetadaMwh"})
    void deveExigirCamposDaMedicao(String campo) throws Exception {
        configurar("3", 200); var corpo = corpo(); corpo.put(campo, null); enviar(fonte.getId(), corpo, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"igual", "invertido", "futuro", "inicioFuturo", "energiaNegativa", "energiaPrecisao", "energiaInteiros", "semFuso", "submicro"})
    void deveRejeitarMedicaoInvalida(String caso) throws Exception {
        configurar("3", 200); var corpo = corpo();
        switch (caso) {
            case "igual" -> corpo.put("fimPeriodo", corpo.get("inicioPeriodo"));
            case "invertido" -> corpo.put("fimPeriodo", AGORA.minusSeconds(10000).toString());
            case "futuro" -> corpo.put("fimPeriodo", AGORA.plusNanos(1).toString());
            case "inicioFuturo" -> { corpo.put("inicioPeriodo", AGORA.plusSeconds(1).toString()); corpo.put("fimPeriodo", AGORA.plusSeconds(2).toString()); }
            case "energiaNegativa" -> corpo.put("energiaInjetadaMwh", new BigDecimal("-0.000001"));
            case "energiaPrecisao" -> corpo.put("energiaInjetadaMwh", new BigDecimal("1.1234567"));
            case "energiaInteiros" -> corpo.put("energiaInjetadaMwh", new BigDecimal("10000000000000"));
            case "semFuso" -> corpo.put("inicioPeriodo", "2026-11-01T00:00:00");
            default -> { corpo.put("inicioPeriodo", AGORA.minusSeconds(1).toString()); corpo.put("fimPeriodo", AGORA.minusSeconds(1).plusNanos(1).toString()); }
        }
        enviar(fonte.getId(), corpo, 400); assertThat(medicoes.count()).isZero();
    }

    @Test
    void deveAceitarEnergiaZero() throws Exception {
        configurar("0", 200); var corpo = corpo(); corpo.put("energiaInjetadaMwh", BigDecimal.ZERO);
        enviar(fonte.getId(), corpo, 201);
    }

    @Test
    void deveBloquearIngestaoPorTecnicoEExigirCsrfESessao() throws Exception {
        configurar("3", 200); autenticar(PerfilAcesso.TECNICO_MANUTENCAO); enviar(fonte.getId(), corpo(), 403);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        mvc.perform(patch(base() + "/parametros-perdas").session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("percentualPerdaTecnica", 3)))).andExpect(status().isForbidden());
        mvc.perform(post(base() + "/fontes/" + fonte.getId() + "/medicoes-energia").session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo()))).andExpect(status().isForbidden());
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post(base() + "/fontes/" + fonte.getId() + "/medicoes-energia").session(anonima).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo()))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void deveCadastrarFontePelaApiComUnicidadeGlobal(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var corpo = Map.of("identificador", "  FONTE-ENERGIA-API  ", "origem", "SIMULADA");
        mvc.perform(post(base() + "/fontes").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.identificador").value("FONTE-ENERGIA-API"))
                .andExpect(jsonPath("$.alimentadorId").value(alimentador.getId()));
        mvc.perform(post(base() + "/fontes").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo))).andExpect(status().isConflict());
    }

    @Test
    void deveValidarCadastroDaFonteEAcesso() throws Exception {
        mvc.perform(post(base() + "/fontes").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("identificador", "FONTE", "origem", "SIMULADA", "subestacaoReferenciaId", 1))))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base() + "/fontes").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/alimentadores/{id}/parametros-perdas", Long.MAX_VALUE).session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("percentualPerdaTecnica", 3))))
                .andExpect(status().isNotFound());
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO);
        mvc.perform(post(base() + "/fontes").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("identificador", "FONTE", "origem", "SIMULADA")))).andExpect(status().isForbidden());
    }

    private void configurar(String valor, int status) throws Exception {
        mvc.perform(patch(base() + "/parametros-perdas").session(sessao).header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("percentualPerdaTecnica", new BigDecimal(valor)))))
                .andExpect(status().is(status));
    }

    private org.springframework.test.web.servlet.MvcResult enviar(Long fonteId, Map<String, Object> corpo, int status) throws Exception {
        return mvc.perform(post(base() + "/fontes/" + fonteId + "/medicoes-energia").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo))).andExpect(status().is(status)).andReturn();
    }

    private Map<String, Object> corpo() {
        return new HashMap<>(Map.of("inicioPeriodo", AGORA.minusSeconds(7200).atOffset(ZoneOffset.ofHours(-3)).toString(),
                "fimPeriodo", AGORA.minusSeconds(3600).toString(), "energiaInjetadaMwh", new BigDecimal("100.123456")));
    }
    private String base() { return "/api/alimentadores/" + alimentador.getId(); }
    private Alimentador alimentador() {
        var sub = new Subestacao(); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub);
        var a = new Alimentador(); a.setSubestacao(sub); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(a); em.flush(); return a;
    }
    private FonteMedicao fonte(Alimentador a) {
        var f = new FonteMedicao(); f.setAtivo(a); f.setOrigem(OrigemMedicao.SIMULADA);
        f.setIdentificador("FONTE-ENERGIA-" + java.util.UUID.randomUUID()); f.setMonitoradaDesde(AGORA.minusSeconds(10000).atOffset(ZoneOffset.UTC));
        em.persist(f); em.flush(); return f;
    }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        var usuario = usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow();
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario.getEmail()).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
