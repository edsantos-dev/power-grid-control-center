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
class PerdaTecnicaControllerTest {
    private static final java.time.OffsetDateTime BASE = java.time.OffsetDateTime.parse("2026-01-01T10:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private MedicaoEnergiaAlimentadorRepository medicoes;
    private Alimentador alimentador;
    private FonteMedicao fonte;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        alimentador = alimentador(); fonte = fonte(alimentador); autenticar(PerfilAcesso.OPERADOR);
    }

    @Test
    void deveCalcularPercentualPonderadoUsandoSnapshotsDeMultiplasFontes() throws Exception {
        leitura(fonte, 0, 1, "100", "3.5"); leitura(fonte, 1, 2, "200", "3.5");
        leitura(fonte(alimentador), 2, 3, "100", "5");
        alimentador.setPercentualPerdaTecnica(new BigDecimal("99")); em.flush();
        var dto = calcular(0, 3);
        assertThat(dto.situacao()).isEqualTo(SituacaoPerdaTecnica.OK);
        assertThat(dto.totalEnergiaInjetadaMwh()).isEqualByComparingTo("400.000000");
        assertThat(dto.totalPerdaEstimadaMwh()).isEqualByComparingTo("15.500000");
        assertThat(dto.percentualPonderado()).isEqualByComparingTo("3.875000");
        assertThat(dto.percentualCobertura()).isEqualByComparingTo("100.000000");
        assertThat(dto.medicoesConsideradas()).extracting(m -> m.percentualPerdaReferencia().stripTrailingZeros())
                .containsExactly(new BigDecimal("3.5"), new BigDecimal("3.5"), new BigDecimal("5"));
    }

    @Test
    void deveSomarPerdasAntesDoArredondamentoFinal() throws Exception {
        leitura(fonte, 0, 1, "0.000001", "50"); leitura(fonte, 1, 2, "0.000001", "50");
        var dto = calcular(0, 2);
        assertThat(dto.totalPerdaEstimadaMwh()).isEqualByComparingTo("0.000001");
        assertThat(dto.percentualPonderado()).isEqualByComparingTo("50.000000");
    }

    @ParameterizedTest
    @CsvSource({"0.000001,0.000000", "0.000003,0.000002"})
    void deveAplicarHalfEvenSomenteNaPerdaFinal(String energia, String esperado) throws Exception {
        leitura(fonte, 0, 1, energia, "50");
        var dto = calcular(0, 1);
        assertThat(dto.totalPerdaEstimadaMwh()).isEqualByComparingTo(esperado);
        assertThat(dto.percentualPonderado()).isEqualByComparingTo("50");
    }

    @ParameterizedTest
    @CsvSource({"0,0.000001,0.000000", "0.000001,0.000002,0.000002"})
    void deveAplicarHalfEvenAoPercentualFinal(String p1, String p2, String esperado) throws Exception {
        leitura(fonte, 0, 1, "1", p1); leitura(fonte, 1, 2, "1", p2);
        assertThat(calcular(0, 2).percentualPonderado()).isEqualByComparingTo(esperado);
    }

    @Test
    void deveSuportarSomaMaiorQueAPrecisaoDaColunaIndividual() throws Exception {
        leitura(fonte, 0, 1, "9999999999999.999999", "1"); leitura(fonte, 1, 2, "9999999999999.999999", "1");
        assertThat(calcular(0, 2).totalEnergiaInjetadaMwh()).isEqualByComparingTo("19999999999999.999998");
    }

    @Test
    void deveRetornarZeroSemDivisaoPorZeroQuandoCoberturaCompletaNaoTemEnergia() throws Exception {
        leitura(fonte, 0, 1, "0", "3.5");
        var dto = calcular(0, 1);
        assertThat(dto.situacao()).isEqualTo(SituacaoPerdaTecnica.SEM_ENERGIA_INJETADA);
        assertThat(dto.totalEnergiaInjetadaMwh()).isZero(); assertThat(dto.totalPerdaEstimadaMwh()).isZero();
        assertThat(dto.percentualPonderado()).isNull(); assertThat(dto.percentualCobertura()).isEqualByComparingTo("100");
    }

    @Test
    void deveInformarLacunaInternaSemApresentarTotaisParciais() throws Exception {
        leitura(fonte, 0, 1, "100", "3.5"); leitura(fonte, 2, 3, "200", "5");
        var dto = calcular(0, 3);
        insuficiente(dto, "66.666667"); assertThat(dto.medicoesConsideradas()).hasSize(2);
    }

    @Test
    void deveDetectarLacunasNasExtremidadesEAusenciaDeDados() throws Exception {
        insuficiente(calcular(0, 3), "0");
        leitura(fonte, 1, 2, "0", "3.5"); insuficiente(calcular(0, 3), "33.333333");
    }

    @Test
    void naoDeveRatearIntervalosQueCruzamAsFronteirasSolicitadas() throws Exception {
        leitura(fonte, 0, 3, "300", "3.5");
        var dto = calcular(1, 2); insuficiente(dto, "0"); assertThat(dto.medicoesConsideradas()).isEmpty();
    }

    @Test
    void deveDetectarLacunaSubsegundoSemDependerDoPercentualArredondado() throws Exception {
        var m = leitura(fonte, 0, 1, "100", "3.5"); m.setFimPeriodo(BASE.plusHours(1).minusNanos(1000)); em.flush();
        var dto = calcular(0, 1);
        assertThat(dto.situacao()).isEqualTo(SituacaoPerdaTecnica.DADOS_INSUFICIENTES);
        assertThat(dto.totalPerdaEstimadaMwh()).isNull();
    }

