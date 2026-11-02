package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.util.Map;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.exception.LimiteFeaturesMapaException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class MapaGeograficoControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private EntityManager em;
    private MockHttpSession sessao;
    private CsrfResponse csrf;

    @BeforeEach
    void preparar() throws Exception { autenticar(PerfilAcesso.OPERADOR); }

    @Test
    void deveRetornarGeoJsonPontualExatoComLongitudeAntesDaLatitude() throws Exception {
        var sub = sub("5", "6"); em.flush(); em.clear();
        mvc.perform(consulta()).andExpect(status().isOk()).andExpect(content().contentType("application/geo+json"))
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(1)).andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].id").value(sub.getId().toString()))
                .andExpect(jsonPath("$.features[0].length()").value(4)).andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(6))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(5))
                .andExpect(jsonPath("$.features[0].properties.length()").value(5))
                .andExpect(jsonPath("$.features[0].properties.ativoId").value(sub.getId()))
                .andExpect(jsonPath("$.features[0].properties.tipoAtivo").value("SUBESTACAO"))
                .andExpect(jsonPath("$.features[0].properties.nome").value("Ativo do mapa"))
                .andExpect(jsonPath("$.features[0].properties.estadoOperacional").value("OPERACIONAL"))
                .andExpect(jsonPath("$.features[0].properties.detalheUri").value("/api/subestacoes/" + sub.getId()))
                .andExpect(jsonPath("$.metadados.totalFeatures").value(1)).andExpect(jsonPath("$.metadados.totalAtivosSemGeometria").value(0));
    }

    @Test
    void deveRetornarConexaoSimplificadaMesmoComAsExtremidadesFora() throws Exception {
        var origem = sub("5", "-5"); var destino = sub("5", "15"); var linha = linha(origem, destino); em.flush(); em.clear();
        mvc.perform(consulta().param("tipos", "LINHA")).andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1)).andExpect(jsonPath("$.features[0].geometry.type").value("LineString"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][0]").value(-5))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][1]").value(5))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1][0]").value(15))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1][1]").value(5))
                .andExpect(jsonPath("$.features[0].properties.length()").value(8))
                .andExpect(jsonPath("$.features[0].properties.subestacaoOrigemId").value(origem.getId()))
                .andExpect(jsonPath("$.features[0].properties.subestacaoDestinoId").value(destino.getId()))
                .andExpect(jsonPath("$.features[0].properties.tipoRepresentacao").value("CONEXAO_SIMPLIFICADA"))
                .andExpect(jsonPath("$.features[0].properties.detalheUri").value("/api/linhas-transmissao/" + linha.getId()));
    }

    @Test
    void deveFiltrarBboxTiposEEstadosEOrdenarGlobalmentePorId() throws Exception {
        var sub = sub("1", "1");
        var usina = new UsinaGeradora(); usina.setSubestacao(sub); usina.setTipoGeracao(TipoGeracao.SOLAR); usina.setCapacidadeMaximaMw(n("10"));
        coordenadas(usina, "2", "3"); usina.setEstadoOperacional(EstadoOperacional.FALHA); em.persist(usina);
        var a = new Alimentador(); a.setSubestacao(sub); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.persist(a);
        var t = new Transformador(); t.setAlimentador(a); t.setCapacidadeMva(n("5")); coordenadas(t, "4", "4"); t.setEstadoOperacional(EstadoOperacional.MANUTENCAO); em.persist(t);
        var fora = sub("11", "11"); fora.setEstadoOperacional(EstadoOperacional.FALHA); em.flush();
        mvc.perform(consulta().param("tipos", "USINA, TRANSFORMADOR").param("estados", "FALHA,MANUTENCAO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(2))
                .andExpect(jsonPath("$.features[0].properties.ativoId").value(usina.getId()))
                .andExpect(jsonPath("$.features[0].properties.detalheUri").value("/api/usinas/" + usina.getId()))
                .andExpect(jsonPath("$.features[1].properties.ativoId").value(t.getId()))
                .andExpect(jsonPath("$.features[1].properties.detalheUri").value("/api/transformadores/" + t.getId()));
        mvc.perform(consulta().param("tipos", "SUBESTACAO").param("estados", "FALHA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features").isEmpty());
    }

    @Test
    void deveOrdenarPontosELinhasJuntosSemDependerDaOrdemDasCamadas() throws Exception {
        var linha = linha(sub("5", "-5"), sub("5", "15")); var ponto = sub("5", "5"); em.flush();
        mvc.perform(consulta()).andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(2))
                .andExpect(jsonPath("$.features[0].properties.ativoId").value(linha.getId()))
                .andExpect(jsonPath("$.features[1].properties.ativoId").value(ponto.getId()));
    }

    @Test
    void deveContarAusenciaDeGeometriaPorCamadaEEstadoSemAplicarBbox() throws Exception {
        sub(null, "20"); var falha = sub("20", null); falha.setEstadoOperacional(EstadoOperacional.FALHA);
        sub("20", "20"); sub("5", "5");
        var l = linha(sub(null, "5"), sub("5", "15"));
        var coincidente = linha(sub("20", "20"), sub("20", "20")); coincidente.setEstadoOperacional(EstadoOperacional.FALHA); em.flush();
        mvc.perform(consulta().param("tipos", "SUBESTACAO").param("estados", "FALHA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metadados.totalAtivosSemGeometria").value(1));
        mvc.perform(consulta().param("tipos", "LINHA").param("estados", "OPERACIONAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metadados.totalAtivosSemGeometria").value(1));
        mvc.perform(consulta().param("tipos", "LINHA").param("estados", "FALHA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.metadados.totalAtivosSemGeometria").value(1));
    }

    @Test
    void deveResponderColecaoVaziaSemInventarGeometria() throws Exception {
        sub(null, null); em.flush();
        mvc.perform(consulta()).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isEmpty()).andExpect(jsonPath("$.metadados.totalFeatures").value(0))
                .andExpect(jsonPath("$.metadados.totalAtivosSemGeometria").value(1));
    }

    @Test
    void deveRetornar422PadronizadoSemMapaParcialAoExcederLimiteCombinado() throws Exception {
        sub("5", "5"); linha(sub("5", "-5"), sub("5", "15")); em.flush();
        var resultado = mvc.perform(consulta().param("limite", "1")).andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(422)).andExpect(jsonPath("$.path").value("/api/mapa/ativos"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Reduza")))
                .andExpect(jsonPath("$.features").doesNotExist()).andReturn();
        assertThat(resultado.getResolvedException()).isInstanceOf(LimiteFeaturesMapaException.class);
        mvc.perform(consulta().param("limite", "2")).andExpect(status().isOk()).andExpect(jsonPath("$.metadados.totalFeatures").value(2));
    }

    @Test
    void deveAplicarLimitePadraoDeMilSemTruncarResultado() throws Exception {
        for (int i = 0; i < 1001; i++) sub("5", "5"); em.flush(); em.clear();
        mvc.perform(consulta().param("tipos", "SUBESTACAO")).andExpect(status().isUnprocessableEntity());
        mvc.perform(consulta().param("tipos", "SUBESTACAO").param("limite", "5000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(1001));
    }

    @Test
    void naoDeveContarFalsosPositivosNemPararAntesDePercorrerTodasAsCandidatas() throws Exception {
        var a = sub("-5", "9"); var b = sub("9", "20");
        for (int i = 0; i < 205; i++) linha(a, b);
        var verdadeira = linha(sub("5", "-5"), sub("5", "15")); em.flush(); em.clear();
        mvc.perform(consulta().param("tipos", "LINHA").param("limite", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].properties.ativoId").value(verdadeira.getId()));
    }

    @ParameterizedTest
    @EnumSource(PerfilAcesso.class)
    void devePermitirConsultaAosTresPerfisComSessao(PerfilAcesso perfil) throws Exception {
        autenticar(perfil); mvc.perform(consulta()).andExpect(status().isOk());
    }

    @Test
    void deveBloquearConsultaSemSessao() throws Exception {
        mvc.perform(get("/api/mapa/ativos").param("minLat", "0").param("maxLat", "10").param("minLng", "0").param("maxLng", "10"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minLat", "maxLat", "minLng", "maxLng"})
    void deveExigirOsQuatroLimites(String ausente) throws Exception {
        var request = get("/api/mapa/ativos").session(sessao);
        for (var limite : Map.of("minLat", "0", "maxLat", "10", "minLng", "0", "maxLng", "10").entrySet()) {
            if (!limite.getKey().equals(ausente)) request.param(limite.getKey(), limite.getValue());
        }
        mvc.perform(request).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"minLat", "maxLat", "minLng", "maxLng", "tipos", "estados", "limite"})
    void deveRejeitarParametrosVaziosOuRepetidos(String parametro) throws Exception {
        var vazio = parametros(); vazio.put(parametro, ""); mvc.perform(consulta(vazio)).andExpect(status().isBadRequest());
        mvc.perform(consulta().param(parametro, "1", "2")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"minLat,-91", "maxLat,91", "minLng,-181", "maxLng,181", "minLat,10", "maxLat,-1", "minLng,10", "maxLng,-1",
            "minLat,NaN", "minLng,Infinity", "maxLat,null", "maxLng,abc", "limite,0", "limite,5001", "limite,1.5", "limite,abc",
            "limite,2147483648", "tipos,MEDIDOR", "tipos,subestacao", "estados,PARADA", "estados,operacional", "sort,id"})
    void deveRejeitarValoresInvalidos(String parametro, String valor) throws Exception {
        var params = parametros(); params.put(parametro, valor); mvc.perform(consulta(params)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {",", "SUBESTACAO,", ",LINHA", "SUBESTACAO,,LINHA"})
    void deveRejeitarItensVaziosDaLista(String valor) throws Exception {
        mvc.perform(consulta().param("tipos", valor)).andExpect(status().isBadRequest());
    }

    @Test
    void deveAceitarDuplicatasNaListaComoMesmoFiltroERecusarAntimeridiano() throws Exception {
        sub("5", "5"); em.flush();
        mvc.perform(consulta().param("tipos", " SUBESTACAO , SUBESTACAO "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.features.length()").value(1));
        var params = parametros(); params.put("minLng", "170"); params.put("maxLng", "-170");
        mvc.perform(consulta(params)).andExpect(status().isBadRequest());
    }

    private MockHttpServletRequestBuilder consulta() { return consulta(parametros()); }
    private MockHttpServletRequestBuilder consulta(Map<String, String> params) {
        var request = get("/api/mapa/ativos").session(sessao); params.forEach(request::param); return request;
    }
    private Map<String, String> parametros() {
        return new java.util.HashMap<>(Map.of("minLat", "0", "maxLat", "10", "minLng", "0", "maxLng", "10"));
    }
    private Subestacao sub(String lat, String lng) { var s = new Subestacao(); coordenadas(s, lat, lng); em.persist(s); return s; }
    private void coordenadas(AtivoRede a, String lat, String lng) {
        a.setNome("Ativo do mapa"); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        a.setLatitude(lat == null ? null : n(lat)); a.setLongitude(lng == null ? null : n(lng));
    }
    private LinhaTransmissao linha(Subestacao origem, Subestacao destino) {
        var l = new LinhaTransmissao(); l.setNome("Linha do mapa"); l.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        l.setSubestacaoOrigem(origem); l.setSubestacaoDestino(destino); em.persist(l); return l;
    }
    private BigDecimal n(String valor) { return new BigDecimal(valor); }

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
