package br.com.powergrid.controlcenter.service;

import java.util.List;

import br.com.powergrid.controlcenter.dto.TransformadorRequestDTO;
import br.com.powergrid.controlcenter.dto.TransformadorResponseDTO;
import br.com.powergrid.controlcenter.model.Transformador;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.repository.TransformadorRepository;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransformadorService {

	private final TransformadorRepository transformadorRepository;
	private final AlimentadorRepository alimentadorRepository;

	public TransformadorService(TransformadorRepository transformadorRepository, AlimentadorRepository alimentadorRepository) {
		this.transformadorRepository = transformadorRepository;
		this.alimentadorRepository = alimentadorRepository;
	}

	@Transactional
	public TransformadorResponseDTO criar(TransformadorRequestDTO request) {
		Alimentador alimentador = encontrarAlimentador(request.alimentadorId());
		Transformador transformador = new Transformador();
		preencher(transformador, request, alimentador);
		return toResponse(transformadorRepository.save(transformador));
	}

	@Transactional(readOnly = true)
	public List<TransformadorResponseDTO> listar() {
		return transformadorRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public TransformadorResponseDTO buscarPorId(Long id) {
		return toResponse(encontrar(id));
	}

	@Transactional
	public TransformadorResponseDTO atualizar(Long id, TransformadorRequestDTO request) {
		Transformador transformador = encontrar(id);
		Alimentador alimentador = encontrarAlimentador(request.alimentadorId());
		preencher(transformador, request, alimentador);
		return toResponse(transformador);
	}

	private Transformador encontrar(Long id) {
		return transformadorRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transformador não encontrado."));
	}

	private Alimentador encontrarAlimentador(Long id) {
		return alimentadorRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimentador não encontrado."));
	}

	private void preencher(Transformador transformador, TransformadorRequestDTO request, Alimentador alimentador) {
		transformador.setNome(request.nome());
		transformador.setLatitude(request.latitude());
		transformador.setLongitude(request.longitude());
		transformador.setCapacidadeMva(request.capacidadeMva());
		transformador.setDataInstalacao(request.dataInstalacao());
		transformador.setEstadoOperacional(request.estadoOperacional() == null
				? EstadoOperacional.OPERACIONAL : request.estadoOperacional());
		transformador.setAlimentador(alimentador);
	}

	private TransformadorResponseDTO toResponse(Transformador transformador) {
		return new TransformadorResponseDTO(transformador.getId(), transformador.getNome(), transformador.getLatitude(),
				transformador.getLongitude(), transformador.getCapacidadeMva(), transformador.getAlimentador().getId(),
				transformador.getAlimentador().getSubestacao().getId(),
				transformador.getDataInstalacao(), transformador.getEstadoOperacional());
	}

}
