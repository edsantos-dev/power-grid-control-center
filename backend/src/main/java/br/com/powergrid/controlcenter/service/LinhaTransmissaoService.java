package br.com.powergrid.controlcenter.service;

import java.util.List;

import br.com.powergrid.controlcenter.dto.LinhaTransmissaoRequestDTO;
import br.com.powergrid.controlcenter.dto.LinhaTransmissaoResponseDTO;
import br.com.powergrid.controlcenter.model.LinhaTransmissao;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.LinhaTransmissaoRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LinhaTransmissaoService {

	private final LinhaTransmissaoRepository linhaTransmissaoRepository;
	private final SubestacaoRepository subestacaoRepository;

	public LinhaTransmissaoService(LinhaTransmissaoRepository linhaTransmissaoRepository, SubestacaoRepository subestacaoRepository) {
		this.linhaTransmissaoRepository = linhaTransmissaoRepository;
		this.subestacaoRepository = subestacaoRepository;
	}

	@Transactional
	public LinhaTransmissaoResponseDTO criar(LinhaTransmissaoRequestDTO request) {
		validar(request);
		Subestacao origem = encontrarSubestacao(request.subestacaoOrigemId());
		Subestacao destino = encontrarSubestacao(request.subestacaoDestinoId());
		LinhaTransmissao linhaTransmissao = new LinhaTransmissao();
		preencher(linhaTransmissao, request, origem, destino);
		return toResponse(linhaTransmissaoRepository.save(linhaTransmissao));
	}

	@Transactional(readOnly = true)
	public List<LinhaTransmissaoResponseDTO> listar() {
		return linhaTransmissaoRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public LinhaTransmissaoResponseDTO buscarPorId(Long id) {
		return toResponse(encontrar(id));
	}

	@Transactional
	public LinhaTransmissaoResponseDTO atualizar(Long id, LinhaTransmissaoRequestDTO request) {
		LinhaTransmissao linhaTransmissao = encontrar(id);
		validar(request);
		Subestacao origem = encontrarSubestacao(request.subestacaoOrigemId());
		Subestacao destino = encontrarSubestacao(request.subestacaoDestinoId());
		preencher(linhaTransmissao, request, origem, destino);
		return toResponse(linhaTransmissao);
	}

	private void validar(LinhaTransmissaoRequestDTO request) {
		if (request.subestacaoOrigemId().equals(request.subestacaoDestinoId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origem e destino devem ser distintos.");
		}
		if ((request.latitude() == null) != (request.longitude() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe latitude e longitude em conjunto.");
		}
	}

	private LinhaTransmissao encontrar(Long id) {
		return linhaTransmissaoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha de transmissão não encontrada."));
	}

	private Subestacao encontrarSubestacao(Long id) {
		return subestacaoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subestação não encontrada."));
	}

	private void preencher(LinhaTransmissao linhaTransmissao, LinhaTransmissaoRequestDTO request, Subestacao origem, Subestacao destino) {
		linhaTransmissao.setNome(request.nome());
		linhaTransmissao.setLatitude(request.latitude());
		linhaTransmissao.setLongitude(request.longitude());
		linhaTransmissao.setTensaoOperacaoKv(request.tensaoOperacaoKv());
		linhaTransmissao.setDataInstalacao(request.dataInstalacao());
		linhaTransmissao.setEstadoOperacional(request.estadoOperacional() == null
				? EstadoOperacional.OPERACIONAL : request.estadoOperacional());
		linhaTransmissao.setSubestacaoOrigem(origem);
		linhaTransmissao.setSubestacaoDestino(destino);
		linhaTransmissao.setExtensaoKm(request.extensaoKm());
	}

	private LinhaTransmissaoResponseDTO toResponse(LinhaTransmissao linhaTransmissao) {
		return new LinhaTransmissaoResponseDTO(linhaTransmissao.getId(), linhaTransmissao.getNome(), linhaTransmissao.getLatitude(),
				linhaTransmissao.getLongitude(), linhaTransmissao.getTensaoOperacaoKv(), linhaTransmissao.getSubestacaoOrigem().getId(),
				linhaTransmissao.getSubestacaoDestino().getId(), linhaTransmissao.getExtensaoKm(),
				linhaTransmissao.getDataInstalacao(), linhaTransmissao.getEstadoOperacional());
	}

}
