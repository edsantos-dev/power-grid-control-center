package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.EventoOperacionalRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
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
class InterrupcaoControllerTest {
    private static final Instant AGORA = Instant.parse("2026-11-01T12:00:00Z");
    private static final String INICIO = "2026-01-01T10:00:00.123456-03:00";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EventoOperacionalRepository eventos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;
    private CsrfResponse csrf;
    private Subestacao ativo;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        ativo = new Subestacao(); ativo.setNome("Ativo da interrupção"); ativo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        em.persist(ativo); em.flush(); autenticar(PerfilAcesso.OPERADOR);
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void deveCriarComInicioFisicoPreservadoAutoriaDaSessaoELocationConsultavel(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        var corpo = corpo();
        corpo.put("registradorUsuarioId", usuario(PerfilAcesso.GERENTE_OPERACOES));
        corpo.put("categoria", "MANOBRA"); corpo.put("situacao", "CONCLUIDO");
        corpo.put("inicioPrevisto", "2099-01-01T00:00:00Z"); corpo.put("terminoEfetivo", AGORA.toString());
        var resultado = enviar(corpo, 201);
        var dto = mapper.readValue(resultado.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
        assertThat(dto.categoria()).isEqualTo(EventoOperacional.Categoria.INTERRUPCAO_FORNECIMENTO);
        assertThat(dto.situacao()).isEqualTo("ABERTO"); assertThat(dto.descricao()).isEqualTo("Interrupção informada pelo operador");
        assertThat(dto.ativoId()).isEqualTo(ativo.getId());
        assertThat(dto.registradorUsuarioId()).isEqualTo(usuario(perfil));
        assertThat(dto.responsavelUsuarioId()).isEqualTo(usuario(PerfilAcesso.GERENTE_OPERACOES));
        assertThat(dto.inicioEfetivo().toInstant()).isEqualTo(OffsetDateTime.parse(INICIO).toInstant()).isNotEqualTo(AGORA);
        assertThat(dto.inicioEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(dto.inicioPrevisto()).isNull(); assertThat(dto.terminoPrevisto()).isNull(); assertThat(dto.terminoEfetivo()).isNull();
        assertThat(dto.criticidade()).isEqualTo("ALTA");
        em.flush(); em.clear();
        var salvo = eventos.findById(dto.id()).orElseThrow();
        assertThat(salvo.getInicioEfetivo().toInstant()).isEqualTo(dto.inicioEfetivo().toInstant());
        assertThat(salvo.getRegistradorUsuario().getId()).isEqualTo(usuario(perfil));
        assertThat(salvo.getSituacao()).isEqualTo("ABERTO");
        assertThat(em.createQuery("select count(h) from HistoricoEventoOperacional h where h.evento.id = :id", Long.class)
                .setParameter("id", dto.id()).getSingleResult()).isZero();
        String location = resultado.getResponse().getHeader("Location");
        assertThat(location).isEqualTo("http://localhost/api/eventos-operacionais/" + dto.id());
        mvc.perform(get(java.net.URI.create(location)).session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("INTERRUPCAO_FORNECIMENTO"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void deveAceitarInicioNoInstanteAtualECriticidadeOpcional() throws Exception {
        var corpo = corpo(); corpo.put("inicioEfetivo", AGORA.toString()); corpo.remove("criticidade");
        var resultado = enviar(corpo, 201);
        var dto = mapper.readValue(resultado.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
        assertThat(dto.inicioEfetivo().toInstant()).isEqualTo(AGORA); assertThat(dto.criticidade()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-01T12:00:00.000000001Z", "2026-11-01T09:00:01-03:00", "2099-01-01T00:00:00Z"})
    void deveRejeitarQualquerInicioFuturoSemAplicarToleranciaDeSensor(String inicio) throws Exception {
        var corpo = corpo(); corpo.put("inicioEfetivo", inicio); enviar(corpo, 400);
        assertThat(eventos.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "descricao", "responsavelUsuarioId", "inicioEfetivo"})
    void deveRejeitarObrigatoriosNulos(String campo) throws Exception {
        var corpo = corpo(); corpo.put(campo, null); enviar(corpo, 400); assertThat(eventos.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ativoId,0", "ativoId,-1", "responsavelUsuarioId,0", "responsavelUsuarioId,-1"})
    void deveRejeitarIdsNaoPositivos(String campo, Long id) throws Exception {
        var corpo = corpo(); corpo.put(campo, id); enviar(corpo, 400); assertThat(eventos.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01T10:00:00", "invalido", "2026-02-30T10:00:00Z"})
    void deveRejeitarDataInvalidaOuSemFuso(String inicio) throws Exception {
        var corpo = corpo(); corpo.put("inicioEfetivo", inicio); enviar(corpo, 400); assertThat(eventos.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void deveRejeitarDescricaoEmBranco(String descricao) throws Exception {
        var corpo = corpo(); corpo.put("descricao", descricao); enviar(corpo, 400);
    }

    @Test
    void deveRejeitarCriticidadeMaiorQue32Caracteres() throws Exception {
        var corpo = corpo(); corpo.put("criticidade", "A".repeat(33)); enviar(corpo, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "responsavelUsuarioId"})
    void deveRetornar404ParaReferenciaAusente(String campo) throws Exception {
        var corpo = corpo(); corpo.put(campo, Long.MAX_VALUE); enviar(corpo, 404);
        assertThat(eventos.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void naoDeveExigirFalhaNemAlterarEstadoDoAtivo(EstadoOperacional estado) throws Exception {
        ativo.setEstadoOperacional(estado); em.flush(); enviar(corpo(), 201); em.clear();
        assertThat(em.find(Subestacao.class, ativo.getId()).getEstadoOperacional()).isEqualTo(estado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALIMENTADOR", "TRANSFORMADOR", "LINHA", "MEDIDOR", "USINA"})
    void deveAceitarOsDemaisSubtiposDeAtivo(String tipo) throws Exception {
        AtivoRede alvo;
        if (tipo.equals("MEDIDOR")) {
            var medidor = new MedidorInteligente(); medidor.setNumeroSerie("INTERRUPCAO-SERIE"); alvo = medidor;
        } else if (tipo.equals("LINHA")) {
            var destino = new Subestacao(); destino.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(destino);
            var linha = new LinhaTransmissao(); linha.setSubestacaoOrigem(ativo); linha.setSubestacaoDestino(destino); alvo = linha;
        } else if (tipo.equals("USINA")) {
            var usina = new UsinaGeradora(); usina.setTipoGeracao(TipoGeracao.SOLAR);
            usina.setCapacidadeMaximaMw(BigDecimal.TEN); usina.setSubestacao(ativo); alvo = usina;
        } else {
            var alimentador = new Alimentador(); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            alimentador.setSubestacao(ativo); em.persist(alimentador); alvo = alimentador;
            if (tipo.equals("TRANSFORMADOR")) {
                var transformador = new Transformador(); transformador.setAlimentador(alimentador);
                transformador.setCapacidadeMva(BigDecimal.TEN); alvo = transformador;
            }
        }
        alvo.setNome("Ativo " + tipo); alvo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(alvo); em.flush();
        var corpo = corpo(); corpo.put("ativoId", alvo.getId());
        var resultado = enviar(corpo, 201); var dto = mapper.readValue(resultado.getResponse().getContentAsString(), EventoOperacionalResponseDTO.class);
        assertThat(dto.ativoId()).isEqualTo(alvo.getId());
    }

    @Test
    void deveExigirCsrfESessaoAutenticada() throws Exception {
        mvc.perform(post("/api/interrupcoes").session(sessao).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo()))).andExpect(status().isForbidden());
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        var anonima = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/interrupcoes").session(anonima).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(corpo()))).andExpect(status().isUnauthorized());
        assertThat(eventos.count()).isZero();
    }

    @Test
    void deveManterInterrupcaoBloqueadaNoCadastroGenerico() throws Exception {
        var corpo = corpo(); corpo.put("categoria", "INTERRUPCAO_FORNECIMENTO"); corpo.put("inicioPrevisto", INICIO);
        mvc.perform(post("/api/eventos-operacionais").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo))).andExpect(status().isBadRequest());
        assertThat(eventos.count()).isZero();
    }

    private org.springframework.test.web.servlet.MvcResult enviar(Map<String, Object> corpo, int esperado) throws Exception {
        var resultado = mvc.perform(post("/api/interrupcoes").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(corpo)))
                .andExpect(status().is(esperado)).andReturn();
        if (esperado == 400 || esperado == 404) {
            assertThat(mapper.readTree(resultado.getResponse().getContentAsString()).get("status").asInt()).isEqualTo(esperado);
        }
        return resultado;
    }

    private Map<String, Object> corpo() {
        return new HashMap<>(Map.of("ativoId", ativo.getId(), "descricao", "  Interrupção informada pelo operador  ",
                "responsavelUsuarioId", usuario(PerfilAcesso.GERENTE_OPERACOES), "inicioEfetivo", INICIO, "criticidade", "ALTA"));
    }

    private Long usuario(PerfilAcesso perfil) {
        return usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow().getId();
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
