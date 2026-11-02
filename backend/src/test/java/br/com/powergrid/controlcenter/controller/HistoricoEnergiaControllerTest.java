package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class HistoricoEnergiaControllerTest {
    private static final OffsetDateTime BASE = OffsetDateTime.parse("2024-01-01T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private UsuarioRepository usuarios;
    @MockitoBean private Clock clock;
    private Alimentador alimentador;
    private FonteMedicao fonte;
    private MockHttpSession sessao;
    private int sequencia;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-02T00:00:00Z")); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        alimentador = alimentador(); fonte = fonte(alimentador); autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void deveAgregarDiaComPercentualPonderadoESnapshotsPreservados() throws Exception {
        leitura(fonte, BASE, BASE.plusHours(12), "100", "3.5");
        leitura(fonte, BASE.plusHours(12), BASE.plusDays(1), "300", "5");
        alimentador.setPercentualPerdaTecnica(new BigDecimal("99")); em.flush();
        var dto = consultar(consulta()); var ponto = dto.pontos().getFirst();
        assertThat(dto.alimentadorId()).isEqualTo(alimentador.getId());
        assertThat(dto.granularidade()).isEqualTo(GranularidadeHistorico.DIA); assertThat(dto.fusoHorario()).isEqualTo("UTC");
        assertThat(dto.possuiDados()).isTrue(); assertThat(dto.pontos()).hasSize(1);
        assertThat(ponto.situacao()).isEqualTo(SituacaoPerdaTecnica.OK); assertThat(ponto.quantidadeLeituras()).isEqualTo(2);
        assertThat(ponto.energiaInjetadaMwh()).isEqualByComparingTo("400.000000");
        assertThat(ponto.perdaEstimadaMwh()).isEqualByComparingTo("18.500000");
        assertThat(ponto.percentualPerdaPonderado()).isEqualByComparingTo("4.625000");
    }

    @Test
    void devePreencherDiasSemLeiturasComNulosSemInventarZero() throws Exception {
        leitura(fonte, BASE, BASE.plusDays(1), "100", "3.5");
        leitura(fonte, BASE.plusDays(2), BASE.plusDays(3), "200", "5"); em.flush();
        var dto = consultar(consulta(BASE, BASE.plusDays(7), GranularidadeHistorico.DIA));
        assertThat(dto.pontos()).hasSize(7); assertThat(dto.possuiDados()).isTrue();
        assertThat(dto.pontos().get(0).situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
        insuficiente(dto.pontos().get(1)); assertThat(dto.pontos().get(1).quantidadeLeituras()).isZero();
        assertThat(dto.pontos().get(2).energiaInjetadaMwh()).isEqualByComparingTo("200");
        for (int i = 0; i < 7; i++) {
            assertThat(dto.pontos().get(i).inicioPeriodo()).isEqualTo(BASE.plusDays(i));
            assertThat(dto.pontos().get(i).fimPeriodo()).isEqualTo(BASE.plusDays(i + 1));
        }
    }

    @Test
    void deveAgregarMesesComFevereiroBissextoEZeroMedido() throws Exception {
        var fevereiro = BASE.plusMonths(1); var marco = BASE.plusMonths(2);
        leitura(fonte, BASE, BASE.plusDays(15), "100", "1");
        leitura(fonte, BASE.plusDays(15), fevereiro, "300", "5");
        leitura(fonte, fevereiro, marco, "0", "3.5"); em.flush();
        var dto = consultar(consulta(BASE, marco, GranularidadeHistorico.MES));
        assertThat(dto.pontos()).hasSize(2);
        assertThat(dto.pontos().getFirst().energiaInjetadaMwh()).isEqualByComparingTo("400");
        assertThat(dto.pontos().getFirst().perdaEstimadaMwh()).isEqualByComparingTo("16");
        assertThat(dto.pontos().getFirst().percentualPerdaPonderado()).isEqualByComparingTo("4");
        var zero = dto.pontos().getLast(); assertThat(zero.inicioPeriodo()).isEqualTo(fevereiro); assertThat(zero.fimPeriodo()).isEqualTo(marco);
        assertThat(zero.situacao()).isEqualTo(SituacaoPerdaTecnica.SEM_ENERGIA_INJETADA);
        assertThat(zero.energiaInjetadaMwh()).isEqualByComparingTo("0"); assertThat(zero.perdaEstimadaMwh()).isEqualByComparingTo("0");
        assertThat(zero.percentualPerdaPonderado()).isNull();
    }

    @Test
    void deveManterMesSemDadosNoCalendario() throws Exception {
        leitura(fonte, BASE, BASE.plusMonths(1), "100", "5");
        leitura(fonte, BASE.plusMonths(2), BASE.plusMonths(3), "200", "5"); em.flush();
        var dto = consultar(consulta(BASE, BASE.plusMonths(3), GranularidadeHistorico.MES));
        assertThat(dto.pontos()).hasSize(3); insuficiente(dto.pontos().get(1));
        assertThat(dto.pontos().getLast().situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
    }

    @Test
    void naoDeveRatearLeituraEntreDiasMasPodeUtilizaLaNoPontoMensalContido() throws Exception {
        leitura(fonte, BASE, BASE.plusDays(2), "200", "5"); em.flush();
        var diario = consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.DIA));
        assertThat(diario.possuiDados()).isTrue(); diario.pontos().forEach(this::insuficiente);
        assertThat(diario.pontos()).allSatisfy(p -> assertThat(p.quantidadeLeituras()).isZero());
        var mensal = consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.MES));
        assertThat(mensal.pontos()).hasSize(1); assertThat(mensal.pontos().getFirst().situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
        assertThat(mensal.pontos().getFirst().energiaInjetadaMwh()).isEqualByComparingTo("200");
        assertThat(mensal.pontos().getFirst().perdaEstimadaMwh()).isEqualByComparingTo("10");
    }

    @Test
    void deveInvalidarAmbosOsMesesAtravessadosSemRateio() throws Exception {
        leitura(fonte, BASE.plusMonths(1).minusHours(1), BASE.plusMonths(1).plusHours(1), "100", "5"); em.flush();
        var dto = consultar(consulta(BASE, BASE.plusMonths(2), GranularidadeHistorico.MES));
        assertThat(dto.pontos()).hasSize(2); dto.pontos().forEach(this::insuficiente);
    }

    @Test
    void deveUsarBordasParciaisReaisDaJanela() throws Exception {
        leitura(fonte, BASE.plusHours(12), BASE.plusDays(1), "100", "3.5");
        leitura(fonte, BASE.plusDays(1), BASE.plusDays(1).plusHours(12), "200", "5"); em.flush();
        var dto = consultar(consulta(BASE.plusHours(12), BASE.plusDays(1).plusHours(12), GranularidadeHistorico.DIA));
        assertThat(dto.pontos()).hasSize(2); assertThat(dto.pontos()).allSatisfy(p -> assertThat(p.situacao()).isEqualTo(SituacaoPerdaTecnica.OK));
        assertThat(dto.pontos().getFirst().inicioPeriodo()).isEqualTo(BASE.plusHours(12));
        assertThat(dto.pontos().getLast().fimPeriodo()).isEqualTo(BASE.plusDays(1).plusHours(12));
    }

    @Test
    void deveRejeitarCoberturaParcialMesmoComEnergiaZero() throws Exception {
        leitura(fonte, BASE, BASE.plusHours(12), "0", "5"); em.flush();
        var p = consultar(consulta()).pontos().getFirst(); insuficiente(p); assertThat(p.quantidadeLeituras()).isEqualTo(1);
    }

    @Test
    void deveDetectarLacunaDeUmMicrossegundo() throws Exception {
        leitura(fonte, BASE, BASE.plusDays(1).minusNanos(1000), "100", "5"); em.flush();
        insuficiente(consultar(consulta()).pontos().getFirst());
    }

    @Test
    void deveDetectarSobreposicaoLegadaSemSomarComoTotalValido() throws Exception {
        leitura(fonte, BASE, BASE.plusDays(1), "100", "3.5");
        leitura(fonte(alimentador), BASE.plusHours(12), BASE.plusDays(1), "200", "5"); em.flush();
        var p = consultar(consulta()).pontos().getFirst(); insuficiente(p); assertThat(p.quantidadeLeituras()).isEqualTo(2);
    }

    @Test
    void deveInvalidarSobreposicaoDeLeituraQueCruzaMesmoComOutraCoberturaCompleta() throws Exception {
        leitura(fonte, BASE, BASE.plusDays(2), "200", "5");
        leitura(fonte(alimentador), BASE, BASE.plusDays(1), "100", "5"); em.flush();
        consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.DIA)).pontos().forEach(this::insuficiente);
    }

    @Test
    void deveSomarAntesDeArredondarPerdas() throws Exception {
        leitura(fonte, BASE, BASE.plusHours(12), "0.000001", "50");
        leitura(fonte, BASE.plusHours(12), BASE.plusDays(1), "0.000001", "50"); em.flush();
        var p = consultar(consulta()).pontos().getFirst();
        assertThat(p.perdaEstimadaMwh()).isEqualByComparingTo("0.000001");
        assertThat(p.percentualPerdaPonderado()).isEqualByComparingTo("50");
    }

    @ParameterizedTest
    @CsvSource({"0,0.000001,0.000000", "0.000001,0.000002,0.000002"})
    void deveArredondarPercentualFinalComHalfEven(String p1, String p2, String esperado) throws Exception {
        leitura(fonte, BASE, BASE.plusHours(12), "1", p1); leitura(fonte, BASE.plusHours(12), BASE.plusDays(1), "1", p2); em.flush();
        assertThat(consultar(consulta()).pontos().getFirst().percentualPerdaPonderado()).isEqualByComparingTo(esperado);
    }

    @Test
    void deveNormalizarConsultaComOffsetParaCalendarioUtc() throws Exception {
        leitura(fonte, BASE, BASE.plusDays(1), "100", "5"); em.flush();
        var dto = consultar(get(uri()).session(sessao).param("dataInicio", "2023-12-31T21:00:00-03:00")
                .param("dataFim", "2024-01-02T02:00:00+02:00").param("granularidade", "DIA"));
        assertThat(dto.dataInicio()).isEqualTo(BASE); assertThat(dto.dataFim()).isEqualTo(BASE.plusDays(1));
        assertThat(dto.pontos()).hasSize(1); assertThat(dto.pontos().getFirst().situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
    }

    @Test
    void deveIsolarAlimentadoresERetornarCalendarioVazioDeValores() throws Exception {
        leitura(fonte(alimentador()), BASE, BASE.plusDays(1), "100", "5"); em.flush();
        var dto = consultar(consulta()); assertThat(dto.possuiDados()).isFalse(); assertThat(dto.pontos()).hasSize(1);
        insuficiente(dto.pontos().getFirst());
    }

    @Test
    void deveExcluirLeituraQueUltrapassaJanelaMacro() throws Exception {
        leitura(fonte, BASE.minusHours(1), BASE.plusDays(1), "100", "5"); em.flush();
        var dto = consultar(consulta()); assertThat(dto.possuiDados()).isFalse(); insuficiente(dto.pontos().getFirst());
    }

    @ParameterizedTest
    @EnumSource(GranularidadeHistorico.class)
    void deveAceitar366PontosERejeitar367(GranularidadeHistorico granularidade) throws Exception {
        var inicio = OffsetDateTime.parse("1990-01-01T00:00:00Z");
        var fim = granularidade == GranularidadeHistorico.DIA ? inicio.plusDays(366) : inicio.plusMonths(366);
        assertThat(consultar(consulta(inicio, fim, granularidade)).pontos()).hasSize(366);
        var excedido = granularidade == GranularidadeHistorico.DIA ? fim.plusDays(1) : fim.plusMonths(1);
        mvc.perform(consulta(inicio, excedido, granularidade)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveBloquearPerfisNaoAutorizados(PerfilAcesso perfil) throws Exception {
        autenticar(perfil); mvc.perform(consulta()).andExpect(status().isForbidden());
    }

    @Test
    void deveExigirSessaoERetornar404ParaAlimentadorInexistente() throws Exception {
        mvc.perform(get(uri())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alimentadores/{id}/historico-energia", Long.MAX_VALUE).session(sessao)
                .param("dataInicio", BASE.toString()).param("dataFim", BASE.plusDays(1).toString()).param("granularidade", "DIA"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim", "granularidade"})
    void deveExigirParametrosObrigatorios(String ausente) throws Exception {
        var r = get(uri()).session(sessao);
        if (!ausente.equals("dataInicio")) r.param("dataInicio", BASE.toString());
        if (!ausente.equals("dataFim")) r.param("dataFim", BASE.plusDays(1).toString());
        if (!ausente.equals("granularidade")) r.param("granularidade", "DIA");
        mvc.perform(r).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"dataInicio,invalida", "dataFim,2024-01-02T00:00:00", "dataInicio,2024-01-02T00:00:00Z",
            "dataInicio,2024-01-03T00:00:00Z", "dataFim,2026-11-02T00:00:01Z", "granularidade,ANO", "granularidade,dia",
            "page,0", "size,20", "sort,inicioPeriodo", "fonteId,1"})
    void deveRejeitarParametrosInvalidos(String nome, String valor) throws Exception {
        mvc.perform(consultaCom(nome, valor)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim", "granularidade"})
    void deveRejeitarParametrosVaziosOuRepetidos(String nome) throws Exception {
        mvc.perform(consultaCom(nome, "")).andExpect(status().isBadRequest());
        mvc.perform(consulta().param(nome, "a", "b")).andExpect(status().isBadRequest());
    }

    private String uri() { return "/api/alimentadores/" + alimentador.getId() + "/historico-energia"; }
    private MockHttpServletRequestBuilder consulta() { return consulta(BASE, BASE.plusDays(1), GranularidadeHistorico.DIA); }
    private MockHttpServletRequestBuilder consulta(OffsetDateTime inicio, OffsetDateTime fim, GranularidadeHistorico granularidade) {
        return get(uri()).session(sessao).param("dataInicio", inicio.toString()).param("dataFim", fim.toString()).param("granularidade", granularidade.name());
    }
    private MockHttpServletRequestBuilder consultaCom(String nome, String valor) {
        var r = get(uri()).session(sessao).param("dataInicio", nome.equals("dataInicio") ? valor : BASE.toString())
                .param("dataFim", nome.equals("dataFim") ? valor : BASE.plusDays(1).toString())
                .param("granularidade", nome.equals("granularidade") ? valor : "DIA");
        if (!nome.equals("dataInicio") && !nome.equals("dataFim") && !nome.equals("granularidade")) r.param(nome, valor);
        return r;
    }
    private HistoricoEnergiaResponseDTO consultar(MockHttpServletRequestBuilder r) throws Exception {
        var result = mvc.perform(r).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), HistoricoEnergiaResponseDTO.class);
    }
    private void insuficiente(PontoHistoricoDTO p) {
        assertThat(p.situacao()).isEqualTo(SituacaoPerdaTecnica.DADOS_INSUFICIENTES);
        assertThat(p.energiaInjetadaMwh()).isNull(); assertThat(p.perdaEstimadaMwh()).isNull(); assertThat(p.percentualPerdaPonderado()).isNull();
    }
    private Alimentador alimentador() {
        var s = new Subestacao(); s.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(s);
        var a = new Alimentador(); a.setNome("Alimentador da série histórica"); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        a.setSubestacao(s); em.persist(a); return a;
    }
    private FonteMedicao fonte(Alimentador a) {
        var f = new FonteMedicao(); f.setAtivo(a); f.setOrigem(OrigemMedicao.SIMULADA); f.setIdentificador("HISTORICO-WEB-" + (++sequencia)); em.persist(f); return f;
    }
    private void leitura(FonteMedicao f, OffsetDateTime inicio, OffsetDateTime fim, String energia, String percentual) {
        var m = new MedicaoEnergiaAlimentador(); m.setFonte(f); m.setInicioPeriodo(inicio); m.setFimPeriodo(fim); m.setRecebidoEm(fim.plusHours(1));
        m.setEnergiaInjetadaMwh(new BigDecimal(energia)); m.setPercentualPerdaReferencia(new BigDecimal(percentual)); em.persist(m);
    }
    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        var usuario = usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow();
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", usuario.getEmail()).param("password", "SenhaSegura123")).andExpect(status().isOk());
    }
}
