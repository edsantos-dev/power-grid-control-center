package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.FalhaEquipamentoRepository;
import br.com.powergrid.controlcenter.support.EventosTransicaoFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class FalhaEquipamentoServiceTest {
    private static final Instant AGORA = Instant.parse("2026-11-04T12:00:00.123456Z");
    @Autowired private FalhaEquipamentoService falhas;
    @Autowired private EventoOperacionalService eventos;
    @Autowired private SubestacaoService subestacoes;
    @Autowired private AlimentadorService alimentadores;
    @Autowired private TransformadorService transformadores;
    @Autowired private LinhaTransmissaoService linhas;
    @Autowired private UsinaGeradoraService usinas;
    @Autowired private MedidorInteligenteService medidores;
    @Autowired private FonteMedicaoService fontes;
    @Autowired private MedicaoTransformadorService medicoes;
    @Autowired private FalhaEquipamentoRepository repository;
    @Autowired private EntityManager em;
    @MockitoBean private Clock clock;
    private Subestacao sub, outra, terceira;
    private Alimentador al, outroAl;
    private Transformador trafo;
    private LinhaTransmissao linha;
    private UsinaGeradora usina;
    private MedidorInteligente medidor;
    private Consumidor consumidor;
    private Usuario gerente, operador;

    @BeforeEach
    void preparar() {
        when(clock.instant()).thenReturn(AGORA); when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        gerente = EventosTransicaoFixture.usuario(em, PerfilAcesso.GERENTE_OPERACOES);
        operador = EventosTransicaoFixture.usuario(em, PerfilAcesso.OPERADOR);
        EventosTransicaoFixture.autenticar(gerente.getEmail());
        sub = sub("Subestação"); outra = sub("Outra subestação"); terceira = sub("Terceira subestação");
        al = al(sub); outroAl = al(outra);
        trafo = new Transformador(); base(trafo); trafo.setAlimentador(al); trafo.setCapacidadeMva(BigDecimal.TEN); em.persist(trafo);
        linha = new LinhaTransmissao(); base(linha); linha.setSubestacaoOrigem(sub); linha.setSubestacaoDestino(outra); em.persist(linha);
        usina = new UsinaGeradora(); base(usina); usina.setSubestacao(sub); usina.setTipoGeracao(TipoGeracao.SOLAR); usina.setCapacidadeMaximaMw(BigDecimal.TEN); em.persist(usina);
        medidor = new MedidorInteligente(); base(medidor); medidor.setNumeroSerie("FALHA-TESTE"); em.persist(medidor);
        consumidor = new Consumidor(); consumidor.setNomeRazaoSocial("Titular"); consumidor.setTipoPessoa(Consumidor.TipoPessoa.PF); consumidor.setDocumento("12345678901"); em.persist(consumidor);
        em.flush();
    }

    @AfterEach
    void limparSessao() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA", "MEDIDOR"})
    void deveRegistrarFalhaAtomicamenteEmQualquerEspecializacao(String tipo) {
        var ativo = ativo(tipo);
        var salvo = falhas.registrar(ativo.getId(), operador.getId(), "  Defeito observado  ", Criticidade.ALTA);
        Long id = salvo.getId(); em.clear();
        var recuperado = repository.findById(id).orElseThrow();
        assertThat(recuperado.getEstadoAnterior()).isEqualTo(EstadoOperacional.OPERACIONAL);
        assertThat(recuperado.getEstadoAposReparo()).isNull();
        assertThat(recuperado.getRegistradoEm().toInstant()).isEqualTo(AGORA);
        var evento = recuperado.getEventoOperacional();
        assertThat(evento.getId()).isEqualTo(id);
        assertThat(evento.getCategoria()).isEqualTo(EventoOperacional.Categoria.FALHA_EQUIPAMENTO);
        assertThat(evento.getSituacao()).isEqualTo("ABERTO");
        assertThat(evento.getDescricao()).isEqualTo("Defeito observado");
        assertThat(evento.getRegistradorUsuario().getId()).isEqualTo(gerente.getId());
        assertThat(evento.getResponsavelUsuario().getId()).isEqualTo(operador.getId());
        assertThat(evento.getAtivo().getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
        assertThat(evento.getInicioEfetivo()).isNull();
    }

    @ParameterizedTest
    @EnumSource(EstadoOperacional.class)
    void devePreservarEstadoAnteriorInclusiveRegularizacaoLegada(EstadoOperacional estado) {
        trafo.setEstadoOperacional(estado); em.flush();
        assertThat(registrar(trafo).getEstadoAnterior()).isEqualTo(estado);
    }

    @Test
    void deveImpedirFalhaDuplicadaSemCriarOutroEvento() {
        registrar(trafo);
        erro(409, () -> registrar(trafo));
        assertThat(em.createQuery("select count(e) from EventoOperacional e", Long.class).getSingleResult()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERACIONAL", "MANUTENCAO"})
    void deveConcluirReparoEPermitirNovoEpisodio(String destino) {
        var falha = registrar(trafo); Long id = falha.getId();
        eventos.transicionar(id, SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Atendimento");
        when(clock.instant()).thenReturn(AGORA.plusSeconds(60));
        Long historico = eventos.transicionar(id, SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO,
                "Reparo concluído", null, EstadoOperacional.valueOf(destino));
        em.flush(); em.clear();
        var salvo = repository.findById(id).orElseThrow();
        assertThat(salvo.getEstadoAposReparo()).isEqualTo(EstadoOperacional.valueOf(destino));
        assertThat(salvo.getEventoOperacional().getAtivo().getEstadoOperacional()).isEqualTo(EstadoOperacional.valueOf(destino));
        assertThat(salvo.getEventoOperacional().getSituacao()).isEqualTo("CONCLUIDO");
        assertThat(salvo.getEventoOperacional().getTerminoEfetivo().toInstant()).isEqualTo(AGORA.plusSeconds(60));
        assertThat(em.find(HistoricoEventoOperacional.class, historico).getUsuario().getId()).isEqualTo(gerente.getId());
        assertThat(registrar(em.find(Transformador.class, trafo.getId())).getEstadoAnterior()).isEqualTo(EstadoOperacional.valueOf(destino));
    }

    @Test
    void deveExigirEstadoValidoParaConcluirReparo() {
        var f = registrar(trafo);
        eventos.transicionar(f.getId(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Iniciado");
        erro(400, () -> eventos.transicionar(f.getId(), SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO, "Fim"));
        erro(400, () -> eventos.transicionar(f.getId(), SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO,
                "Fim", null, EstadoOperacional.FALHA));
        em.clear();
        assertThat(repository.findById(f.getId()).orElseThrow().getEstadoAposReparo()).isNull();
        assertThat(em.find(AtivoRede.class, trafo.getId()).getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @Test
    void naoDeveAceitarEstadoDeReparoEmOutraCategoria() {
        var e = eventos.criar(request(EventoOperacional.Categoria.MANUTENCAO));
        eventos.transicionar(e.id(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Início");
        erro(400, () -> eventos.transicionar(e.id(), SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO,
                "Fim", null, EstadoOperacional.OPERACIONAL));
    }

    @Test
    void deveBloquearCadastroEInicioDeManobraMasPermitirEncerramentoAdministrativo() {
        var planejada = eventos.criar(request(EventoOperacional.Categoria.MANOBRA));
        var iniciada = eventos.criar(request(EventoOperacional.Categoria.MANOBRA));
        eventos.transicionar(iniciada.id(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Início");
        registrar(trafo);
        erro(409, () -> eventos.criar(request(EventoOperacional.Categoria.MANOBRA)));
        erro(409, () -> eventos.transicionar(planejada.id(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Início"));
        eventos.transicionar(iniciada.id(), SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO, "Encerramento administrativo");
        assertThat(trafo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @Test
    void deveManterManutencaoETelemetriaSemLiberarFalha() {
        var fonte = fontes.criarParaTransformador(trafo.getId(), new FonteMedicaoRequestDTO("FONTE-FALHA", OrigemMedicao.SIMULADA, null));
        registrar(trafo);
        var e = eventos.criar(request(EventoOperacional.Categoria.MANUTENCAO));
        eventos.transicionar(e.id(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Início");
        eventos.transicionar(e.id(), SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO, "Fim");
        medicoes.ingerir(trafo.getId(), new MedicaoTransformadorRequestDTO(fonte.id(), AGORA.atOffset(ZoneOffset.UTC), BigDecimal.ONE));
        assertThat(trafo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA", "MEDIDOR"})
    void deveRejeitarCriacaoDiretaEmFalha(String tipo) { erro(409, () -> criar(tipo, EstadoOperacional.FALHA)); }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA"})
    void deveBloquearLimpezaDeEstadoPorPutInclusiveQuandoOmitido(String tipo) {
        var ativo = ativo(tipo); registrar(ativo);
        for (var estado : new EstadoOperacional[] {null, EstadoOperacional.OPERACIONAL, EstadoOperacional.MANUTENCAO})
            erro(409, () -> atualizar(tipo, estado, false));
        assertThat(ativo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBESTACAO", "ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA"})
    void devePermitirMetadadosSemAlterarFalhaEImpedirAtribuicaoDireta(String tipo) {
        erro(409, () -> atualizar(tipo, EstadoOperacional.FALHA, false));
        registrar(ativo(tipo));
        atualizar(tipo, EstadoOperacional.FALHA, false);
        assertThat(ativo(tipo).getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA"})
    void deveImpedirMudancaTopologicaDoAtivoEmFalha(String tipo) {
        registrar(ativo(tipo)); erro(409, () -> atualizar(tipo, EstadoOperacional.FALHA, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA"})
    void deveImpedirAssociacaoANovoDestinoEmFalha(String tipo) {
        registrar(tipo.equals("TRANSFORMADOR") ? outroAl : tipo.equals("LINHA") ? terceira : outra);
        erro(409, () -> atualizar(tipo, EstadoOperacional.OPERACIONAL, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA"})
    void deveImpedirDesassociacaoDeOrigemEmFalha(String tipo) {
        registrar(tipo.equals("TRANSFORMADOR") ? al : sub);
        erro(409, () -> atualizar(tipo, EstadoOperacional.OPERACIONAL, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALIMENTADOR", "TRANSFORMADOR", "LINHA", "USINA"})
    void deveImpedirCadastroDeFilhoEmOrigemEmFalha(String tipo) {
        registrar(tipo.equals("TRANSFORMADOR") ? al : sub);
        erro(409, () -> criar(tipo, EstadoOperacional.OPERACIONAL));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deveBloquearInstalacaoSeMedidorOuTransformadorEstiverEmFalha(boolean noMedidor) {
        registrar(noMedidor ? medidor : trafo);
        erro(409, () -> medidores.instalar(medidor.getId(), new MedidorInstalacaoRequestDTO(consumidor.getId(), trafo.getId(),
                BigDecimal.ZERO, BigDecimal.ZERO, LocalDate.of(2026, 1, 1), null)));
        assertThat(medidor.getTransformador()).isNull(); assertThat(medidor.getConsumidor()).isNull();
    }

    @Test
    void deveRejeitarRegistroComReferenciasOuDadosInvalidos() {
        erro(404, () -> falhas.registrar(Long.MAX_VALUE, operador.getId(), "Defeito", null));
        erro(404, () -> falhas.registrar(trafo.getId(), Long.MAX_VALUE, "Defeito", null));
        erro(400, () -> falhas.registrar(trafo.getId(), operador.getId(), " ", null));
    }

    @Test
    void tecnicoNaoPodeRegistrarMasResponsavelPodeConcluir() {
        var tecnico = EventosTransicaoFixture.usuario(em, PerfilAcesso.TECNICO_MANUTENCAO);
        var falha = falhas.registrar(trafo.getId(), tecnico.getId(), "Falha", null);
        EventosTransicaoFixture.autenticar(tecnico.getEmail());
        assertThatThrownBy(() -> registrar(sub)).isInstanceOf(AccessDeniedException.class);
        eventos.transicionar(falha.getId(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Início");
        eventos.transicionar(falha.getId(), SituacaoEventoOperacional.EM_ANDAMENTO, SituacaoEventoOperacional.CONCLUIDO,
                "Fim", null, EstadoOperacional.MANUTENCAO);
        assertThat(trafo.getEstadoOperacional()).isEqualTo(EstadoOperacional.MANUTENCAO);
    }

    @Test
    void falhaLegadaSemComposicaoNaoDeveSerLiberadaNemDuplicada() {
        var e = new EventoOperacional(); e.setAtivo(trafo); e.setCategoria(EventoOperacional.Categoria.FALHA_EQUIPAMENTO);
        e.setSituacao("EM_ANDAMENTO"); e.setInicioEfetivo(AGORA.minusSeconds(60).atOffset(ZoneOffset.UTC));
        e.setRegistradorUsuario(gerente); e.setResponsavelUsuario(operador); em.persist(e);
        trafo.setEstadoOperacional(EstadoOperacional.FALHA); em.flush();
        erro(409, () -> registrar(trafo));
        erro(409, () -> eventos.transicionar(e.getId(), SituacaoEventoOperacional.EM_ANDAMENTO,
                SituacaoEventoOperacional.CONCLUIDO, "Regularização necessária", null, EstadoOperacional.OPERACIONAL));
        assertThat(trafo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @Test
    void outraFalhaLegadaAbertaDeveImpedirLiberacaoDoMesmoAtivo() {
        var atual = registrar(trafo);
        var outraFalha = new EventoOperacional(); outraFalha.setAtivo(trafo);
        outraFalha.setCategoria(EventoOperacional.Categoria.FALHA_EQUIPAMENTO); outraFalha.setSituacao("ABERTO");
        outraFalha.setRegistradorUsuario(gerente); outraFalha.setResponsavelUsuario(operador); em.persist(outraFalha); em.flush();
        eventos.transicionar(atual.getId(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Atendimento");
        erro(409, () -> eventos.transicionar(atual.getId(), SituacaoEventoOperacional.EM_ANDAMENTO,
                SituacaoEventoOperacional.CONCLUIDO, "Fim", null, EstadoOperacional.OPERACIONAL));
        assertThat(trafo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    @Test
    void falhaAbertaDeveBloquearMesmoSeEstadoCadastralEstiverInconsistente() {
        registrar(trafo); trafo.setEstadoOperacional(EstadoOperacional.OPERACIONAL); em.flush();
        erro(409, () -> atualizar("TRANSFORMADOR", EstadoOperacional.OPERACIONAL, false));
        erro(409, () -> eventos.criar(request(EventoOperacional.Categoria.MANOBRA)));
    }

    @Test
    void naoDeveSobrescreverEstadoDeReparoDeComposicaoInconsistente() {
        var falha = registrar(trafo);
        eventos.transicionar(falha.getId(), SituacaoEventoOperacional.ABERTO, SituacaoEventoOperacional.EM_ANDAMENTO, "Início");
        falha.setEstadoAposReparo(EstadoOperacional.MANUTENCAO); em.flush();
        erro(409, () -> eventos.transicionar(falha.getId(), SituacaoEventoOperacional.EM_ANDAMENTO,
                SituacaoEventoOperacional.CONCLUIDO, "Fim", null, EstadoOperacional.OPERACIONAL));
        assertThat(falha.getEstadoAposReparo()).isEqualTo(EstadoOperacional.MANUTENCAO);
        assertThat(trafo.getEstadoOperacional()).isEqualTo(EstadoOperacional.FALHA);
    }

    private FalhaEquipamento registrar(AtivoRede ativo) { return falhas.registrar(ativo.getId(), operador.getId(), "Defeito", null); }
    private EventoOperacionalRequestDTO request(EventoOperacional.Categoria categoria) {
        return new EventoOperacionalRequestDTO(trafo.getId(), categoria, "Atendimento", operador.getId(), AGORA.atOffset(ZoneOffset.UTC), null, null);
    }
    private AtivoRede ativo(String tipo) { return switch(tipo) {
        case "SUBESTACAO" -> sub; case "ALIMENTADOR" -> al; case "TRANSFORMADOR" -> trafo;
        case "LINHA" -> linha; case "USINA" -> usina; default -> medidor;
    }; }
    private void criar(String tipo, EstadoOperacional estado) { switch(tipo) {
        case "SUBESTACAO" -> subestacoes.criar(subReq(estado));
        case "ALIMENTADOR" -> alimentadores.criar(alReq(sub.getId(), estado));
        case "TRANSFORMADOR" -> transformadores.criar(trafoReq(al.getId(), estado));
        case "LINHA" -> linhas.criar(linhaReq(sub.getId(), estado));
        case "USINA" -> usinas.criar(usinaReq(sub.getId(), estado));
        default -> medidores.criar(new MedidorRequestDTO("Novo", "SERIE-NOVA", estado));
    } }
    private void atualizar(String tipo, EstadoOperacional estado, boolean mudar) { switch(tipo) {
        case "SUBESTACAO" -> subestacoes.atualizar(sub.getId(), subReq(estado));
        case "ALIMENTADOR" -> alimentadores.atualizar(al.getId(), alReq(mudar ? outra.getId() : sub.getId(), estado));
        case "TRANSFORMADOR" -> transformadores.atualizar(trafo.getId(), trafoReq(mudar ? outroAl.getId() : al.getId(), estado));
        case "LINHA" -> linhas.atualizar(linha.getId(), new LinhaTransmissaoRequestDTO("Editada", null, null, BigDecimal.TEN, sub.getId(), mudar ? terceira.getId() : outra.getId(), BigDecimal.TEN, LocalDate.of(2026,1,1), estado));
        case "USINA" -> usinas.atualizar(usina.getId(), usinaReq(mudar ? outra.getId() : sub.getId(), estado));
    } }
    private SubestacaoRequestDTO subReq(EstadoOperacional e) { return new SubestacaoRequestDTO("Editada", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN, LocalDate.of(2026,1,1), e); }
    private AlimentadorRequestDTO alReq(Long pai, EstadoOperacional e) { return new AlimentadorRequestDTO("Editado", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, pai, LocalDate.of(2026,1,1), e); }
    private TransformadorRequestDTO trafoReq(Long pai, EstadoOperacional e) { return new TransformadorRequestDTO("Editado", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, pai, LocalDate.of(2026,1,1), e); }
    private LinhaTransmissaoRequestDTO linhaReq(Long origem, EstadoOperacional e) { return new LinhaTransmissaoRequestDTO("Editada", null, null, BigDecimal.TEN, origem, outra.getId(), BigDecimal.TEN, LocalDate.of(2026,1,1), e); }
    private UsinaRequestDTO usinaReq(Long pai, EstadoOperacional e) { return new UsinaRequestDTO("Editada", BigDecimal.ZERO, BigDecimal.ZERO, TipoGeracao.SOLAR, BigDecimal.TEN, pai, null, e); }
    private void base(AtivoRede a) { a.setNome("Ativo"); a.setEstadoOperacional(EstadoOperacional.OPERACIONAL); }
    private Subestacao sub(String nome) { var s = new Subestacao(); base(s); s.setNome(nome); em.persist(s); return s; }
    private Alimentador al(Subestacao s) { var a = new Alimentador(); base(a); a.setSubestacao(s); em.persist(a); return a; }
    private void erro(int status, Runnable acao) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(status));
    }
}
