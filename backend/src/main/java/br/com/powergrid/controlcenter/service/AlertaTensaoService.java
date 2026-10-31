package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.dto.AlertaTensaoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.AlertaTensaoRepository;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaTensaoService {
    private static final BigDecimal FATOR_INFERIOR = new BigDecimal("0.95");
    private static final BigDecimal FATOR_SUPERIOR = new BigDecimal("1.05");
    private final AlertaTensaoRepository repository;
    private final Clock clock;
    private final UsuarioRepository usuarios;

    public AlertaTensaoService(AlertaTensaoRepository repository, Clock clock, UsuarioRepository usuarios) {
        this.repository = repository; this.clock = clock; this.usuarios = usuarios;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void avaliarEGerar(MedicaoLinha medicao, BigDecimal referencia) {
        BigDecimal tensao = medicao.getTensaoKv();
        if (tensao == null) return;
        if (referencia == null || referencia.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Linha sem tensão de referência válida.");
        }
        BigDecimal inferior = referencia.multiply(FATOR_INFERIOR);
        BigDecimal superior = referencia.multiply(FATOR_SUPERIOR);
        TipoAlertaTensao tipo;
        if (tensao.compareTo(inferior) <= 0) tipo = TipoAlertaTensao.SUBTENSAO;
        else if (tensao.compareTo(superior) >= 0) tipo = TipoAlertaTensao.SOBRETENSAO;
        else return;
        AlertaTensao alerta = new AlertaTensao();
        alerta.setMedicao(medicao); alerta.setAtivo(medicao.getFonte().getAtivo()); alerta.setTipo(tipo);
        alerta.setTensaoMedidaKv(tensao); alerta.setTensaoReferenciaKv(referencia);
        alerta.setLimiteInferiorKv(inferior); alerta.setLimiteSuperiorKv(superior);
        alerta.setStatus(StatusAlerta.ATIVO); alerta.setGeradoEm(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
        repository.saveAndFlush(alerta);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<AlertaTensaoResponseDTO> listar(MultiValueMap<String, String> params) {
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
        TipoAlertaTensao tipo = null;
        String valorTipo = ParametrosConsultaMedicao.parametro(params, "tipo");
        if (valorTipo != null) {
            try { tipo = TipoAlertaTensao.valueOf(valorTipo); }
            catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de alerta inválido."); }
        }
        return PageResponseDTO.from(repository.buscarFiltrados(ativoId, tipo, status, consulta.inicio(), consulta.fim(), consulta.pageable())
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AlertaTensaoResponseDTO buscarPorId(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta de tensão não encontrado.")));
    }

    @Transactional
    public AlertaTensaoResponseDTO reconhecer(Long id, Long usuarioId) {
        var alerta = repository.buscarPorIdParaReconhecimento(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Alerta de tensão não encontrado."));
        if (alerta.getStatus() == StatusAlerta.RECONHECIDO) return toResponse(alerta);
        var usuario = usuarios.findById(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário autenticado não disponível."));
        alerta.setStatus(StatusAlerta.RECONHECIDO);
        alerta.setReconhecidoPorUsuario(usuario);
        alerta.setReconhecidoEm(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        repository.flush();
        return toResponse(alerta);
    }

    private AlertaTensaoResponseDTO toResponse(AlertaTensao a) {
        return new AlertaTensaoResponseDTO(a.getId(), a.getAtivo().getId(), a.getMedicao().getId(), a.getTipo(),
                a.getTensaoMedidaKv(), a.getTensaoReferenciaKv(), a.getLimiteInferiorKv(), a.getLimiteSuperiorKv(),
                a.getStatus(), a.getGeradoEm().withOffsetSameInstant(ZoneOffset.UTC),
                a.getReconhecidoEm() == null ? null : a.getReconhecidoEm().withOffsetSameInstant(ZoneOffset.UTC),
                a.getReconhecidoPorUsuario() == null ? null : a.getReconhecidoPorUsuario().getId());
    }
}
