package br.com.powergrid.controlcenter.service;

import java.util.List;

import br.com.powergrid.controlcenter.dto.SubestacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.SubestacaoResponseDTO;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubestacaoService {

	private final SubestacaoRepository subestacaoRepository;

	public SubestacaoService(SubestacaoRepository subestacaoRepository) {
		this.subestacaoRepository = subestacaoRepository;
	}

	@Transactional
	public SubestacaoResponseDTO criar(SubestacaoRequestDTO request) {
		Subestacao subestacao = new Subestacao();
		preencher(subestacao, request);
		return toResponse(subestacaoRepository.save(subestacao));
	}

	@Transactional(readOnly = true)
	public List<SubestacaoResponseDTO> listar() {
		return subestacaoRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public SubestacaoResponseDTO buscarPorId(Long id) {
		return toResponse(encontrar(id));
	}

	@Transactional
	public SubestacaoResponseDTO atualizar(Long id, SubestacaoRequestDTO request) {
		Subestacao subestacao = encontrar(id);
		preencher(subestacao, request);
		return toResponse(subestacao);
	}

	private Subestacao encontrar(Long id) {
		return subestacaoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subestação não encontrada."));
	}

	private void preencher(Subestacao subestacao, SubestacaoRequestDTO request) {
		subestacao.setNome(request.nome());
		subestacao.setLatitude(request.latitude());
		subestacao.setLongitude(request.longitude());
		subestacao.setTensaoNominalKv(request.tensaoNominalKv());
		subestacao.setCapacidadeMva(request.capacidadeMva());
		subestacao.setDataInstalacao(request.dataInstalacao());
		subestacao.setEstadoOperacional(request.estadoOperacional() == null
				? EstadoOperacional.OPERACIONAL : request.estadoOperacional());
	}

	private SubestacaoResponseDTO toResponse(Subestacao salva) {
		return new SubestacaoResponseDTO(salva.getId(), salva.getNome(), salva.getLatitude(),
				salva.getLongitude(), salva.getTensaoNominalKv(), salva.getCapacidadeMva(),
				salva.getDataInstalacao(), salva.getEstadoOperacional());
	}

}
