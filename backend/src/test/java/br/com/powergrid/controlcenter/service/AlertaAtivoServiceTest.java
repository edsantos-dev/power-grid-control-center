package br.com.powergrid.controlcenter.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.repository.AlertaAtivoRepository;
import br.com.powergrid.controlcenter.repository.FiltroAlertasAtivos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlertaAtivoServiceTest {
    private AlertaAtivoRepository repository;
    private AlertaAtivoService service;

    @BeforeEach
    void preparar() {
        repository = mock(AlertaAtivoRepository.class);
        service = new AlertaAtivoService(repository);
    }

    @Test
    void deveNormalizarInstantesUtcEDelegarPaginaSemOrdenacaoInjetada() {
        var inicio = OffsetDateTime.parse("2026-01-02T09:00:00-03:00");
        var fim = inicio.plusHours(1);
        var filtro = new FiltroAlertasAtivos(null, null, null, null, null, null, null, inicio, fim);
        when(repository.buscar(any(), any())).thenReturn(Page.empty(PageRequest.of(2, 10)));
        var resposta = service.listar(filtro, PageRequest.of(2, 10));
        var captor = ArgumentCaptor.forClass(FiltroAlertasAtivos.class);
        verify(repository).buscar(captor.capture(), eq(PageRequest.of(2, 10)));
        assertThat(captor.getValue().dataInicio().getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(captor.getValue().dataInicio().toInstant()).isEqualTo(inicio.toInstant());
        assertThat(captor.getValue().dataFim().toInstant()).isEqualTo(fim.toInstant());
        assertThat(resposta.content()).isEmpty();
        assertThat(resposta.page()).isEqualTo(2);
    }

    @Test
    void deveRejeitarSortTambemNaEntradaTipada() {
        rejeitar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(0, 20, Sort.by("ocorridoEm")));
    }

    @Test
    void deveRejeitarAusenciaDePaginacaoOuFiltros() {
        rejeitar(null, PageRequest.of(0, 20));
        rejeitar(FiltroAlertasAtivos.semFiltros(), null);
        rejeitar(FiltroAlertasAtivos.semFiltros(), Pageable.unpaged());
    }

    @Test
    void deveRejeitarTamanhoAcimaDoLimite() { rejeitar(FiltroAlertasAtivos.semFiltros(), PageRequest.of(0, 101)); }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void deveRejeitarIdsNaoPositivosNaEntradaTipada(long id) {
        rejeitar(new FiltroAlertasAtivos(null, null, null, id, null, null, null, null, null), PageRequest.of(0, 20));
        rejeitar(new FiltroAlertasAtivos(null, null, null, null, id, null, null, null, null), PageRequest.of(0, 20));
        rejeitar(new FiltroAlertasAtivos(null, null, null, null, null, null, id, null, null), PageRequest.of(0, 20));
    }

    @Test
    void deveRejeitarJanelaInconsistenteNaEntradaTipada() {
        var inicio = OffsetDateTime.parse("2026-01-02T12:00:00Z");
        rejeitar(new FiltroAlertasAtivos(null, null, null, null, null, null, null, inicio, inicio), PageRequest.of(0, 20));
        rejeitar(new FiltroAlertasAtivos(null, null, null, null, null, null, null, inicio, inicio.minusSeconds(1)), PageRequest.of(0, 20));
    }

    private void rejeitar(FiltroAlertasAtivos filtro, Pageable pagina) {
        assertThatThrownBy(() -> service.listar(filtro, pagina)).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(repository);
    }
}
