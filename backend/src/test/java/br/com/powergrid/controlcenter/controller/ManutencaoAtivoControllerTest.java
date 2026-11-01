package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ManutencaoAtivoControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager em;
    @Autowired private EntityManagerFactory emf;
    @MockitoBean private Clock clock;
    private static final Instant AGORA = Instant.parse("2026-11-01T12:00:00Z");
    private Subestacao ativo;
    private Subestacao outro;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        ativo = subestacao("Principal"); outro = subestacao("Outro");
        autenticar(PerfilAcesso.GERENTE_OPERACOES);
    }

    @Test
    void deveAgruparEventoEUltimaTransicaoPreservandoDadosEInstantesUtc() throws Exception {
        Long id = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Inspeção do equipamento");
        var inicio = transicionar(id, "ABERTO", "EM_ANDAMENTO", AGORA);
        var conclusao = transicionar(id, "EM_ANDAMENTO", "CONCLUIDO", AGORA.plusSeconds(60));
        em.clear();
        var pagina = consultar(ativo.getId(), params());
        assertThat(pagina.totalElements()).isEqualTo(1);
        var item = pagina.content().getFirst();
        assertThat(item.evento().id()).isEqualTo(id); assertThat(item.evento().ativoId()).isEqualTo(ativo.getId());
        assertThat(item.evento().categoria()).isEqualTo(EventoOperacional.Categoria.MANUTENCAO);
        assertThat(item.evento().situacao()).isEqualTo("CONCLUIDO");
        assertThat(item.evento().descricao()).isEqualTo("Inspeção do equipamento");
        assertThat(item.evento().responsavelUsuarioId()).isEqualTo(usuario(PerfilAcesso.OPERADOR).getId());
        assertThat(item.evento().registradorUsuarioId()).isEqualTo(usuario(PerfilAcesso.GERENTE_OPERACOES).getId());
        assertThat(item.evento().inicioEfetivo().toInstant()).isEqualTo(inicio.alteradoEm().toInstant());
        assertThat(item.evento().terminoEfetivo().toInstant()).isEqualTo(conclusao.alteradoEm().toInstant());
        assertThat(item.evento().inicioEfetivo().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(item.evento().inicioPrevisto().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(item.ultimaTransicao()).isEqualTo(conclusao);
        assertThat(item.ultimaTransicao().usuarioId()).isEqualTo(usuario(PerfilAcesso.GERENTE_OPERACOES).getId());
        mockMvc.perform(get("/api/eventos-operacionais/{id}/transicoes", id).session(sessao))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void devePreservarEventosSemTransicoesEIncluirTodosOsEstadosPorPadrao() throws Exception {
        Long aberto = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Planejada");
        Long andamento = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Em execução");
        transicionar(andamento, "ABERTO", "EM_ANDAMENTO", AGORA);
        Long legado = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Registro anterior");
        var evento = em.find(EventoOperacional.class, legado); evento.setSituacao("CONCLUIDO");
        evento.setInicioEfetivo(AGORA.minusSeconds(60).atOffset(ZoneOffset.UTC)); evento.setTerminoEfetivo(AGORA.minusSeconds(30).atOffset(ZoneOffset.UTC));
        em.flush(); em.clear();
        var pagina = consultar(ativo.getId(), params());
        assertThat(pagina.content()).extracting(m -> m.evento().id()).containsExactly(andamento, legado, aberto);
        assertThat(pagina.content().get(1).ultimaTransicao()).isNull();
        assertThat(pagina.content().getLast().ultimaTransicao()).isNull();
    }

    @Test
    void deveIsolarAtivosECategoriaEManterPaginaVaziaParaAtivoSemManutencoes() throws Exception {
        criar(outro.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Outro ativo");
        criar(ativo.getId(), PerfilAcesso.OPERADOR, "FALHA_EQUIPAMENTO", "Falha");
        var vazia = consultar(ativo.getId(), params());
        assertThat(vazia.content()).isEmpty(); assertThat(vazia.totalElements()).isZero();
        assertThat(vazia.page()).isZero(); assertThat(vazia.size()).isEqualTo(20);
        assertThat(consultar(outro.getId(), params()).totalElements()).isEqualTo(1);
    }

    @Test
    void deveAplicarFiltrosHttpComInicioInclusivoFimExclusivoEResponsavel() throws Exception {
        Long primeiro = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Primeiro");
        transicionar(primeiro, "ABERTO", "EM_ANDAMENTO", AGORA);
        Long tecnico = criar(ativo.getId(), PerfilAcesso.TECNICO_MANUTENCAO, "MANUTENCAO", "Técnico");
        transicionar(tecnico, "ABERTO", "EM_ANDAMENTO", AGORA.plusSeconds(1));
        Long segundo = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Segundo");
        transicionar(segundo, "ABERTO", "EM_ANDAMENTO", AGORA.plusSeconds(2));
        Long limiteFinal = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Limite final");
        transicionar(limiteFinal, "ABERTO", "EM_ANDAMENTO", AGORA.plusSeconds(3));
        criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Não iniciada");
        var filtros = params("situacao", "EM_ANDAMENTO", "responsavelUsuarioId", usuario(PerfilAcesso.OPERADOR).getId().toString(),
                "dataInicio", "2026-11-01T09:00:00-03:00", "dataFim", AGORA.plusSeconds(3).toString(), "size", "1");
        var pagina = consultar(ativo.getId(), filtros);
        assertThat(pagina.totalElements()).isEqualTo(2); assertThat(pagina.totalPages()).isEqualTo(2);
        assertThat(pagina.content().getFirst().evento().id()).isEqualTo(segundo);
        filtros.set("page", "1");
        assertThat(consultar(ativo.getId(), filtros).content().getFirst().evento().id()).isEqualTo(primeiro);
        assertThat(consultar(ativo.getId(), params("responsavelUsuarioId", usuario(PerfilAcesso.TECNICO_MANUTENCAO).getId().toString())).content())
                .extracting(m -> m.evento().id()).containsExactly(tecnico);
        assertThat(consultar(ativo.getId(), params("situacao", "ABERTO")).totalElements()).isEqualTo(1);
        assertThat(consultar(ativo.getId(), params("dataFim", AGORA.toString())).content()).isEmpty();
        assertThat(consultar(ativo.getId(), params("dataInicio", AGORA.plusSeconds(3).toString())).content())
                .extracting(m -> m.evento().id()).containsExactly(limiteFinal);
        assertThat(consultar(ativo.getId(), params("responsavelUsuarioId", Long.toString(Long.MAX_VALUE))).content()).isEmpty();
    }

    @Test
    void devePaginarComOrdenacaoFixaDesempatePorIdENulosAoFinal() throws Exception {
        Long nulo = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Planejada");
        Long a = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "A"); transicionar(a, "ABERTO", "EM_ANDAMENTO", AGORA);
        Long b = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "B"); var hb = transicionar(b, "ABERTO", "EM_ANDAMENTO", AGORA);
        var p1 = consultar(ativo.getId(), params("size", "1"));
        assertThat(p1.totalElements()).isEqualTo(3); assertThat(p1.totalPages()).isEqualTo(3);
        assertThat(p1.first()).isTrue(); assertThat(p1.last()).isFalse();
        assertThat(p1.content().getFirst().evento().id()).isEqualTo(b);
        assertThat(p1.content().getFirst().ultimaTransicao().id()).isEqualTo(hb.id());
        assertThat(consultar(ativo.getId(), params("page", "1", "size", "1")).content().getFirst().evento().id()).isEqualTo(a);
        var ultima = consultar(ativo.getId(), params("page", "2", "size", "1"));
        assertThat(ultima.last()).isTrue(); assertThat(ultima.content().getFirst().evento().id()).isEqualTo(nulo);
        assertThat(ultima.content().getFirst().ultimaTransicao()).isNull();
        assertThat(consultar(ativo.getId(), params("size", "100")).content()).hasSize(3);
        var distante = consultar(ativo.getId(), params("page", "2147483647", "size", "100"));
        assertThat(distante.content()).isEmpty(); assertThat(distante.totalElements()).isEqualTo(3);
    }

    @Test
    void deveUsarQuatroConsultasIncluindoValidacaoDoAtivoSemNMaisUmNoDto() throws Exception {
        for (int i = 0; i < 4; i++) {
            Long id = criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Manutenção " + i);
            transicionar(id, "ABERTO", "EM_ANDAMENTO", AGORA.plusSeconds(i));
            transicionar(id, "EM_ANDAMENTO", "CONCLUIDO", AGORA.plusSeconds(i + 60));
        }
        em.flush(); em.clear();
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); boolean habilitado = stats.isStatisticsEnabled(); stats.setStatisticsEnabled(true);
        try {
            long antes = stats.getPrepareStatementCount();
            var pagina = consultar(ativo.getId(), params("size", "3"));
            assertThat(pagina.totalElements()).isEqualTo(4); assertThat(pagina.content()).hasSize(3);
            assertThat(pagina.content()).allSatisfy(m -> assertThat(m.ultimaTransicao().situacaoNova()).isEqualTo(SituacaoEventoOperacional.CONCLUIDO));
            assertThat(stats.getPrepareStatementCount() - antes).isEqualTo(4);
            antes = stats.getPrepareStatementCount();
            assertThat(consultar(outro.getId(), params()).content()).isEmpty();
            assertThat(stats.getPrepareStatementCount() - antes).isEqualTo(2);
        } finally { stats.setStatisticsEnabled(habilitado); }
    }

    @Test
    void deveRetornar404ParaAtivoInexistenteE401SemSessao() throws Exception {
        mockMvc.perform(get(base(Long.MAX_VALUE)).session(sessao)).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get(base(ativo.getId()))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirLeituraParaTodosOsPerfisAutenticados(PerfilAcesso perfil) throws Exception {
        criar(ativo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Planejada"); autenticar(perfil);
        assertThat(consultar(ativo.getId(), params()).totalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "page=abc", "page=2147483648", "size=0", "size=101", "size=1.5", "sort=inicioEfetivo,asc",
            "situacao=RESOLVIDO", "situacao=concluido", "responsavelUsuarioId=0", "responsavelUsuarioId=-1", "responsavelUsuarioId=abc",
            "responsavelUsuarioId=9223372036854775808", "dataInicio=invalido", "dataFim=2026-11-01T12:00:00", "categoria=MANOBRA", "ativoId=1"})
    void deveRejeitarFiltrosPaginacaoEParametrosInvalidos(String parametro) throws Exception {
        String[] partes = parametro.split("=", 2);
        mockMvc.perform(get(base(ativo.getId())).session(sessao).param(partes[0], partes[1]))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "size", "sort", "situacao", "responsavelUsuarioId", "dataInicio", "dataFim"})
    void deveRejeitarFiltrosVaziosOuRepetidos(String campo) throws Exception {
        mockMvc.perform(get(base(ativo.getId())).session(sessao).param(campo, "")).andExpect(status().isBadRequest());
        mockMvc.perform(get(base(ativo.getId())).session(sessao).param(campo, "1", "2")).andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarPeriodoIgualOuInvertido() throws Exception {
        for (String fim : java.util.List.of(AGORA.toString(), AGORA.minusSeconds(1).toString())) {
            mockMvc.perform(get(base(ativo.getId())).session(sessao).param("dataInicio", AGORA.toString()).param("dataFim", fim))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void deveRejeitarIdentificadorDeAtivoInvalido(String id) throws Exception {
        mockMvc.perform(get("/api/ativos-rede/" + id + "/manutencoes").session(sessao)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALIMENTADOR", "TRANSFORMADOR", "LINHA", "MEDIDOR"})
    void deveConsultarAtivosDosDemaisSubtipos(String tipo) throws Exception {
        AtivoRede alvo;
        if (tipo.equals("LINHA")) {
            var linha = new LinhaTransmissao(); linha.setNome("Linha"); linha.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            linha.setSubestacaoOrigem(ativo); linha.setSubestacaoDestino(outro); em.persist(linha); alvo = linha;
        } else if (tipo.equals("MEDIDOR")) {
            var medidor = new MedidorInteligente(); medidor.setNome("Medidor"); medidor.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            medidor.setNumeroSerie("MANUTENCAO-SERIE"); em.persist(medidor); alvo = medidor;
        } else {
            var alimentador = new Alimentador(); alimentador.setNome("Alimentador"); alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
            alimentador.setSubestacao(ativo); em.persist(alimentador); alvo = alimentador;
            if (tipo.equals("TRANSFORMADOR")) {
                var trafo = new Transformador(); trafo.setNome("Transformador"); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
                trafo.setAlimentador(alimentador); trafo.setCapacidadeMva(BigDecimal.TEN); em.persist(trafo); alvo = trafo;
            }
        }
        em.flush();
        Long id = criar(alvo.getId(), PerfilAcesso.OPERADOR, "MANUTENCAO", "Intervenção");
        assertThat(consultar(alvo.getId(), params()).content()).extracting(m -> m.evento().id()).containsExactly(id);
    }

    private Long criar(Long alvo, PerfilAcesso responsavel, String categoria, String descricao) {
        var evento = new EventoOperacional(); evento.setAtivo(em.getReference(AtivoRede.class, alvo));
        evento.setCategoria(EventoOperacional.Categoria.valueOf(categoria)); evento.setDescricao(descricao); evento.setCriticidade("ALTA");
        evento.setRegistradorUsuario(usuario(PerfilAcesso.GERENTE_OPERACOES)); evento.setResponsavelUsuario(usuario(responsavel));
        evento.setInicioPrevisto(java.time.OffsetDateTime.parse("2026-01-01T07:00:00-03:00"));
        evento.setTerminoPrevisto(java.time.OffsetDateTime.parse("2026-01-01T08:00:00-03:00")); evento.setSituacao("ABERTO");
        em.persist(evento); em.flush(); return evento.getId();
    }

    private HistoricoEventoResponseDTO transicionar(Long eventoId, String esperada, String destino, Instant instante) throws Exception {
        when(clock.instant()).thenReturn(instante);
        var body = Map.of("situacaoEsperada", esperada, "situacaoDestino", destino, "comentario", "Procedimento " + destino);
        var result = mockMvc.perform(post("/api/eventos-operacionais/{id}/transicoes", eventoId).session(sessao)
                .header(csrf.headerName(), csrf.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), HistoricoEventoResponseDTO.class);
    }

    private PageResponseDTO<ManutencaoHistoricoResponseDTO> consultar(Long alvo, MultiValueMap<String, String> params) throws Exception {
        var result = mockMvc.perform(get(base(alvo)).session(sessao).params(params)).andExpect(status().isOk()).andReturn();
        var tipo = objectMapper.getTypeFactory().constructParametricType(PageResponseDTO.class, ManutencaoHistoricoResponseDTO.class);
        return objectMapper.readValue(result.getResponse().getContentAsString(), tipo);
    }

    private MultiValueMap<String, String> params(String... pares) {
        var params = new LinkedMultiValueMap<String, String>(); for (int i = 0; i < pares.length; i += 2) params.add(pares[i], pares[i + 1]); return params;
    }

    private String base(Long alvo) { return "/api/ativos-rede/" + alvo + "/manutencoes"; }
    private Usuario usuario(PerfilAcesso perfil) { return EventosTransicaoFixture.usuario(em, perfil); }
    private Subestacao subestacao(String nome) {
        var sub = new Subestacao(); sub.setNome(nome); sub.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(sub); em.flush(); return sub;
    }

    private void autenticar(PerfilAcesso perfil) throws Exception {
        var inicial = mockMvc.perform(get("/api/auth/csrf")).andReturn(); sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        var token = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao).header(token.headerName(), token.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("email", usuario(perfil).getEmail()).param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
        var renovado = mockMvc.perform(get("/api/auth/csrf").session(sessao)).andReturn();
        csrf = objectMapper.readValue(renovado.getResponse().getContentAsString(), CsrfResponse.class);
    }
}
