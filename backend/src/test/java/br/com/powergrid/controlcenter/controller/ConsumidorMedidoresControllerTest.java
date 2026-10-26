package br.com.powergrid.controlcenter.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import br.com.powergrid.controlcenter.dto.CsrfResponse;
import br.com.powergrid.controlcenter.dto.MedidorResponseDTO;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Consumidor;
import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;
import br.com.powergrid.controlcenter.model.MedidorInteligente;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.model.Transformador;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class ConsumidorMedidoresControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;
    private MockHttpSession sessao;

    @BeforeEach
    void autenticar() throws Exception {
        MvcResult inicial = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        sessao = (MockHttpSession) inicial.getRequest().getSession(false);
        CsrfResponse csrf = objectMapper.readValue(inicial.getResponse().getContentAsString(), CsrfResponse.class);
        mockMvc.perform(post("/api/auth/login").session(sessao)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header(csrf.headerName(), csrf.token())
                        .param("email", "operador@powergrid.local").param("password", "SenhaSegura123"))
                .andExpect(status().isOk());
    }

    @Test
    void deveListarEmOrdemDeIdComTopologiaEIsolamento() throws Exception {
        Consumidor titular = consumidor("52998224725");
        Consumidor outro = consumidor("01234567890");
        Transformador primeiroTransformador = transformador();
        Transformador segundoTransformador = transformador();
        MedidorInteligente primeiro = medidor("Z-003", titular, primeiroTransformador, EstadoOperacional.FALHA);
        MedidorInteligente alheio = medidor("OUTRO", outro, primeiroTransformador, EstadoOperacional.OPERACIONAL);
        MedidorInteligente segundo = medidor("A-001", titular, segundoTransformador, EstadoOperacional.MANUTENCAO);
        MedidorInteligente estoque = medidor("ESTOQUE", null, null, EstadoOperacional.OPERACIONAL);
        MedidorInteligente terceiro = medidor("M-002", titular, primeiroTransformador, EstadoOperacional.OPERACIONAL);
        Long alimentadorId = primeiroTransformador.getAlimentador().getId();
        Long subestacaoId = primeiroTransformador.getAlimentador().getSubestacao().getId();
        Long segundoAlimentadorId = segundoTransformador.getAlimentador().getId();
        Long segundaSubestacaoId = segundoTransformador.getAlimentador().getSubestacao().getId();
        entityManager.flush();
        entityManager.clear();

        MedidorResponseDTO[] lista = consultar(titular.getId());
        assertThat(lista).extracting(MedidorResponseDTO::id)
                .containsExactly(primeiro.getId(), segundo.getId(), terceiro.getId())
                .doesNotContain(alheio.getId(), estoque.getId());
        assertThat(lista).extracting(MedidorResponseDTO::consumidorId).containsOnly(titular.getId());
        assertThat(lista).extracting(MedidorResponseDTO::estadoOperacional)
                .containsExactly(EstadoOperacional.FALHA, EstadoOperacional.MANUTENCAO, EstadoOperacional.OPERACIONAL);
        assertThat(lista[0].transformadorId()).isEqualTo(primeiroTransformador.getId());
        assertThat(lista[0].alimentadorId()).isEqualTo(alimentadorId);
        assertThat(lista[0].subestacaoId()).isEqualTo(subestacaoId);
        assertThat(lista[1].transformadorId()).isEqualTo(segundoTransformador.getId());
        assertThat(lista[1].alimentadorId()).isEqualTo(segundoAlimentadorId);
        assertThat(lista[1].subestacaoId()).isEqualTo(segundaSubestacaoId);
        assertThat(lista[0].nome()).isEqualTo(primeiro.getNome());
        assertThat(lista[0].numeroSerie()).isEqualTo("Z-003");
        assertThat(lista[0].latitude()).isEqualByComparingTo(primeiro.getLatitude());
        assertThat(lista[0].longitude()).isEqualByComparingTo(primeiro.getLongitude());
        assertThat(lista[0].dataInstalacao()).isEqualTo(primeiro.getDataInstalacao());
        assertThat(lista[0].enderecoFornecimento()).isEqualTo(primeiro.getEnderecoFornecimento());
        assertThat(consultar(outro.getId())).extracting(MedidorResponseDTO::id).containsExactly(alheio.getId());
    }

    @Test
    void devePreservarMedidorSemTransformadorNaLista() throws Exception {
        Consumidor titular = consumidor("52998224725");
        MedidorInteligente medidor = medidor("SEM-TRANSFORMADOR", titular, null, EstadoOperacional.OPERACIONAL);
        entityManager.flush();
        entityManager.clear();
        MedidorResponseDTO[] lista = consultar(titular.getId());
        assertThat(lista).hasSize(1);
        assertThat(lista[0].id()).isEqualTo(medidor.getId());
        assertThat(lista[0].consumidorId()).isEqualTo(titular.getId());
        assertThat(lista[0].transformadorId()).isNull();
        assertThat(lista[0].alimentadorId()).isNull();
        assertThat(lista[0].subestacaoId()).isNull();
    }

    @Test
    void deveRetornarListaVaziaParaTitularSemMedidores() throws Exception {
        Consumidor titular = consumidor("52998224725");
        medidor("ESTOQUE", null, null, EstadoOperacional.OPERACIONAL);
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(get("/api/consumidores/{id}/medidores", titular.getId()).session(sessao))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void deveRetornar404ParaTitularInexistente() throws Exception {
        mockMvc.perform(get("/api/consumidores/{id}/medidores", Long.MAX_VALUE).session(sessao))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "9223372036854775808"})
    void deveRetornar400ParaIdInvalido(String id) throws Exception {
        mockMvc.perform(get("/api/consumidores/{id}/medidores", id).session(sessao))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveExigirSessaoAutenticada() throws Exception {
        mockMvc.perform(get("/api/consumidores/1/medidores")).andExpect(status().isUnauthorized());
    }

    private MedidorResponseDTO[] consultar(Long id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/consumidores/{id}/medidores", id).session(sessao))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), MedidorResponseDTO[].class);
    }

    private Consumidor consumidor(String documento) {
        Consumidor consumidor = new Consumidor();
        consumidor.setNomeRazaoSocial("Titular");
        consumidor.setTipoPessoa(TipoPessoa.PF);
        consumidor.setDocumento(documento);
        entityManager.persist(consumidor);
        return consumidor;
    }

    private MedidorInteligente medidor(String serie, Consumidor consumidor,
            Transformador transformador, EstadoOperacional estado) {
        MedidorInteligente medidor = new MedidorInteligente();
        medidor.setNome("Medidor " + serie);
        medidor.setNumeroSerie(serie);
        medidor.setEstadoOperacional(estado);
        medidor.setConsumidor(consumidor);
        medidor.setTransformador(transformador);
        if (transformador != null) {
            medidor.setLatitude(new BigDecimal("-25.4284001"));
            medidor.setLongitude(new BigDecimal("-49.2733001"));
            medidor.setDataInstalacao(LocalDate.of(2026, 1, 1));
            medidor.setEnderecoFornecimento("Rua das Flores, 100");
        }
        entityManager.persist(medidor);
        return medidor;
    }

    private Transformador transformador() {
        Subestacao subestacao = new Subestacao();
        subestacao.setNome("Subestação");
        subestacao.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        subestacao.setCapacidadeMva(new BigDecimal("100"));
        subestacao.setTensaoNominalKv(new BigDecimal("138"));
        entityManager.persist(subestacao);
        Alimentador alimentador = new Alimentador();
        alimentador.setNome("Alimentador");
        alimentador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        alimentador.setTensaoNominalKv(new BigDecimal("13.8"));
        alimentador.setSubestacao(subestacao);
        entityManager.persist(alimentador);
        Transformador transformador = new Transformador();
        transformador.setNome("Transformador");
        transformador.setEstadoOperacional(EstadoOperacional.OPERACIONAL);
        transformador.setCapacidadeMva(new BigDecimal("5"));
        transformador.setAlimentador(alimentador);
        entityManager.persist(transformador);
        return transformador;
    }
}