    @Test
    void deveRecusarDadosLegadosSobrepostosSemDuplicarCobertura() throws Exception {
        leitura(fonte, 0, 2, "100", "3.5"); leitura(fonte(alimentador), 0, 1, "50", "5");
        insuficiente(calcular(0, 2), "100");
    }

    @Test
    void deveIsolarAlimentadoresEFontesNaConsulta() throws Exception {
        leitura(fonte(alimentador()), 0, 1, "100", "3.5"); insuficiente(calcular(0, 1), "0");
    }

    @Test
    void deveListarComPaginacaoFixaEFiltrosSobreInicioDoPeriodo() throws Exception {
        var a = leitura(fonte, 0, 1, "100", "3.5"); var b = leitura(fonte, 1, 2, "200", "5");
        leitura(fonte(alimentador()), 3, 4, "300", "7");
        mvc.perform(get(base() + "/medicoes-energia").session(sessao).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(b.getId())).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get(base() + "/medicoes-energia").session(sessao).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(a.getId()));
        mvc.perform(get(base() + "/medicoes-energia").session(sessao).param("dataInicio", BASE.toString()).param("dataFim", BASE.plusHours(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(base() + "/medicoes-energia").session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultasParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        autenticar(perfil); calcular(0, 1);
        mvc.perform(get(base() + "/medicoes-energia").session(sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void deveExigirSessaoEValidarAlimentador() throws Exception {
        mvc.perform(get(base() + "/perdas-tecnicas")).andExpect(status().isUnauthorized());
        mvc.perform(get(base() + "/medicoes-energia")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/alimentadores/{id}/perdas-tecnicas", Long.MAX_VALUE).session(sessao)
                .param("dataInicio", BASE.toString()).param("dataFim", BASE.plusHours(1).toString())).andExpect(status().isNotFound());
        mvc.perform(get("/api/alimentadores/{id}/medicoes-energia", Long.MAX_VALUE).session(sessao)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim"})
    void deveExigirOsDoisLimitesTemporais(String ausente) throws Exception {
        String presente = ausente.equals("dataInicio") ? "dataFim" : "dataInicio";
        mvc.perform(get(base() + "/perdas-tecnicas").session(sessao).param(presente, BASE.toString())).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio=invalida", "dataFim=2026-01-01T11:00:00", "sort=id,asc", "page=0"})
    void deveRejeitarParametrosInvalidosNoCalculo(String parametro) throws Exception {
        var partes = parametro.split("=", 2);
        var request = get(base() + "/perdas-tecnicas").session(sessao).param("dataInicio", BASE.toString()).param("dataFim", BASE.plusHours(1).toString());
        request.param(partes[0], partes[1]); mvc.perform(request).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim"})
    void deveRejeitarPeriodoVazioRepetidoIgualOuInvertido(String campo) throws Exception {
        mvc.perform(get(base() + "/perdas-tecnicas").session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mvc.perform(get(base() + "/perdas-tecnicas").session(sessao).param(campo, BASE.toString(), BASE.plusHours(1).toString())).andExpect(status().isBadRequest());
        mvc.perform(get(base() + "/perdas-tecnicas").session(sessao).param("dataInicio", BASE.toString()).param("dataFim", BASE.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base() + "/perdas-tecnicas").session(sessao).param("dataInicio", BASE.plusHours(1).toString()).param("dataFim", BASE.toString()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=101", "size=0", "page=abc", "sort=inicioPeriodo,asc", "fonteId=1"})
    void deveRejeitarPaginacaoOuParametrosInvalidos(String filtro) throws Exception {
        var partes = filtro.split("=", 2);
        mvc.perform(get(base() + "/medicoes-energia").session(sessao).param(partes[0], partes[1])).andExpect(status().isBadRequest());
    }

    private PerdaTecnicaResponseDTO calcular(int inicio, int fim) throws Exception {
        var result = mvc.perform(get(base() + "/perdas-tecnicas").session(sessao).param("dataInicio", BASE.plusHours(inicio).toString())
                .param("dataFim", BASE.plusHours(fim).toString())).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), PerdaTecnicaResponseDTO.class);
    }
    private void insuficiente(PerdaTecnicaResponseDTO dto, String cobertura) {
        assertThat(dto.situacao()).isEqualTo(SituacaoPerdaTecnica.DADOS_INSUFICIENTES);
        assertThat(dto.percentualCobertura()).isEqualByComparingTo(cobertura);
        assertThat(dto.totalEnergiaInjetadaMwh()).isNull(); assertThat(dto.totalPerdaEstimadaMwh()).isNull(); assertThat(dto.percentualPonderado()).isNull();
    }
    private String base() { return "/api/alimentadores/" + alimentador.getId(); }
    private Alimentador alimentador() {
        var s = new Subestacao(); s.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(s);
        var a = new Alimentador(); a.setSubestacao(s); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(a); em.flush(); return a;
    }
    private FonteMedicao fonte(Alimentador a) {
        var f = new FonteMedicao(); f.setAtivo(a); f.setOrigem(OrigemMedicao.SIMULADA); f.setIdentificador("PERDAS-" + java.util.UUID.randomUUID()); em.persist(f); em.flush(); return f;
    }
    private MedicaoEnergiaAlimentador leitura(FonteMedicao f, int inicio, int fim, String energia, String percentual) {
        var m = new MedicaoEnergiaAlimentador(); m.setFonte(f); m.setInicioPeriodo(BASE.plusHours(inicio)); m.setFimPeriodo(BASE.plusHours(fim));
        m.setRecebidoEm(BASE.plusDays(1)); m.setEnergiaInjetadaMwh(new BigDecimal(energia)); m.setPercentualPerdaReferencia(new BigDecimal(percentual));
        return medicoes.saveAndFlush(m);
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
