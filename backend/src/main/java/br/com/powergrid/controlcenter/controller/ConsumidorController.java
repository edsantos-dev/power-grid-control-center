package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.ConsumidorRequestDTO;
import br.com.powergrid.controlcenter.dto.ConsumidorDetalheResponseDTO;
import br.com.powergrid.controlcenter.dto.ConsumidorResumoResponseDTO;
import br.com.powergrid.controlcenter.service.ConsumidorService;
import br.com.powergrid.controlcenter.service.MedidorInteligenteService;
import br.com.powergrid.controlcenter.dto.MedidorResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/consumidores")
@PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR', 'TECNICO_MANUTENCAO')")
public class ConsumidorController {

	private final ConsumidorService consumidorService;
	private final MedidorInteligenteService medidorService;

	public ConsumidorController(ConsumidorService consumidorService, MedidorInteligenteService medidorService) {
		this.consumidorService = consumidorService;
		this.medidorService = medidorService;
	}

	@GetMapping
	public List<ConsumidorResumoResponseDTO> listar() {
		return consumidorService.listar();
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> buscarPorId(@PathVariable Long id, Authentication autenticacao) {
		return responder(ResponseEntity.ok(), consumidorService.buscarPorId(id), autenticacao);
	}


	@GetMapping("/{id}/medidores")
	public List<MedidorResponseDTO> listarMedidores(@PathVariable Long id) {
		return medidorService.listarPorConsumidor(id);
	}

	@PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'OPERADOR')")
	@PostMapping
	public ResponseEntity<?> criar(@Valid @RequestBody ConsumidorRequestDTO request, Authentication autenticacao) {
		ConsumidorDetalheResponseDTO response = consumidorService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return responder(ResponseEntity.created(location), response, autenticacao);
	}

	private ResponseEntity<?> responder(ResponseEntity.BodyBuilder resposta, ConsumidorDetalheResponseDTO detalhe,
			Authentication autenticacao) {
		if (autenticacao.getAuthorities().stream().anyMatch(
				autoridade -> "ROLE_GERENTE_OPERACOES".equals(autoridade.getAuthority()))) {
			return resposta.cacheControl(CacheControl.noStore()).body(detalhe);
		}
		return resposta.body(detalhe.resumo());
	}
}
