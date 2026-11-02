package br.com.powergrid.controlcenter.controller;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.EventoOperacional.Categoria;
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
class IndicadoresControllerTest {
    private static final String URI = "/api/indicadores/operacionais";
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    private static final OffsetDateTime FIM = INICIO.plusHours(1);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private EntityManager em;
    @Autowired private UsuarioRepository usuarios;
    @MockitoBean private Clock clock;
    private MockHttpSession sessao;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(FIM.toInstant()); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void deveCalcularIndicadoresComFusaoTemporalEInventarioAgrupado() throws Exception {
        var a = sub(); var b = sub(); b.setEstadoOperacional(EstadoOperacional.FALHA);
        evento(a, 0, 900); evento(a, 600, 1200); evento(b, 1800, null); em.flush(); em.clear();
        var dto = consultar(consulta());
        assertThat(dto.totalAtivos()).isEqualTo(2);
        assertThat(dto.quantidadeAtivos()).hasSize(2);
        assertThat(dto.quantidadeAtivos()).extracting(IndicadoresOperacionaisResponseDTO.QuantidadeAtivos::quantidade).containsOnly(1L);
        assertThat(dto.quantidadeIniciadasNoPeriodo()).isEqualTo(3);
        assertThat(dto.quantidadeSobrepostasAoPeriodo()).isEqualTo(3);
        assertThat(dto.quantidadeSemTerminoFisico()).isEqualTo(1);
        assertThat(dto.quantidadeRestabelecimentosNoPeriodo()).isEqualTo(2);
        assertThat(dto.quantidadeRegistrosInconsistentes()).isZero();
        assertThat(dto.tempoInterrompidoSegundos()).isEqualByComparingTo("3000.000000");
        assertThat(dto.tempoObservadoAtivosSegundos()).isEqualByComparingTo("7200.000000");
        assertThat(dto.tempoMedioRestabelecimentoSegundos()).isEqualByComparingTo("750.000000");
        assertThat(dto.disponibilidadeEstimadaPercentual()).isEqualByComparingTo("58.333333");
        assertThat(dto.situacaoDisponibilidade()).isEqualTo(SituacaoDisponibilidade.COBERTURA_NAO_AFERIDA);
        assertThat(dto.calculadoEm()).isEqualTo(FIM);
        assertThat(dto.filtrosAplicados().tipos()).containsExactly(TipoAtivoIndicador.values());
    }

    @Test
    void deveRetornarCemEstimadoComCoberturaNaoAferidaSemOcorrencias() throws Exception {
        sub(); em.flush();
        var dto = consultar(consulta());
        assertThat(dto.disponibilidadeEstimadaPercentual()).isEqualByComparingTo("100.000000");
        assertThat(dto.situacaoDisponibilidade()).isEqualTo(SituacaoDisponibilidade.COBERTURA_NAO_AFERIDA);
        assertThat(dto.tempoMedioRestabelecimentoSegundos()).isNull();
        assertThat(dto.quantidadeRestabelecimentosNoPeriodo()).isZero();
        assertThat(dto.tempoInterrompidoSegundos()).isEqualByComparingTo("0");
    }

    @Test
    void deveRetornarSemPopulacaoSemDividirPorZero() throws Exception {
        var dto = consultar(consulta());
        assertThat(dto.quantidadeAtivos()).isEmpty(); assertThat(dto.totalAtivos()).isZero();
        assertThat(dto.tempoObservadoAtivosSegundos()).isEqualByComparingTo("0");
        assertThat(dto.disponibilidadeEstimadaPercentual()).isNull();
        assertThat(dto.tempoMedioRestabelecimentoSegundos()).isNull();
        assertThat(dto.situacaoDisponibilidade()).isEqualTo(SituacaoDisponibilidade.SEM_POPULACAO_ATIVA);
    }

