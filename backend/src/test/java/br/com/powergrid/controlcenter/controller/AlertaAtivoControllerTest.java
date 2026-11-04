package br.com.powergrid.controlcenter.controller;

import java.util.List;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.support.AlertasDashboardFixture;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static br.com.powergrid.controlcenter.support.AlertasDashboardFixture.INSTANTE;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class AlertaAtivoControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;
    private MockHttpSession sessao;
    private static final String URI = "/api/alertas/ativos";

    @BeforeEach
    void preparar() throws Exception {
        AlertasDashboardFixture.inserir(jdbc);
        autenticar(PerfilAcesso.OPERADOR);
    }

    @Test
    void deveRetornarPaginaPadraoComIdentidadeTopologiaESnapshotsDeTensao() throws Exception {
        mvc.perform(get(URI).session(sessao)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true)).andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].*", hasSize(27)))
                .andExpect(jsonPath("$.content[0].idOrigem").value(1301))
                .andExpect(jsonPath("$.content[0].tipoAlerta").value("TENSAO"))
                .andExpect(jsonPath("$.content[0].tipoAtivo").value("LINHA"))
                .andExpect(jsonPath("$.content[0].criticidade").value("ALTA"))
                .andExpect(jsonPath("$.content[0].escopo").value("ATIVO"))
                .andExpect(jsonPath("$.content[0].ativoId").value(1003))
                .andExpect(jsonPath("$.content[0].fonteId").value(1101))
                .andExpect(jsonPath("$.content[0].medicaoId").value(1201))
                .andExpect(jsonPath("$.content[0].statusOrigem").value("ATIVO"))
                .andExpect(jsonPath("$.content[0].subtipo").value("SUBTENSAO"))
                .andExpect(jsonPath("$.content[0].ocorridoEm").value(INSTANTE.toString()))
                .andExpect(jsonPath("$.content[0].tensaoMedidaKv").value(90))
                .andExpect(jsonPath("$.content[0].tensaoReferenciaKv").value(100))
                .andExpect(jsonPath("$.content[0].limiteInferiorKv").value(95))
                .andExpect(jsonPath("$.content[0].limiteSuperiorKv").value(105))
                .andExpect(jsonPath("$.content[0].descricao").value("Subtensão: 90,00 kV (referência: 100,00 kV; faixa: 95,00 a 105,00 kV)"))
                .andExpect(jsonPath("$.content[0].reconhecidoEm").value(nullValue()))
                .andExpect(jsonPath("$.content[0].reconhecidoPorUsuarioId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].resolvidoEm").value(nullValue()))
                .andExpect(jsonPath("$.content[0].resolvidoPorUsuarioId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].restabelecidoEm").value(nullValue()));
    }

    @Test
    void deveExporAutoriaDoGenericoReconhecidoSemExporCredenciais() throws Exception {
        Long usuario = jdbc.queryForObject("SELECT MIN(id) FROM usuario", Long.class);
        jdbc.update("UPDATE alerta SET situacao='RECONHECIDO' WHERE id=1301");
        jdbc.update("INSERT INTO historico_alerta (alerta_id, usuario_id, situacao_anterior, situacao_nova, alterado_em, comentario) VALUES (1301, ?, 'ABERTO', 'RECONHECIDO', ?, 'Verificado pelo operador')", usuario, INSTANTE.plusMinutes(1));
        mvc.perform(get(URI).session(sessao).param("tipoAlerta", "GENERICO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].statusOrigem").value("RECONHECIDO"))
                .andExpect(jsonPath("$.content[0].descricao").value("Aviso geral"))
                .andExpect(jsonPath("$.content[0].mensagem").value("Aviso geral"))
                .andExpect(jsonPath("$.content[0].escopo").value("REDE_MONITORADA"))
                .andExpect(jsonPath("$.content[0].ativoId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].tipoAtivo").value(nullValue()))
                .andExpect(jsonPath("$.content[0].reconhecidoPorUsuarioId").value(usuario))
                .andExpect(jsonPath("$.content[0].reconhecidoEm").value(INSTANTE.plusMinutes(1).toString()))
                .andExpect(jsonPath("$.content[0].resolvidoEm").value(nullValue()))
                .andExpect(jsonPath("$..senha").doesNotExist()).andExpect(jsonPath("$..password").doesNotExist());
    }

    @Test
    void deveExporSnapshotsEDescricoesDeSobrecargaEComunicacao() throws Exception {
        ativarTodos();
        mvc.perform(get(URI).session(sessao).param("tipoAlerta", "SOBRECARGA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].tipoAtivo").value("TRANSFORMADOR"))
                .andExpect(jsonPath("$.content[0].ativoId").value(1005))
                .andExpect(jsonPath("$.content[0].fonteId").value(1102))
                .andExpect(jsonPath("$.content[0].medicaoId").value(1202))
                .andExpect(jsonPath("$.content[0].cargaPercentual").value(120))
                .andExpect(jsonPath("$.content[0].limiarPercentual").value(100))
                .andExpect(jsonPath("$.content[0].descricao").value("Sobrecarga de 120,00% (limiar: 100,00%)"));
        mvc.perform(get(URI).session(sessao).param("tipoAlerta", "COMUNICACAO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].tipoAtivo").value("LINHA"))
                .andExpect(jsonPath("$.content[0].medicaoId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].monitoradaDesde").value(INSTANTE.minusDays(1).toString()))
                .andExpect(jsonPath("$.content[0].ultimaComunicacaoEm").value(INSTANTE.minusHours(1).toString()))
                .andExpect(jsonPath("$.content[0].toleranciaSegundos").value(300))
                .andExpect(jsonPath("$.content[0].descricao").value("Perda de comunicação na fonte 1101 (tolerância: 300 s)"));
    }

    @Test
    void devePaginarPorCriticidadeSemColisaoEntreIdsDeOrigensDiferentes() throws Exception {
        ativarTodos();
        mvc.perform(get(URI).session(sessao).param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[*].tipoAlerta", contains("SOBRECARGA", "TENSAO")));
        mvc.perform(get(URI).session(sessao).param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[*].tipoAlerta", contains("COMUNICACAO", "GENERICO")));
    }

    @Test
    void deveCombinarFiltrosIncluindoTopologiaEJanelaComFuso() throws Exception {
        ativarTodos();
        mvc.perform(get(URI).session(sessao).param("tipoAlerta", "TENSAO").param("criticidade", "ALTA")
                .param("escopo", "ATIVO").param("ativoId", "1003").param("fonteId", "1101")
                .param("tipoAtivo", "LINHA").param("subestacaoId", "1002")
                .param("dataInicio", "2026-01-02T09:00:00.123456-03:00").param("dataFim", "2026-01-02T12:00:01Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].tipoAlerta").value("TENSAO"));
    }

    @Test
    void deveRespeitarInicioInclusivoEFimExclusivoComFiltrosUnilaterais() throws Exception {
        mvc.perform(get(URI).session(sessao).param("dataInicio", INSTANTE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(URI).session(sessao).param("dataFim", INSTANTE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void deveRetornarPaginaVaziaParaFiltrosSemCorrespondenciaEPaginaDistante() throws Exception {
        for (String campo : List.of("ativoId", "fonteId", "subestacaoId")) {
            mvc.perform(get(URI).session(sessao).param(campo, Long.toString(Long.MAX_VALUE)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                    .andExpect(jsonPath("$.content", hasSize(0)));
        }
        mvc.perform(get(URI).session(sessao).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(100)).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @ParameterizedTest
    @CsvSource(value = {"page|-1", "page|abc", "page|2147483648", "size|0", "size|101", "size|1.5",
            "sort|criticidade,asc", "statusOrigem|RESOLVIDO", "desconhecido|1", "tipos|TENSAO",
            "tipoAlerta|TENSAO_ANORMAL", "tipoAlerta|tensao", "criticidade|OUTRA", "criticidade|alta",
            "escopo|REDE", "tipoAtivo|OUTRO", "tipoAtivo|linha", "ativoId|0", "ativoId|-1", "ativoId|abc",
            "ativoId|9223372036854775808", "fonteId|0", "fonteId|1.1", "subestacaoId|-1", "subestacaoId|abc",
            "subestacaoId|9223372036854775808", "dataInicio|invalido", "dataInicio|2026-01-02T12:00:00",
            "dataFim|2026-01-02T12:00:00", "ativoId|+1"}, delimiter = '|')
    void deveRejeitarFiltrosInvalidosComErroPadronizado(String campo, String valor) throws Exception {
        mvc.perform(get(URI).session(sessao).param(campo, valor)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", not(blankOrNullString())))
                .andExpect(jsonPath("$.path").value(URI)).andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "dataInicio", "dataFim", "tipoAlerta", "criticidade", "escopo", "ativoId", "fonteId", "tipoAtivo", "subestacaoId", "sort", "statusOrigem", "desconhecido"})
    void deveRejeitarParametrosVaziosEmBrancoOuRepetidos(String campo) throws Exception {
        for (String valor : List.of("", " "))
            mvc.perform(get(URI).session(sessao).param(campo, valor)).andExpect(status().isBadRequest());
        mvc.perform(get(URI).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-02T12:00:00Z", "2026-01-02T11:59:59Z", "2026-01-02T09:00:00-03:00"})
    void deveRejeitarPeriodoInvertidoOuIgualInclusiveComOffsetsDistintos(String fim) throws Exception {
        mvc.perform(get(URI).session(sessao).param("dataInicio", "2026-01-02T12:00:00Z").param("dataFim", fim))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultaParaTodosOsPerfisAutenticados(PerfilAcesso perfil) throws Exception {
        autenticar(perfil);
        mvc.perform(get(URI).session(sessao)).andExpect(status().isOk());
    }

    @Test
    void deveExigirAutenticacao() throws Exception { mvc.perform(get(URI)).andExpect(status().isUnauthorized()); }

    private void ativarTodos() {
        jdbc.update("UPDATE alerta SET situacao='ABERTO' WHERE id=1301");
        jdbc.update("UPDATE alerta_sobrecarga SET status='ATIVO', reconhecido_em=NULL, reconhecido_por_usuario_id=NULL WHERE id=1301");
        jdbc.update("UPDATE alerta_comunicacao SET status='ATIVO', restabelecido_em=NULL WHERE id=1301");
    }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        String email = jdbc.queryForObject("SELECT email FROM usuario WHERE perfil = ?", String.class, perfil.name());
        var inicio = mvc.perform(get("/api/auth/csrf")).andReturn();
        sessao = (MockHttpSession) inicio.getRequest().getSession(false);
        var csrf = mapper.readValue(inicio.getResponse().getContentAsString(), CsrfResponse.class);
        mvc.perform(post("/api/auth/login").session(sessao).header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", email).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
    }
}
