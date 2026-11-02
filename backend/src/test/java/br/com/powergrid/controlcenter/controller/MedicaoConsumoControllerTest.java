package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.support.ConsumoMedidorFixture;
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
class MedicaoConsumoControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00.123456Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private UsuarioRepository usuarios;
    @MockitoBean private Clock clock;
    private ConsumoMedidorFixture.Dados dados;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        dados = ConsumoMedidorFixture.criar(em, false); autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void deveCriarFontesComIdentificadoresGeradosDistintos(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var a = criarFonte(dados.medidorId(), Map.of("origem", "SIMULADA", "identificador", "NAO-CONFIAR"), 201);
        var b = criarFonte(dados.medidorId(), Map.of("origem", "REAL"), 201);
        var primeira = mapper.readValue(a.getResponse().getContentAsString(), FonteMedidorResponseDTO.class);
        var segunda = mapper.readValue(b.getResponse().getContentAsString(), FonteMedidorResponseDTO.class);
        assertThat(primeira.identificador()).startsWith("MEDIDOR-").isNotEqualTo("NAO-CONFIAR").isNotEqualTo(segunda.identificador());
        assertThat(primeira.medidorId()).isEqualTo(dados.medidorId()); assertThat(primeira.monitoradaDesde().toInstant()).isEqualTo(AGORA);
        assertThat(primeira.ultimaComunicacaoEm()).isNull(); assertThat(segunda.origem()).isEqualTo(OrigemMedicao.REAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"estoque", "consumidor", "transformador", "data"})
    void deveBloquearFonteEIngestaoSemInstalacaoCompleta(String campo) throws Exception {
        var medidor = em.find(MedidorInteligente.class, dados.medidorId());
        if (campo.equals("estoque") || campo.equals("consumidor")) medidor.setConsumidor(null);
        if (campo.equals("estoque") || campo.equals("transformador")) medidor.setTransformador(null);
        if (campo.equals("estoque") || campo.equals("data")) medidor.setDataInstalacao(null);
        em.flush(); criarFonte(dados.medidorId(), Map.of("origem", "SIMULADA"), 409); enviar(dados.medidorId(), corpo(), 409);
        assertThat(em.find(FonteMedicao.class, dados.fonteId()).getUltimaComunicacaoEm()).isNull();
    }

    @Test
    void deveValidarOrigemEMedidorNoCadastroDeFonte() throws Exception {
        criarFonte(dados.medidorId(), Map.of(), 400); criarFonte(dados.medidorId(), Map.of("origem", "INVALIDA"), 400);
        criarFonte(Long.MAX_VALUE, Map.of("origem", "SIMULADA"), 409);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"GERENTE_OPERACOES", "OPERADOR"})
    void deveIngerirConsultarEPersistirSnapshotsSemConfiarNoCliente(PerfilAcesso perfil) throws Exception {
        autenticar(perfil); var corpo = corpo(); corpo.put("consumidorId", Long.MAX_VALUE); corpo.put("transformadorId", Long.MAX_VALUE);
        corpo.put("alimentadorId", Long.MAX_VALUE); corpo.put("subestacaoId", Long.MAX_VALUE); corpo.put("recebidoEm", "2000-01-01T00:00:00Z");
        var resultado = enviar(dados.medidorId(), corpo, 201);
        var dto = mapper.readValue(resultado.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
        assertThat(dto.consumidorId()).isEqualTo(dados.consumidorId()); assertThat(dto.transformadorId()).isEqualTo(dados.transformadorId());
        assertThat(dto.alimentadorId()).isEqualTo(dados.alimentadorId()); assertThat(dto.subestacaoId()).isEqualTo(dados.subestacaoId());
        assertThat(dto.consumoKwh()).isEqualByComparingTo("100.123456"); assertThat(dto.recebidoEm().toInstant()).isEqualTo(AGORA);
        assertThat(dto.inicioPeriodo().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(resultado.getResponse().getHeader("Location")).isEqualTo("http://localhost" + base(dados.medidorId()) + "/medicoes-consumo/" + dto.id());
        em.clear(); var salva = em.find(MedicaoConsumoMedidor.class, dto.id());
        assertThat(salva.getConsumidorId()).isEqualTo(dados.consumidorId()); assertThat(salva.getAlimentadorId()).isEqualTo(dados.alimentadorId());
        assertThat(em.find(FonteMedicao.class, dados.fonteId()).getUltimaComunicacaoEm().toInstant()).isEqualTo(AGORA);
        mvc.perform(get(resultado.getResponse().getHeader("Location")).session(sessao)).andExpect(status().isOk())
                .andExpect(content().json(resultado.getResponse().getContentAsString()));
    }

    @Test
    void deveRestabelecerAlertaUsandoMesmoInstanteDoRecebimento() throws Exception {
        var alerta = ConsumoMedidorFixture.alerta(em, em.find(FonteMedicao.class, dados.fonteId())); em.flush();
        var r = enviar(dados.medidorId(), corpo(), 201); var dto = mapper.readValue(r.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
        em.clear(); var salvo = em.find(AlertaComunicacao.class, alerta.getId());
        assertThat(salvo.getStatus()).isEqualTo(StatusComunicacao.RESTABELECIDO);
        assertThat(salvo.getRestabelecidoEm()).isEqualTo(dto.recebidoEm());
    }

    @Test
    void devePreservarSnapshotsNaConsultaAposMudancaCadastral() throws Exception {
        var r = enviar(dados.medidorId(), corpo(), 201); var dto = mapper.readValue(r.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
        var outro = ConsumoMedidorFixture.criar(em, false);
        var medidor = em.find(MedidorInteligente.class, dados.medidorId());
        medidor.setConsumidor(em.find(Consumidor.class, outro.consumidorId())); medidor.setTransformador(em.find(Transformador.class, outro.transformadorId())); em.flush(); em.clear();
        mvc.perform(get(base(dados.medidorId()) + "/medicoes-consumo/" + dto.id()).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.consumidorId").value(dados.consumidorId())).andExpect(jsonPath("$.transformadorId").value(dados.transformadorId()))
                .andExpect(jsonPath("$.alimentadorId").value(dados.alimentadorId())).andExpect(jsonPath("$.subestacaoId").value(dados.subestacaoId()));
    }

    @Test
    void deveBloquearDuplicidadeESobreposicaoEntreFontesSemConfirmarContatoRejeitado() throws Exception {
        enviar(dados.medidorId(), corpo(), 201); enviar(dados.medidorId(), corpo(), 409);
        var outro = corpo(); outro.put("fonteId", dados.segundaFonteId()); enviar(dados.medidorId(), outro, 409);
        assertThat(em.find(FonteMedicao.class, dados.segundaFonteId()).getUltimaComunicacaoEm()).isNull();
        var adjacente = corpo(); adjacente.put("inicioPeriodo", AGORA.minusSeconds(3600).toString()); adjacente.put("fimPeriodo", AGORA.toString());
        enviar(dados.medidorId(), adjacente, 201);
    }

    @Test
    void deveRejeitarPeriodoAntesDaInstalacaoEmUtcEAceitarBordaExata() throws Exception {
        var medidor = em.find(MedidorInteligente.class, dados.medidorId()); medidor.setDataInstalacao(LocalDate.of(2026, 11, 2)); em.flush();
        var req = corpo(); req.put("inicioPeriodo", "2026-11-01T23:59:59.999999Z"); req.put("fimPeriodo", "2026-11-02T01:00:00Z");
        enviar(dados.medidorId(), req, 409); assertThat(em.find(FonteMedicao.class, dados.fonteId()).getUltimaComunicacaoEm()).isNull();
        req.put("inicioPeriodo", "2026-11-01T21:00:00-03:00"); enviar(dados.medidorId(), req, 201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fonteId", "inicioPeriodo", "fimPeriodo", "consumoKwh"})
    void deveExigirCamposObrigatorios(String campo) throws Exception {
        var r = corpo(); r.put(campo, null); enviar(dados.medidorId(), r, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"igual", "invertido", "futuro", "inicioFuturo", "negativo", "fracao", "inteiros", "semFuso", "submicro", "fonteZero"})
    void deveRejeitarContratoInvalidoSemContato(String caso) throws Exception {
        var r = corpo();
        switch (caso) {
            case "igual" -> r.put("fimPeriodo", r.get("inicioPeriodo"));
            case "invertido" -> r.put("fimPeriodo", AGORA.minusSeconds(10000).toString());
            case "futuro" -> r.put("fimPeriodo", AGORA.plusNanos(1).toString());
            case "inicioFuturo" -> { r.put("inicioPeriodo", AGORA.plusSeconds(1).toString()); r.put("fimPeriodo", AGORA.plusSeconds(2).toString()); }
            case "negativo" -> r.put("consumoKwh", "-0.000001");
            case "fracao" -> r.put("consumoKwh", "1.1234567");
            case "inteiros" -> r.put("consumoKwh", "10000000000000");
            case "semFuso" -> r.put("inicioPeriodo", "2026-11-02T10:00:00");
            case "fonteZero" -> r.put("fonteId", 0);
            default -> { r.put("inicioPeriodo", AGORA.minusSeconds(1).toString()); r.put("fimPeriodo", AGORA.minusSeconds(1).plusNanos(1).toString()); }
        }
        enviar(dados.medidorId(), r, 400); assertThat(em.find(FonteMedicao.class, dados.fonteId()).getUltimaComunicacaoEm()).isNull();
    }

    @Test
    void deveAceitarZero() throws Exception {
        var r = corpo(); r.put("consumoKwh", 0); enviar(dados.medidorId(), r, 201);
    }

    @Test
    void deveValidarReferenciasEVinculoDaFonte() throws Exception {
        enviar(Long.MAX_VALUE, corpo(), 404); var req = corpo(); req.put("fonteId", Long.MAX_VALUE); enviar(dados.medidorId(), req, 404);
        var outro = ConsumoMedidorFixture.criar(em, false); req.put("fonteId", outro.fonteId()); enviar(dados.medidorId(), req, 400);
        var f = new FonteMedicao(); f.setAtivo(em.find(Transformador.class, dados.transformadorId())); f.setOrigem(OrigemMedicao.SIMULADA);
        f.setIdentificador("FONTE-TRANSFORMADOR-CONSUMO"); em.persist(f); em.flush(); req.put("fonteId", f.getId()); enviar(dados.medidorId(), req, 400);
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultaAutenticadaParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        var result = enviar(dados.medidorId(), corpo(), 201); var dto = mapper.readValue(result.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
        autenticar(perfil); mvc.perform(get(base(dados.medidorId()) + "/medicoes-consumo/" + dto.id()).session(sessao)).andExpect(status().isOk());
    }

    @Test
    void deveIsolarConsultaPorMedidorERetornar404() throws Exception {
        var result = enviar(dados.medidorId(), corpo(), 201); var dto = mapper.readValue(result.getResponse().getContentAsString(), MedicaoConsumoResponseDTO.class);
        var outro = ConsumoMedidorFixture.criar(em, false);
        mvc.perform(get(base(outro.medidorId()) + "/medicoes-consumo/" + dto.id()).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(get(base(dados.medidorId()) + "/medicoes-consumo/" + Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
        mvc.perform(get(base(Long.MAX_VALUE) + "/medicoes-consumo/" + dto.id()).session(sessao)).andExpect(status().isNotFound());
    }

    @Test
    void deveBloquearEscritaPorTecnicoEExigirCsrfESessao() throws Exception {
        autenticar(PerfilAcesso.TECNICO_MANUTENCAO); criarFonte(dados.medidorId(), Map.of("origem", "SIMULADA"), 403); enviar(dados.medidorId(), corpo(), 403);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
        mvc.perform(post(base(dados.medidorId()) + "/fontes").session(sessao).contentType(MediaType.APPLICATION_JSON).content("{\"origem\":\"SIMULADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(base(dados.medidorId()) + "/medicoes-consumo").session(sessao).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo())))
                .andExpect(status().isForbidden());
        mvc.perform(get(base(dados.medidorId()) + "/medicoes-consumo/1")).andExpect(status().isUnauthorized());
        var inicio = mvc.perform(get("/api/auth/csrf")).andReturn(); var anonima = (MockHttpSession) inicio.getRequest().getSession(false);
        var token = mapper.readValue(inicio.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post(base(dados.medidorId()) + "/medicoes-consumo").session(anonima).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo()))).andExpect(status().isUnauthorized());
    }

    private String base(Long id) { return "/api/medidores/" + id; }
    private Map<String, Object> corpo() { return new HashMap<>(Map.of("fonteId", dados.fonteId(), "inicioPeriodo", AGORA.minusSeconds(7200).atOffset(ZoneOffset.ofHours(-3)).toString(),
            "fimPeriodo", AGORA.minusSeconds(3600).toString(), "consumoKwh", new BigDecimal("100.123456"))); }
    private org.springframework.test.web.servlet.MvcResult enviar(Long id, Map<String, Object> r, int statusEsperado) throws Exception {
        return mvc.perform(post(base(id) + "/medicoes-consumo").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(r))).andExpect(status().is(statusEsperado)).andReturn();
    }
    private org.springframework.test.web.servlet.MvcResult criarFonte(Long id, Map<String, ?> r, int statusEsperado) throws Exception {
        return mvc.perform(post(base(id) + "/fontes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(r))).andExpect(status().is(statusEsperado)).andReturn();
    }
    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicio = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicio.getRequest().getSession(false);
        var token = mapper.readValue(inicio.getResponse().getContentAsString(), CsrfResponse.class);
        var usuario = usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow();
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", usuario.getEmail()).param("password", "SenhaSegura123")).andExpect(status().isOk());
        var renovado = mvc.perform(get("/api/auth/csrf").session(sessao)).andReturn(); csrf = mapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
