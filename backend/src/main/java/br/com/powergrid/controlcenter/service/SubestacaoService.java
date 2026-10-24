package br.com.powergrid.controlcenter.service;

import br.com.powergrid.controlcenter.dto.SubestacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.SubestacaoResponseDTO;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.model.Subestacao;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubestacaoService {

	private final SubestacaoRepository subestacaoRepository;

	public SubestacaoService(SubestacaoRepository subestacaoRepository) {
		this.subestacaoRepository = subestacaoRepository;
	}

	@Transactional
	public SubestacaoResponseDTO criar(SubestacaoRequestDTO request) {
		Subestacao subestacao = new Subestacao();
		subestacao.setNome(request.nome());
		subestacao.setLatitude(request.latitude());
		subestacao.setLongitude(request.longitude());
		subestacao.setTensaoNominalKv(request.tensaoNominalKv());
		subestacao.setCapacidadeMva(request.capacidadeMva());
		subestacao.setDataInstalacao(request.dataInstalacao());
		subestacao.setEstadoOperacional(request.estadoOperacional() == null
				? EstadoOperacional.OPERACIONAL : request.estadoOperacional());

		Subestacao salva = subestacaoRepository.save(subestacao);
		return new SubestacaoResponseDTO(salva.getId(), salva.getNome(), salva.getLatitude(),
				salva.getLongitude(), salva.getTensaoNominalKv(), salva.getCapacidadeMva(),
				salva.getDataInstalacao(), salva.getEstadoOperacional());
	}

}
