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
import br.com.powergrid.controlcenter.support.ConsumoMedidorFixture;
import br.com.powergrid.controlcenter.support.ConsumoMedidorFixture.Dados;
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
class HistoricoConsumoMedidoControllerTest {
    private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-01-01T00:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private UsuarioRepository usuarios;
    @MockitoBean private Clock clock;
    private Dados dados;
    private MockHttpSession sessao;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2026-11-02T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        dados = ConsumoMedidorFixture.criar(em, false);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void deveSomarMedidoresSimultaneosEConverterKwhParaMwh() throws Exception {
        var segundo = outroMedidor();
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "150.5");
        leitura(segundo, segundo.fonteId(), BASE, BASE.plusDays(1), "49.5");
        var dto = consultar(consulta());
        assertThat(dto.alimentadorId()).isEqualTo(dados.alimentadorId());
        assertThat(dto.granularidade()).isEqualTo(GranularidadeHistorico.DIA);
        assertThat(dto.fusoHorario()).isEqualTo("UTC");
        assertThat(dto.coberturaCadastral()).isEqualTo("NAO_AFERIDA");
        assertThat(dto.possuiDados()).isTrue();
        assertThat(dto.quantidadeMedidoresConsiderados()).isEqualTo(2);
        var p = dto.pontos().getFirst();
        assertThat(p.situacao()).isEqualTo(SituacaoConsumoMedido.OK);
        assertThat(p.consumoMedidoMwh()).isEqualByComparingTo("0.200000000");
        assertThat(p.consumoMedidoMwh().scale()).isEqualTo(9);
        assertThat(p.quantidadeLeituras()).isEqualTo(2);
        assertThat(p.quantidadeMedidoresComCoberturaCompleta()).isEqualTo(2);
    }

    @Test
    void deveCombinarFontesDoMesmoEquipamentoSemDuplicarPopulacao() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusHours(12), "0.000001");
        leitura(dados, dados.segundaFonteId(), BASE.plusHours(12), BASE.plusDays(1), "0.000001");
        var dto = consultar(consulta());
        assertThat(dto.quantidadeMedidoresConsiderados()).isEqualTo(1);
        var p = dto.pontos().getFirst();
        assertThat(p.consumoMedidoMwh()).isEqualByComparingTo("0.000000002");
        assertThat(p.quantidadeLeituras()).isEqualTo(2);
        assertThat(p.quantidadeMedidoresComCoberturaCompleta()).isEqualTo(1);
    }

    @Test
    void deveSomarSemLimitarTotalAoTamanhoDaColunaIndividual() throws Exception {
        var segundo = outroMedidor();
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "9999999999999.999999");
        leitura(segundo, segundo.fonteId(), BASE, BASE.plusDays(1), "9999999999999.999999");
        assertThat(consultar(consulta()).pontos().getFirst().consumoMedidoMwh()).isEqualByComparingTo("19999999999.999999998");
    }

    @Test
    void deveExigirTodosOsMedidoresDaJanelaEmCadaPonto() throws Exception {
        var segundo = outroMedidor();
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "100");
        leitura(dados, dados.fonteId(), BASE.plusDays(1), BASE.plusDays(2), "50");
        leitura(segundo, segundo.fonteId(), BASE.plusDays(1), BASE.plusDays(2), "150");
        var dto = consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.DIA));
        assertThat(dto.quantidadeMedidoresConsiderados()).isEqualTo(2);
        insuficiente(dto.pontos().getFirst());
        assertThat(dto.pontos().getFirst().quantidadeMedidoresComCoberturaCompleta()).isEqualTo(1);
        assertThat(dto.pontos().getLast().situacao()).isEqualTo(SituacaoConsumoMedido.OK);
        assertThat(dto.pontos().getLast().consumoMedidoMwh()).isEqualByComparingTo("0.2");
    }

    @Test
    void deveDistinguirZeroMedidoDeLacuna() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "0");
        leitura(dados, dados.fonteId(), BASE.plusDays(1), BASE.plusDays(1).plusHours(12), "0");
        var dto = consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.DIA));
        assertThat(dto.pontos().getFirst().situacao()).isEqualTo(SituacaoConsumoMedido.SEM_CONSUMO);
        assertThat(dto.pontos().getFirst().consumoMedidoMwh()).isEqualByComparingTo("0");
        insuficiente(dto.pontos().getLast());
    }

    @Test
    void deveRetornarCalendarioNuloSemInventarPopulacaoCadastral() throws Exception {
        outroMedidor();
        var dto = consultar(consulta(BASE, BASE.plusDays(3), GranularidadeHistorico.DIA));
        assertThat(dto.possuiDados()).isFalse();
        assertThat(dto.quantidadeMedidoresConsiderados()).isZero();
        assertThat(dto.pontos()).hasSize(3);
        dto.pontos().forEach(this::insuficiente);
        assertThat(dto.pontos()).allSatisfy(p -> {
            assertThat(p.quantidadeLeituras()).isZero();
            assertThat(p.quantidadeMedidoresComCoberturaCompleta()).isZero();
        });
    }

    @Test
    void naoDeveIncluirNoCensoMedidorSemNenhumaLeitura() throws Exception {
        outroMedidor();
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "150");
        var dto = consultar(consulta());
        assertThat(dto.quantidadeMedidoresConsiderados()).isEqualTo(1);
        assertThat(dto.coberturaCadastral()).isEqualTo("NAO_AFERIDA");
        assertThat(dto.pontos().getFirst().situacao()).isEqualTo(SituacaoConsumoMedido.OK);
    }

    @Test
    void deveAgregarMesBissextoComCoberturaIntegral() throws Exception {
        var inicio = OffsetDateTime.parse("2024-02-01T00:00:00Z");
        var fim = inicio.plusMonths(1);
        leitura(dados, dados.fonteId(), inicio, inicio.plusDays(14), "150");
        leitura(dados, dados.segundaFonteId(), inicio.plusDays(14), fim, "50");
        var dto = consultar(consulta(inicio, fim, GranularidadeHistorico.MES));
        assertThat(dto.pontos()).hasSize(1);
        assertThat(dto.pontos().getFirst().fimPeriodo()).isEqualTo(fim);
        assertThat(dto.pontos().getFirst().consumoMedidoMwh()).isEqualByComparingTo("0.2");
    }

    @Test
    void naoDeveRatearLeituraEntreDiasMasDeveAceitarMesmoIntervaloMensal() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(2), "200");
        var diario = consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.DIA));
        diario.pontos().forEach(this::insuficiente);
        assertThat(diario.possuiDados()).isTrue();
        assertThat(diario.quantidadeMedidoresConsiderados()).isEqualTo(1);
        assertThat(diario.pontos()).allSatisfy(p -> assertThat(p.quantidadeLeituras()).isZero());
        var mensal = consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.MES));
        assertThat(mensal.pontos().getFirst().consumoMedidoMwh()).isEqualByComparingTo("0.2");
    }

    @Test
    void deveInvalidarMesesAtravessadosSemRatear() throws Exception {
        leitura(dados, dados.fonteId(), BASE.plusMonths(1).minusHours(1), BASE.plusMonths(1).plusHours(1), "100");
        consultar(consulta(BASE, BASE.plusMonths(2), GranularidadeHistorico.MES)).pontos().forEach(this::insuficiente);
    }

    @ParameterizedTest
    @CsvSource({"-1,24", "0,25", "-1,25"})
    void deveManterPopulacaoEInvalidarLeituraQueCruzaBordaMacro(int inicio, int fim) throws Exception {
        leitura(dados, dados.fonteId(), BASE.plusHours(inicio), BASE.plusHours(fim), "100");
        var dto = consultar(consulta());
        assertThat(dto.possuiDados()).isTrue();
        assertThat(dto.quantidadeMedidoresConsiderados()).isEqualTo(1);
        insuficiente(dto.pontos().getFirst());
    }

    @Test
    void deveInvalidarSobreposicaoEntreFontesDoMesmoMedidor() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "100");
        leitura(dados, dados.segundaFonteId(), BASE.plusHours(12), BASE.plusDays(1), "50");
        var p = consultar(consulta()).pontos().getFirst();
        insuficiente(p);
        assertThat(p.quantidadeLeituras()).isEqualTo(2);
        assertThat(p.quantidadeMedidoresComCoberturaCompleta()).isZero();
    }

    @Test
    void deveInvalidarLeituraQueCruzaMesmoHavendoOutraCoberturaCompleta() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(2), "200");
        leitura(dados, dados.segundaFonteId(), BASE, BASE.plusDays(1), "100");
        consultar(consulta(BASE, BASE.plusDays(2), GranularidadeHistorico.DIA)).pontos().forEach(this::insuficiente);
    }

    @Test
    void deveDetectarLacunaDeMicrossegundo() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1).minusNanos(1000), "100");
        insuficiente(consultar(consulta()).pontos().getFirst());
    }

    @Test
    void deveUsarBordasParciaisENormalizarOffsetParaUtc() throws Exception {
        leitura(dados, dados.fonteId(), BASE.plusHours(12), BASE.plusDays(1), "150");
        leitura(dados, dados.fonteId(), BASE.plusDays(1), BASE.plusDays(1).plusHours(12), "50");
        var dto = consultar(get(uri()).session(sessao)
                .param("dataInicio", BASE.plusHours(12).withOffsetSameInstant(ZoneOffset.ofHours(-3)).toString())
                .param("dataFim", BASE.plusDays(1).plusHours(12).withOffsetSameInstant(ZoneOffset.ofHours(2)).toString())
                .param("granularidade", "DIA"));
        assertThat(dto.dataInicio()).isEqualTo(BASE.plusHours(12));
        assertThat(dto.dataFim()).isEqualTo(BASE.plusDays(1).plusHours(12));
        assertThat(dto.pontos()).hasSize(2);
        assertThat(dto.pontos().getFirst().consumoMedidoMwh()).isEqualByComparingTo("0.15");
        assertThat(dto.pontos().getLast().consumoMedidoMwh()).isEqualByComparingTo("0.05");
    }

    @Test
    void deveSelecionarSnapshotMesmoAposMudancaDaTopologiaAtual() throws Exception {
        leitura(dados, dados.fonteId(), BASE, BASE.plusDays(1), "150");
        var outro = ConsumoMedidorFixture.criar(em, false);
        em.find(MedidorInteligente.class, dados.medidorId()).setTransformador(em.find(Transformador.class, outro.transformadorId()));
        em.flush(); em.clear();
        assertThat(consultar(consulta()).pontos().getFirst().consumoMedidoMwh()).isEqualByComparingTo("0.15");
    }

    @Test
    void deveIgnorarLeiturasDeOutroAlimentador() throws Exception {
        var outro = ConsumoMedidorFixture.criar(em, false);
        leitura(outro, outro.fonteId(), BASE, BASE.plusDays(1), "150");
        assertThat(consultar(consulta()).quantidadeMedidoresConsiderados()).isZero();
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
        mvc.perform(get("/api/alimentadores/{id}/historico-consumo-medido", Long.MAX_VALUE).session(sessao)
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
    @CsvSource({"dataInicio,invalida", "dataFim,2026-01-02T00:00:00", "dataInicio,2026-01-02T00:00:00Z",
            "dataInicio,2026-01-03T00:00:00Z", "dataFim,2026-11-02T00:00:01Z", "granularidade,ANO", "granularidade,dia",
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

    private String uri() { return "/api/alimentadores/" + dados.alimentadorId() + "/historico-consumo-medido"; }
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
    private HistoricoConsumoMedidoResponseDTO consultar(MockHttpServletRequestBuilder r) throws Exception {
        em.flush();
        var result = mvc.perform(r).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), HistoricoConsumoMedidoResponseDTO.class);
    }
    private void insuficiente(PontoConsumoMedidoDTO p) {
        assertThat(p.situacao()).isEqualTo(SituacaoConsumoMedido.DADOS_INSUFICIENTES);
        assertThat(p.consumoMedidoMwh()).isNull();
    }
    private Dados outroMedidor() {
        var d = ConsumoMedidorFixture.criar(em, false);
        em.find(Transformador.class, d.transformadorId()).setAlimentador(em.find(Alimentador.class, dados.alimentadorId()));
        return new Dados(dados.subestacaoId(), dados.alimentadorId(), d.transformadorId(), d.consumidorId(),
                d.medidorId(), d.fonteId(), d.segundaFonteId(), null);
    }
    private void leitura(Dados d, Long fonteId, OffsetDateTime inicio, OffsetDateTime fim, String consumo) {
        var m = new MedicaoConsumoMedidor();
        m.setFonte(em.find(FonteMedicao.class, fonteId)); m.setInicioPeriodo(inicio); m.setFimPeriodo(fim); m.setRecebidoEm(fim.plusHours(1));
        m.setConsumoKwh(new BigDecimal(consumo)); m.setConsumidorId(d.consumidorId()); m.setTransformadorId(d.transformadorId());
        m.setAlimentadorId(d.alimentadorId()); m.setSubestacaoId(d.subestacaoId()); em.persist(m);
    }
    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        var usuario = usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow();
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token()).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", usuario.getEmail()).param("password", "SenhaSegura123")).andExpect(status().isOk());
    }
}
