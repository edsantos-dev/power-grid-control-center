package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorRequestDTO;
import br.com.powergrid.controlcenter.dto.MedicaoTransformadorResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.MedicaoTransformador;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import br.com.powergrid.controlcenter.repository.MedicaoTransformadorRepository;
import br.com.powergrid.controlcenter.repository.TransformadorRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicaoTransformadorService {
    private final MedicaoTransformadorRepository repository;
    private final FonteMedicaoRepository fontes;
    private final TransformadorRepository transformadores;
    private final Clock clock;
    private final long toleranciaSegundos;

    public MedicaoTransformadorService(MedicaoTransformadorRepository repository, FonteMedicaoRepository fontes,
            TransformadorRepository transformadores, Clock clock,
            @Value("${medicoes.tolerancia-futura-segundos:60}") long toleranciaSegundos) {
        if (toleranciaSegundos < 0) throw new IllegalArgumentException("Tolerância deve ser não negativa.");
        this.repository = repository; this.fontes = fontes; this.transformadores = transformadores;
        this.clock = clock; this.toleranciaSegundos = toleranciaSegundos;
    }

    @Transactional
    public MedicaoTransformadorResponseDTO ingerir(Long id, MedicaoTransformadorRequestDTO request) {
        var transformador = transformadores.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformador não encontrado."));
        var fonte = fontes.findById(request.fonteId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte de medição não encontrada."));
        if (!fonte.getAtivo().getId().equals(id)) throw invalido("A fonte não pertence ao transformador informado.");
        if (request.potenciaAparenteMva() == null || request.potenciaAparenteMva().signum() < 0) {
            throw invalido("Potência aparente deve ser informada e não negativa.");
        }
        Instant agora = Instant.now(clock);
        if (request.medidoEm().toInstant().isAfter(agora.plusSeconds(toleranciaSegundos))) {
            throw invalido("Instante da medição excede a tolerância futura.");
        }
        MedicaoTransformador medicao = new MedicaoTransformador();
        medicao.setFonte(fonte);
        medicao.setMedidoEm(request.medidoEm().withOffsetSameInstant(ZoneOffset.UTC));
        medicao.setRecebidoEm(agora.atOffset(ZoneOffset.UTC));
        medicao.setPotenciaAparenteMva(request.potenciaAparenteMva());
        medicao.setCapacidadeReferenciaMva(transformador.getCapacidadeMva());
        try {
            repository.saveAndFlush(medicao);
        } catch (DataIntegrityViolationException ex) {
            throw invalido("Medição incompatível com as restrições de integridade.");
        }
        return toResponse(medicao);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<MedicaoTransformadorResponseDTO> listar(Long id, MultiValueMap<String, String> params) {
        var consulta = ParametrosConsultaMedicao.from(params);
        if (!transformadores.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformador não encontrado.");
        }
        return PageResponseDTO.from(repository.buscarPorTransformadorEPeriodo(id, consulta.inicio(), consulta.fim(), consulta.pageable())
                .map(this::toResponse));
    }

    private MedicaoTransformadorResponseDTO toResponse(MedicaoTransformador m) {
        BigDecimal percentual = m.getPotenciaAparenteMva().multiply(new BigDecimal("100"))
                .divide(m.getCapacidadeReferenciaMva(), 6, RoundingMode.HALF_EVEN);
        return new MedicaoTransformadorResponseDTO(m.getId(), m.getFonte().getAtivo().getId(), m.getFonte().getId(),
                m.getFonte().getOrigem(), m.getMedidoEm().withOffsetSameInstant(ZoneOffset.UTC),
                m.getRecebidoEm().withOffsetSameInstant(ZoneOffset.UTC), m.getPotenciaAparenteMva(),
                m.getCapacidadeReferenciaMva(), percentual);
    }

    private ResponseStatusException invalido(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
