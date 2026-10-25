package br.com.powergrid.controlcenter.service;

import java.util.List;

import br.com.powergrid.controlcenter.dto.AlimentadorRequestDTO;
import br.com.powergrid.controlcenter.dto.AlimentadorResponseDTO;
import br.com.powergrid.controlcenter.model.Alimentador;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.AlimentadorRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlimentadorService {

	private final AlimentadorRepository alimentadorRepository;
	private final SubestacaoRepository subestacaoRepository;

	public AlimentadorService(AlimentadorRepository alimentadorRepository, SubestacaoRepository subestacaoRepository) {
		this.alimentadorRepository = alimentadorRepository;
		this.subestacaoRepository = subestacaoRepository;
	}

	@Transactional
	public AlimentadorResponseDTO criar(AlimentadorRequestDTO request) {
		Subestacao subestacao = encontrarSubestacao(request.subestacaoId());
		Alimentador alimentador = new Alimentador();
		preencher(alimentador, request, subestacao);
		return toResponse(alimentadorRepository.save(alimentador));
	}

	@Transactional(readOnly = true)
	public List<AlimentadorResponseDTO> listar() {
		return alimentadorRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public AlimentadorResponseDTO buscarPorId(Long id) {
		return toResponse(encontrar(id));
	}

	@Transactional
	public AlimentadorResponseDTO atualizar(Long id, AlimentadorRequestDTO request) {
		Alimentador alimentador = encontrar(id);
		Subestacao subestacao = encontrarSubestacao(request.subestacaoId());
		preencher(alimentador, request, subestacao);
		return toResponse(alimentador);
	}

	private Alimentador encontrar(Long id) {
		return alimentadorRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alimentador não encontrado."));
	}

	private Subestacao encontrarSubestacao(Long id) {
		return subestacaoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subestação não encontrada."));
	}

	private void preencher(Alimentador alimentador, AlimentadorRequestDTO request, Subestacao subestacao) {
		alimentador.setNome(request.nome());
		alimentador.setLatitude(request.latitude());
		alimentador.setLongitude(request.longitude());
		alimentador.setTensaoNominalKv(request.tensaoNominalKv());
		alimentador.setDataInstalacao(request.dataInstalacao());
		alimentador.setEstadoOperacional(request.estadoOperacional() == null
				? EstadoOperacional.OPERACIONAL : request.estadoOperacional());
		alimentador.setSubestacao(subestacao);
	}

	private AlimentadorResponseDTO toResponse(Alimentador alimentador) {
		return new AlimentadorResponseDTO(alimentador.getId(), alimentador.getNome(), alimentador.getLatitude(),
				alimentador.getLongitude(), alimentador.getTensaoNominalKv(), alimentador.getSubestacao().getId(),
				alimentador.getDataInstalacao(), alimentador.getEstadoOperacional());
	}

}
