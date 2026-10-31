package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import br.com.powergrid.controlcenter.model.Criticidade;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.dto.AlertaComunicacaoResponseDTO;
import br.com.powergrid.controlcenter.dto.AvaliacaoComunicacaoResultDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.AlertaComunicacao;
import br.com.powergrid.controlcenter.model.StatusComunicacao;
import br.com.powergrid.controlcenter.repository.AlertaComunicacaoRepository;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaComunicacaoService {
    private static final int TAMANHO_LOTE = 100;
    private final AlertaComunicacaoRepository alertas;
    private final FonteMedicaoRepository fontes;
    private final Clock clock;
    private final int toleranciaSegundos;
    private final TransactionTemplate transacao;

    public AlertaComunicacaoService(AlertaComunicacaoRepository alertas, FonteMedicaoRepository fontes,
            Clock clock, PlatformTransactionManager transactionManager,
            @Value("${monitoramento.comunicacao.timeout-segundos:300}") int toleranciaSegundos) {
        if (toleranciaSegundos <= 0) {
            throw new IllegalArgumentException("monitoramento.comunicacao.timeout-segundos deve ser positivo.");
        }
        this.alertas = alertas;
        this.fontes = fontes;
        this.clock = clock;
        this.toleranciaSegundos = toleranciaSegundos;
        this.transacao = new TransactionTemplate(transactionManager);
    }

    public AvaliacaoComunicacaoResultDTO avaliar() {
        var agora = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        long limite = fontes.maiorId();
        long cursor = 0;
        long avaliadas = 0;
        long criados = 0;
        long existentes = 0;
        while (true) {
            var ids = fontes.buscarIdsParaAvaliacao(cursor, limite, PageRequest.of(0, TAMANHO_LOTE));
            if (ids.isEmpty()) break;
            for (Long id : ids) {
                ResultadoFonte resultado = transacao.execute(status -> avaliarFonte(id, agora));
                if (resultado != ResultadoFonte.REMOVIDA) avaliadas++;
                if (resultado == ResultadoFonte.CRIADO) criados++;
                if (resultado == ResultadoFonte.EXISTENTE) existentes++;
            }
            cursor = ids.getLast();
        }
        return new AvaliacaoComunicacaoResultDTO(agora, toleranciaSegundos, avaliadas, criados, existentes);
    }

    private ResultadoFonte avaliarFonte(Long id, OffsetDateTime agora) {
        var fonte = fontes.buscarPorIdComBloqueio(id).orElse(null);
        if (fonte == null) return ResultadoFonte.REMOVIDA;
        if (alertas.existsByFonteIdAndStatus(id, StatusComunicacao.ATIVO)) return ResultadoFonte.EXISTENTE;
        var base = fonte.getUltimaComunicacaoEm() == null ? fonte.getMonitoradaDesde() : fonte.getUltimaComunicacaoEm();
        if (!agora.isAfter(base.plusSeconds(toleranciaSegundos))) return ResultadoFonte.NORMAL;
        var alerta = new AlertaComunicacao();
        alerta.setCriticidade(Criticidade.MEDIA);
        alerta.setFonte(fonte);
        alerta.setAtivo(fonte.getAtivo());
        alerta.setMonitoradaDesde(fonte.getMonitoradaDesde());
        alerta.setUltimaComunicacaoEm(fonte.getUltimaComunicacaoEm());
        alerta.setToleranciaSegundos(toleranciaSegundos);
        alerta.setDetectadoEm(agora);
        alerta.setStatus(StatusComunicacao.ATIVO);
        alertas.saveAndFlush(alerta);
        return ResultadoFonte.CRIADO;
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<AlertaComunicacaoResponseDTO> listar(MultiValueMap<String, String> params) {
        var consulta = ParametrosConsultaMedicao.from(params, "detectadoEm");
        Long fonteId = identificador(params, "fonteId");
        Long ativoId = identificador(params, "ativoId");
        StatusComunicacao status = null;
        String valor = ParametrosConsultaMedicao.parametro(params, "status");
        if (valor != null) {
            try { status = StatusComunicacao.valueOf(valor); }
            catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de comunicação inválido.");
            }
        }
        return PageResponseDTO.from(alertas.buscarFiltrados(fonteId, ativoId, status,
                consulta.inicio(), consulta.fim(), consulta.pageable()).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AlertaComunicacaoResponseDTO buscarPorId(Long id) {
        return toResponse(alertas.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta de comunicação não encontrado.")));
    }

    private Long identificador(MultiValueMap<String, String> params, String nome) {
        String valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nome + " deve ser um inteiro positivo válido.");
        }
    }

    private AlertaComunicacaoResponseDTO toResponse(AlertaComunicacao a) {
        return new AlertaComunicacaoResponseDTO(a.getId(), a.getFonte().getId(), a.getAtivo().getId(),
                utc(a.getMonitoradaDesde()), utc(a.getUltimaComunicacaoEm()), a.getToleranciaSegundos(),
                utc(a.getDetectadoEm()), a.getStatus(), utc(a.getRestabelecidoEm()));
    }

    private OffsetDateTime utc(OffsetDateTime instante) {
        return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private enum ResultadoFonte { NORMAL, CRIADO, EXISTENTE, REMOVIDA }
}
