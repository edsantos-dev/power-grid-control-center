package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.AlertaSobrecargaResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.AlertaSobrecarga;
import br.com.powergrid.controlcenter.model.MedicaoTransformador;
import br.com.powergrid.controlcenter.model.StatusAlerta;
import br.com.powergrid.controlcenter.repository.AlertaSobrecargaRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaSobrecargaService {
    private static final BigDecimal LIMIAR = new BigDecimal("100.000000");
    private final AlertaSobrecargaRepository repository;
    private final Clock clock;

    public AlertaSobrecargaService(AlertaSobrecargaRepository repository, Clock clock) {
        this.repository = repository; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void avaliarEGerar(MedicaoTransformador medicao, BigDecimal cargaPercentual) {
        if (cargaPercentual.compareTo(LIMIAR) <= 0) return;
        AlertaSobrecarga alerta = new AlertaSobrecarga();
        alerta.setMedicao(medicao); alerta.setAtivo(medicao.getFonte().getAtivo());
        alerta.setCargaPercentual(cargaPercentual); alerta.setLimiarPercentual(LIMIAR);
        alerta.setStatus(StatusAlerta.ATIVO);
        alerta.setGeradoEm(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
        repository.saveAndFlush(alerta);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<AlertaSobrecargaResponseDTO> listar(MultiValueMap<String, String> params) {
        var consulta = ParametrosConsultaMedicao.from(params, "geradoEm");
        Long ativoId = null;
        String ativo = ParametrosConsultaMedicao.parametro(params, "ativoId");
        if (ativo != null) {
            try {
                if (!ativo.matches("[0-9]+")) throw new NumberFormatException();
                ativoId = Long.parseLong(ativo);
                if (ativoId <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ativoId deve ser um inteiro positivo válido.");
            }
        }
        StatusAlerta status = null;
        String valorStatus = ParametrosConsultaMedicao.parametro(params, "status");
        if (valorStatus != null) {
            try { status = StatusAlerta.valueOf(valorStatus); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de alerta inválido."); }
        }
        return PageResponseDTO.from(repository.buscarFiltrados(ativoId, status, consulta.inicio(), consulta.fim(), consulta.pageable())
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AlertaSobrecargaResponseDTO buscarPorId(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta de sobrecarga não encontrado.")));
    }

    private AlertaSobrecargaResponseDTO toResponse(AlertaSobrecarga a) {
        return new AlertaSobrecargaResponseDTO(a.getId(), a.getAtivo().getId(), a.getMedicao().getId(),
                a.getCargaPercentual(), a.getLimiarPercentual(), a.getStatus(), a.getGeradoEm().withOffsetSameInstant(ZoneOffset.UTC),
                a.getReconhecidoEm() == null ? null : a.getReconhecidoEm().withOffsetSameInstant(ZoneOffset.UTC),
                a.getReconhecidoPorUsuario() == null ? null : a.getReconhecidoPorUsuario().getId());
    }
}