    @Test
    void deveFiltrarInventarioEInterrupcoesPeloMesmoEscopo() throws Exception {
        var s = sub(); var outra = sub(); var a = new Alimentador(); a.setSubestacao(s);
        a.setNome("Alimentador dos indicadores"); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(a);
        evento(a, 0, 600); evento(outra, 0, 1800); em.flush(); em.clear();
        var dto = consultar(consulta().param("ativoId", a.getId().toString()).param("subestacaoId", s.getId().toString())
                .param("tipos", " ALIMENTADOR,ALIMENTADOR "));
        assertThat(dto.totalAtivos()).isEqualTo(1);
        assertThat(dto.tempoInterrompidoSegundos()).isEqualByComparingTo("600");
        assertThat(dto.disponibilidadeEstimadaPercentual()).isEqualByComparingTo("83.333333");
        assertThat(dto.filtrosAplicados().ativoId()).isEqualTo(a.getId());
        assertThat(dto.filtrosAplicados().subestacaoId()).isEqualTo(s.getId());
        assertThat(dto.filtrosAplicados().tipos()).containsExactly(TipoAtivoIndicador.ALIMENTADOR);
        assertThat(consultar(consulta().param("ativoId", a.getId().toString()).param("tipos", "USINA")).totalAtivos()).isZero();
    }

