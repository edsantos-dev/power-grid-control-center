package br.com.powergrid.controlcenter.service;

import java.util.List;
import java.util.Locale;
import br.com.powergrid.controlcenter.dto.MedidorRequestDTO;
import br.com.powergrid.controlcenter.dto.MedidorInstalacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.MedidorResponseDTO;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Consumidor;
import br.com.powergrid.controlcenter.model.MedidorInteligente;
import br.com.powergrid.controlcenter.model.Transformador;
import br.com.powergrid.controlcenter.repository.ConsumidorRepository;
import br.com.powergrid.controlcenter.repository.MedidorInteligenteRepository;
import br.com.powergrid.controlcenter.repository.TransformadorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedidorInteligenteService {
    private final MedidorInteligenteRepository repository;
    private final ConsumidorRepository consumidorRepository;
    private final TransformadorRepository transformadorRepository;
    private final EntityManager entityManager;

    public MedidorInteligenteService(MedidorInteligenteRepository repository,
            ConsumidorRepository consumidorRepository, TransformadorRepository transformadorRepository,
            EntityManager entityManager) {
        this.repository = repository;
        this.consumidorRepository = consumidorRepository;
        this.transformadorRepository = transformadorRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public MedidorResponseDTO criar(MedidorRequestDTO request) {
        if (repository.existsByNumeroSerie(request.numeroSerie())) throw serieDuplicada();
        MedidorInteligente medidor = new MedidorInteligente();
        medidor.setNome(request.nome());
        medidor.setNumeroSerie(request.numeroSerie());
        medidor.setEstadoOperacional(request.estadoOperacional() == null
                ? EstadoOperacional.OPERACIONAL : request.estadoOperacional());
        try {
            return toResponse(repository.saveAndFlush(medidor));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
                if (causa instanceof ConstraintViolationException violacao
                        && "23505".equals(violacao.getSQLState())
                        && violacao.getConstraintName() != null
                        && violacao.getConstraintName().toLowerCase(Locale.ROOT).contains("uq_medidor_numero_serie")) {
                    throw serieDuplicada();
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<MedidorResponseDTO> listar() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MedidorResponseDTO> listarPorConsumidor(Long consumidorId) {
        if (consumidorId == null || consumidorId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID do consumidor deve ser positivo.");
        }
        if (!consumidorRepository.existsById(consumidorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado.");
        }
        return repository.findByConsumidorIdOrderByIdAsc(consumidorId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MedidorResponseDTO buscarPorId(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Medidor não encontrado.")));
    }

    @Transactional
    public MedidorResponseDTO instalar(Long id, MedidorInstalacaoRequestDTO request) {
        MedidorInteligente medidor = entityManager.find(MedidorInteligente.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (medidor == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medidor não encontrado.");
        if (medidor.getConsumidor() != null || medidor.getTransformador() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Medidor já possui vínculo de instalação.");
        }
        Consumidor consumidor = consumidorRepository.findById(request.consumidorId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado."));
        Transformador transformador = transformadorRepository.findById(request.transformadorId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformador não encontrado."));
        medidor.setConsumidor(consumidor);
        medidor.setTransformador(transformador);
        medidor.setLatitude(request.latitude());
        medidor.setLongitude(request.longitude());
        medidor.setDataInstalacao(request.dataInstalacao());
        medidor.setEnderecoFornecimento(request.enderecoFornecimento());
        return toResponse(medidor);
    }

    private ResponseStatusException serieDuplicada() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Número de série já cadastrado.");
    }

    private MedidorResponseDTO toResponse(MedidorInteligente medidor) {
        Transformador transformador = medidor.getTransformador();
        return new MedidorResponseDTO(medidor.getId(), medidor.getNome(), medidor.getNumeroSerie(),
                medidor.getEstadoOperacional(), medidor.getLatitude(), medidor.getLongitude(),
                medidor.getDataInstalacao(), medidor.getEnderecoFornecimento(),
                medidor.getConsumidor() == null ? null : medidor.getConsumidor().getId(),
                transformador == null ? null : transformador.getId(),
                transformador == null ? null : transformador.getAlimentador().getId(),
                transformador == null ? null : transformador.getAlimentador().getSubestacao().getId());
    }
}
