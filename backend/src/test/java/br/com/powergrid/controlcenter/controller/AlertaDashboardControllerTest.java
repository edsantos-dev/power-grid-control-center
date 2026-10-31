package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AlertaDashboardControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    @Autowired private AlertaService genericos;
    @Autowired private FonteMedicaoService fontes;
    @Autowired private MedicaoLinhaService medicoesLinha;
    @Autowired private MedicaoTransformadorService medicoesTransformador;
    @Autowired private AlertaComunicacaoService comunicacao;
    @Autowired private AlertaSobrecargaService sobrecargas;
    @Autowired private AlertaTensaoService tensoes;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-10-31T12:00:00Z");
    private MockHttpSession sessao;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void devePaginarGlobalmenteComDesempatesEDatasNulasAoFinal() throws Exception {
        var c = cenario();
        var mesmoInstante = generico("Outro aviso", AGORA);
        var antigo = generico("Antigo", AGORA.minusSeconds(1));
        var recente = generico("Recente", AGORA.plusSeconds(1));
        var legado = new Alerta(); legado.setEscopo(EscopoAlerta.REDE_MONITORADA); entityManager.persist(legado);
        entityManager.flush(); entityManager.clear();
        var primeira = consultar(params("size", "3"));
        assertThat(primeira.totalElements()).isEqualTo(8); assertThat(primeira.totalPages()).isEqualTo(3);
        assertThat(primeira.first()).isTrue(); assertThat(primeira.last()).isFalse();
        assertThat(primeira.content()).extracting(AlertaDashboardResponseDTO::tipoAlerta)
                .containsExactly(TipoAlertaDashboard.GENERICO, TipoAlertaDashboard.COMUNICACAO, TipoAlertaDashboard.GENERICO);
        assertThat(primeira.content()).extracting(AlertaDashboardResponseDTO::idOrigem)
                .containsExactly(recente, c.comunicacaoId(), mesmoInstante);
        var segunda = consultar(params("size", "3", "page", "1"));
        assertThat(segunda.content()).extracting(AlertaDashboardResponseDTO::tipoAlerta)
                .containsExactly(TipoAlertaDashboard.GENERICO, TipoAlertaDashboard.SOBRECARGA, TipoAlertaDashboard.TENSAO);
        assertThat(segunda.content().getFirst().idOrigem()).isEqualTo(c.genericoId());
        var ultima = consultar(params("size", "3", "page", "2"));
        assertThat(ultima.last()).isTrue();
        assertThat(ultima.content()).extracting(AlertaDashboardResponseDTO::idOrigem).containsExactly(antigo, legado.getId());
        assertThat(ultima.content().getLast().ocorridoEm()).isNull();
        assertThat(ultima.content().getLast().criticidade()).isNull();
        assertThat(ultima.content().getLast().descricao()).isNull();
    }

    @Test
    void deveAplicarTodosOsFiltrosIsoladosECombinados() throws Exception {
        var c = cenario();
        assertThat(consultar(params("criticidade", "BAIXA")).totalElements()).isEqualTo(1);
        assertThat(consultar(params("criticidade", "ALTA")).totalElements()).isEqualTo(2);
        assertThat(consultar(params("criticidade", "MEDIA")).totalElements()).isEqualTo(1);
        assertThat(consultar(params("criticidade", "CRITICA")).content()).isEmpty();
        assertThat(consultar(params("escopo", "REDE_MONITORADA")).totalElements()).isEqualTo(1);
        assertThat(consultar(params("escopo", "ATIVO")).totalElements()).isEqualTo(3);
        assertThat(consultar(params("ativoId", c.transformadorId().toString())).totalElements()).isEqualTo(2);
        assertThat(consultar(params("fonteId", c.fonteComunicacaoId().toString())).totalElements()).isEqualTo(1);
        var filtro = consultar(params("tipoAlerta", "TENSAO", "criticidade", "ALTA", "ativoId", c.linhaId().toString(),
                "fonteId", c.fonteLinhaId().toString(), "escopo", "ATIVO", "statusOrigem", "ATIVO",
                "dataInicio", "2026-10-31T09:00:00-03:00", "dataFim", "2026-10-31T12:00:01Z"));
        assertThat(filtro.content()).hasSize(1);
        assertThat(filtro.content().getFirst().idOrigem()).isEqualTo(c.tensaoId());
        assertThat(consultar(params("tipoAlerta", "TENSAO", "statusOrigem", "RESTABELECIDO")).content()).isEmpty();
        assertThat(consultar(params("ativoId", Long.toString(Long.MAX_VALUE))).content()).isEmpty();
        assertThat(consultar(params("fonteId", Long.toString(Long.MAX_VALUE))).content()).isEmpty();
    }

    @Test
    void deveFiltrarPeriodoPeloInstanteDaOcorrenciaComInicioInclusivoEFimExclusivo() throws Exception {
        cenario(); generico("Anterior", AGORA.minusSeconds(1));
        assertThat(consultar(params("dataInicio", AGORA.toString())).totalElements()).isEqualTo(4);
        assertThat(consultar(params("dataFim", AGORA.toString())).totalElements()).isEqualTo(1);
        assertThat(consultar(params("dataInicio", AGORA.plusSeconds(1).toString())).content()).isEmpty();
        var legado = new Alerta(); legado.setEscopo(EscopoAlerta.REDE_MONITORADA); entityManager.persist(legado); entityManager.flush();
        assertThat(consultar(params("dataInicio", AGORA.toString())).totalElements()).isEqualTo(4);
        assertThat(consultar(params("dataFim", AGORA.toString())).totalElements()).isEqualTo(1);
    }

    @Test
    void deveFormatarDescricoesSemAlterarNumerosTecnicos() throws Exception {
        var c = cenario();
        var sob = consultar(params("tipoAlerta", "SOBRECARGA")).content().getFirst();
        assertThat(sob.descricao()).isEqualTo("Sobrecarga de 115,00% (limiar: 100,00%)");
        assertThat(sob.cargaPercentual()).isEqualByComparingTo("115");
        assertThat(sob.medicaoId()).isNotNull(); assertThat(sob.fonteId()).isEqualTo(c.fonteTransformadorId());
        var tensao = consultar(params("tipoAlerta", "TENSAO")).content().getFirst();
        assertThat(tensao.descricao()).isEqualTo("Subtensão: 90,123456 kV (referência: 100,00 kV; faixa: 95,00 a 105,00 kV)");
        assertThat(tensao.tensaoMedidaKv()).isEqualByComparingTo("90.123456");
        assertThat(tensao.subtipo()).isEqualTo("SUBTENSAO");
        assertThat(consultar(params("tipoAlerta", "COMUNICACAO")).content().getFirst().descricao())
                .isEqualTo("Perda de comunicação na fonte " + c.fonteComunicacaoId() + " (tolerância: 300 s)");
        assertThat(consultar(params("tipoAlerta", "GENERICO")).content().getFirst().descricao()).isEqualTo("Mensagem manual");
        medicoesLinha.ingerir(c.linhaId(), new MedicaoLinhaRequestDTO(c.fonteLinhaId(), historico(), new BigDecimal("110"), null, null, null, null));
        assertThat(consultar(params("tipoAlerta", "TENSAO")).content().getFirst().descricao()).startsWith("Sobretensão: 110,00 kV");
        medicoesTransformador.ingerir(c.transformadorId(), new MedicaoTransformadorRequestDTO(c.fonteTransformadorId(), historico(), new BigDecimal("11.500001")));
        var preciso = consultar(params("tipoAlerta", "SOBRECARGA")).content().getFirst();
        assertThat(preciso.descricao()).isEqualTo("Sobrecarga de 115,00001% (limiar: 100,00%)");
        assertThat(preciso.cargaPercentual()).isEqualByComparingTo("115.000010");
    }

    @Test
    void devePreservarReconhecimentoRestabelecimentoEHistoricoCompleto() throws Exception {
        var c = cenario();
        Long usuarioId = entityManager.createQuery("select u.id from Usuario u where u.perfil = :perfil", Long.class)
                .setParameter("perfil", PerfilAcesso.GERENTE_OPERACOES).getSingleResult();
        when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        sobrecargas.reconhecer(c.sobrecargaId(), usuarioId); tensoes.reconhecer(c.tensaoId(), usuarioId);
        medicoesTransformador.ingerir(c.transformadorId(), new MedicaoTransformadorRequestDTO(c.fonteComunicacaoId(), historico(), new BigDecimal("8")));
        entityManager.find(Alerta.class, c.genericoId()).setSituacao(SituacaoAlerta.RESOLVIDO);
        entityManager.flush(); entityManager.clear();
        var reconhecidos = consultar(params("statusOrigem", "RECONHECIDO"));
        assertThat(reconhecidos.totalElements()).isEqualTo(2);
        for (var a : reconhecidos.content()) {
            assertThat(a.reconhecidoPorUsuarioId()).isEqualTo(usuarioId);
            assertThat(a.reconhecidoEm().toInstant()).isEqualTo(AGORA.plusSeconds(60));
            assertThat(a.reconhecidoEm().getOffset()).isEqualTo(ZoneOffset.UTC);
            assertThat(a.restabelecidoEm()).isNull();
        }
        var restabelecido = consultar(params("statusOrigem", "RESTABELECIDO")).content().getFirst();
        assertThat(restabelecido.tipoAlerta()).isEqualTo(TipoAlertaDashboard.COMUNICACAO);
        assertThat(restabelecido.restabelecidoEm().toInstant()).isEqualTo(AGORA.plusSeconds(60));
        assertThat(restabelecido.reconhecidoEm()).isNull(); assertThat(restabelecido.reconhecidoPorUsuarioId()).isNull();
        assertThat(restabelecido.monitoradaDesde().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(restabelecido.ocorridoEm().toInstant()).isEqualTo(AGORA);
        assertThat(consultar(params("statusOrigem", "RESOLVIDO")).content()).hasSize(1);
        assertThat(consultar(params()).totalElements()).isEqualTo(4);
        assertThat(consultar(params("dataInicio", AGORA.plusSeconds(1).toString())).content()).isEmpty();
    }

    @Test
    void deveAplicarPadroesLimiteMaximoEPaginaDistante() throws Exception {
        var vazio = consultar(params());
        assertThat(vazio.content()).isEmpty(); assertThat(vazio.page()).isZero(); assertThat(vazio.size()).isEqualTo(20);
        cenario();
        assertThat(consultar(params("size", "100")).content()).hasSize(4);
        assertThat(consultar(params("page", "2147483647", "size", "100")).content()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "page|abc", "page|2147483648", "size|0", "size|101", "size|1.5", "sort|ocorridoEm,asc",
            "tipoAlerta|OUTRO", "tipoAlerta|tensao", "criticidade|OUTRA", "criticidade|alta", "escopo|REDE", "ativoId|0", "ativoId|-1",
            "ativoId|abc", "ativoId|9223372036854775808", "fonteId|0", "fonteId|abc", "fonteId|9223372036854775808",
            "statusOrigem|OUTRO", "statusOrigem|ativo", "dataInicio|invalido", "dataFim|2026-10-31T12:00:00"}, delimiter = '|')
    void deveRejeitarParametrosInvalidos(String campo, String valor) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, valor)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "tipoAlerta", "criticidade", "escopo", "ativoId", "fonteId", "statusOrigem", "dataInicio", "dataFim"})
    void deveRejeitarParametrosVaziosOuRepetidos(String campo) throws Exception {
        mockMvc.perform(get(base()).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base()).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarPeriodoIgualOuInvertido() throws Exception {
        for (String fim : List.of(AGORA.toString(), AGORA.minusSeconds(1).toString())) {
            mockMvc.perform(get(base()).session(sessao).param("dataInicio", AGORA.toString()).param("dataFim", fim))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultaAutenticadaParaTodosOsPerfis(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        assertThat(consultar(params()).content()).isEmpty();
    }

    @Test
    void deveExigirSessao() throws Exception {
        mockMvc.perform(get(base())).andExpect(status().isUnauthorized());
    }

    private Cenario cenario() {
        var origem = subestacao("Origem"); var destino = subestacao("Destino");
        var linha = new LinhaTransmissao(); linha.setNome("Linha"); linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        linha.setSubestacaoOrigem(origem); linha.setSubestacaoDestino(destino); linha.setTensaoOperacaoKv(new BigDecimal("100")); entityManager.persist(linha);
        var alimentador = new Alimentador(); alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setSubestacao(origem); entityManager.persist(alimentador);
        var trafo = new Transformador(); trafo.setNome("Transformador"); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        trafo.setCapacidadeMva(BigDecimal.TEN); trafo.setAlimentador(alimentador); entityManager.persist(trafo); entityManager.flush();
        var fl = fontes.criar(linha.getId(), new FonteMedicaoRequestDTO("DASH-LINHA", OrigemMedicao.SIMULADA, origem.getId()));
        var ft = fontes.criarParaTransformador(trafo.getId(), new FonteMedicaoRequestDTO("DASH-TRAFO", OrigemMedicao.SIMULADA, null));
        var fc = fontes.criarParaTransformador(trafo.getId(), new FonteMedicaoRequestDTO("DASH-COM", OrigemMedicao.SIMULADA, null));
        medicoesLinha.ingerir(linha.getId(), new MedicaoLinhaRequestDTO(fl.id(), historico(), new BigDecimal("90.123456"), null, null, null, null));
        medicoesTransformador.ingerir(trafo.getId(), new MedicaoTransformadorRequestDTO(ft.id(), historico(), new BigDecimal("11.5")));
        entityManager.find(FonteMedicao.class, fc.id()).setMonitoradaDesde(AGORA.minusSeconds(301).atOffset(ZoneOffset.UTC));
        comunicacao.avaliar();
        Long generico = generico("Mensagem manual", AGORA);
        entityManager.flush();
        Long tensao = entityManager.createQuery("select a.id from AlertaTensao a", Long.class).getSingleResult();
        Long sobrecarga = entityManager.createQuery("select a.id from AlertaSobrecarga a", Long.class).getSingleResult();
        Long comunicacaoId = entityManager.createQuery("select a.id from AlertaComunicacao a", Long.class).getSingleResult();
        return new Cenario(generico, tensao, sobrecarga, comunicacaoId, linha.getId(), trafo.getId(), fl.id(), ft.id(), fc.id());
    }

    private Long generico(String mensagem, Instant instante) {
        var dto = genericos.criar(new AlertaRequestDTO(mensagem, Criticidade.BAIXA, EscopoAlerta.REDE_MONITORADA, null));
        entityManager.find(Alerta.class, dto.id()).setGeradoEm(instante.atOffset(ZoneOffset.UTC));
        entityManager.flush(); return dto.id();
    }

    private Subestacao subestacao(String nome) {
        var sub = new Subestacao(); sub.setNome(nome); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); entityManager.persist(sub); return sub;
    }

    private OffsetDateTime historico() { return OffsetDateTime.parse("2026-01-01T00:00:00Z"); }
    private String base() { return "/api/alertas-dashboard"; }

    private MultiValueMap<String, String> params(String... pares) {
        var params = new LinkedMultiValueMap<String, String>();
        for (int i = 0; i < pares.length; i += 2) params.add(pares[i], pares[i + 1]);
        return params;
    }

    private PageResponseDTO<AlertaDashboardResponseDTO> consultar(MultiValueMap<String, String> params) throws Exception {
        var result = mockMvc.perform(get(base()).session(sessao).params(params)).andExpect(status().isOk()).andReturn();
        var tipo = objectMapper.getTypeFactory().constructParametricType(PageResponseDTO.class, AlertaDashboardResponseDTO.class);
        return objectMapper.readValue(result.getResponse().getContentAsString(), tipo);
    }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        String email = entityManager.createQuery("select u.email from Usuario u where u.perfil = :perfil", String.class)
                .setParameter("perfil", perfil).getSingleResult();
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var csrf = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
    }

    private record Cenario(Long genericoId, Long tensaoId, Long sobrecargaId, Long comunicacaoId,
            Long linhaId, Long transformadorId, Long fonteLinhaId, Long fonteTransformadorId, Long fonteComunicacaoId) {
    }
}