    @Test
    void devePreservarFusosENormalizarMetadadosParaUtc() throws Exception {
        sub(); em.flush();
        var dto = consultar(get(URI).session(sessao).param("dataInicio", "2026-09-30T21:00:00-03:00")
                .param("dataFim", "2026-10-01T03:00:00+02:00"));
        assertThat(dto.dataInicio()).isEqualTo(INICIO); assertThat(dto.dataFim()).isEqualTo(FIM);
        assertThat(dto.calculadoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(dto.tempoObservadoAtivosSegundos()).isEqualByComparingTo("3600");
    }

    @Test
    void deveDistinguirInterrupcoesAnterioresDasIniciadasNaJanela() throws Exception {
        var s = sub(); evento(s, -600, null); evento(s, -300, 0); evento(s, 3600, null); em.flush();
        var dto = consultar(consulta());
        assertThat(dto.quantidadeIniciadasNoPeriodo()).isZero();
        assertThat(dto.quantidadeSobrepostasAoPeriodo()).isEqualTo(1);
        assertThat(dto.quantidadeSemTerminoFisico()).isEqualTo(1);
        assertThat(dto.tempoInterrompidoSegundos()).isEqualByComparingTo("3600");
        assertThat(dto.disponibilidadeEstimadaPercentual()).isEqualByComparingTo("0");
        assertThat(dto.tempoMedioRestabelecimentoSegundos()).isEqualByComparingTo("300");
    }

    @Test
    void deveSuprimirDisponibilidadeQuandoLegadoNaoTemInicioFisico() throws Exception {
        var s = sub(); var e = evento(s, 0, 600); e.setInicioEfetivo(null); em.flush();
        var dto = consultar(consulta());
        assertThat(dto.quantidadeRegistrosInconsistentes()).isEqualTo(1);
        assertThat(dto.disponibilidadeEstimadaPercentual()).isNull();
        assertThat(dto.situacaoDisponibilidade()).isEqualTo(SituacaoDisponibilidade.DADOS_INCONSISTENTES);
        assertThat(dto.tempoMedioRestabelecimentoSegundos()).isNull();
    }

    @Test
    void deveDetectarConclusaoLegadaSemTerminoFisicoSemAfetarOutroEscopo() throws Exception {
        var s = sub(); var outro = sub(); var e = evento(s, 0, null); e.setSituacao("CONCLUIDO"); em.flush();
        assertThat(consultar(consulta()).situacaoDisponibilidade()).isEqualTo(SituacaoDisponibilidade.DADOS_INCONSISTENTES);
        var dto = consultar(consulta().param("ativoId", outro.getId().toString()));
        assertThat(dto.quantidadeRegistrosInconsistentes()).isZero();
        assertThat(dto.disponibilidadeEstimadaPercentual()).isEqualByComparingTo("100");
    }

    @ParameterizedTest
    @CsvSource({"1,99.999998", "3,99.999992"})
    void deveArredondarDisponibilidadeComHalfEvenSomenteNoResultadoFinal(int micros, String esperado) throws Exception {
        var s = sub(); var e = evento(s, 0, 1); e.setTerminoEfetivo(INICIO.plusNanos(micros * 1000L)); em.flush();
        var dto = consultar(get(URI).session(sessao).param("dataInicio", INICIO.toString()).param("dataFim", INICIO.plusSeconds(40).toString()));
        assertThat(dto.disponibilidadeEstimadaPercentual()).isEqualByComparingTo(esperado);
    }

    @ParameterizedTest
    @EnumSource(value = PerfilAcesso.class, names = {"OPERADOR", "TECNICO_MANUTENCAO"})
    void deveBloquearPerfisSemPermissao(PerfilAcesso perfil) throws Exception {
        autenticar(perfil); mvc.perform(consulta()).andExpect(status().isForbidden());
    }

    @Test
    void deveExigirAutenticacao() throws Exception {
        mvc.perform(get(URI).param("dataInicio", INICIO.toString()).param("dataFim", FIM.toString())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim"})
    void deveExigirLimitesTemporais(String ausente) throws Exception {
        mvc.perform(get(URI).session(sessao).param(ausente.equals("dataInicio") ? "dataFim" : "dataInicio", INICIO.toString()))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"dataInicio,invalida", "dataFim,2026-10-01T01:00:00", "dataInicio,2026-10-01", "dataFim,2026-10-01T01:00:01Z",
            "dataInicio,2026-10-01T01:00:00Z", "dataInicio,2026-10-01T02:00:00Z", "ativoId,0", "ativoId,-1", "ativoId,abc",
            "ativoId,9223372036854775808", "subestacaoId,0", "subestacaoId,1.5", "tipos,MEDIDOR", "tipos,subestacao",
            "tipos,SUBESTACAO|USINA", "page,0", "size,20", "sort,id", "desconhecido,1"})
    void deveRejeitarParametrosInvalidos(String nome, String valor) throws Exception {
        mvc.perform(consultaCom(nome, valor)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dataInicio", "dataFim", "ativoId", "tipos", "subestacaoId"})
    void deveRejeitarParametrosVaziosOuRepetidos(String nome) throws Exception {
        var vazio = consultaCom(nome, "");
        mvc.perform(vazio).andExpect(status().isBadRequest());
        mvc.perform(consulta().param(nome, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO,", ",USINA", "SUBESTACAO,,USINA"})
    void deveRejeitarCamadasVaziasNaLista(String tipos) throws Exception {
        mvc.perform(consulta().param("tipos", tipos)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ativoId", "subestacaoId"})
    void deveRetornar404ParaIdentificadorInexistente(String nome) throws Exception {
        mvc.perform(consulta().param(nome, Long.toString(Long.MAX_VALUE))).andExpect(status().isNotFound());
    }

    @Test
    void deveArredondarMediaDeRestabelecimentoSomenteNoResultadoFinal() throws Exception {
        var s = sub(); evento(s, 0, 0); evento(s, 10, 10); evento(s, 20, 21); em.flush();
        var dto = consultar(consulta());
        assertThat(dto.quantidadeRestabelecimentosNoPeriodo()).isEqualTo(3);
        assertThat(dto.tempoMedioRestabelecimentoSegundos()).isEqualByComparingTo("0.333333");
    }

    private MockHttpServletRequestBuilder consulta() {
        return get(URI).session(sessao).param("dataInicio", INICIO.toString()).param("dataFim", FIM.toString());
    }

    private MockHttpServletRequestBuilder consultaCom(String nome, String valor) {
        var request = get(URI).session(sessao)
                .param("dataInicio", nome.equals("dataInicio") ? valor : INICIO.toString())
                .param("dataFim", nome.equals("dataFim") ? valor : FIM.toString());
        if (!nome.equals("dataInicio") && !nome.equals("dataFim")) request.param(nome, valor);
        return request;
    }

    private IndicadoresOperacionaisResponseDTO consultar(MockHttpServletRequestBuilder request) throws Exception {
        var resultado = mvc.perform(request).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn();
        return mapper.readValue(resultado.getResponse().getContentAsString(), IndicadoresOperacionaisResponseDTO.class);
    }

    private Subestacao sub() {
        var s = new Subestacao(); s.setNome("Subestação dos indicadores"); s.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(s); return s;
    }

    private EventoOperacional evento(AtivoRede ativo, int inicio, Integer fim) {
        var u = usuarios.findByEmail("operador@powergrid.local").orElseThrow();
        var e = new EventoOperacional(); e.setAtivo(ativo); e.setCategoria(Categoria.INTERRUPCAO_FORNECIMENTO);
        e.setRegistradorUsuario(u); e.setResponsavelUsuario(u); e.setInicioEfetivo(INICIO.plusSeconds(inicio));
        e.setTerminoEfetivo(fim == null ? null : INICIO.plusSeconds(fim)); e.setSituacao(fim == null ? "ABERTO" : "CONCLUIDO"); em.persist(e); return e;
    }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mvc.perform(get("/api/auth/csrf")).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = mapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        var usuario = usuarios.findAll().stream().filter(u -> u.getPerfil() == perfil).findFirst().orElseThrow();
        mvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario.getEmail()).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
    }
}
