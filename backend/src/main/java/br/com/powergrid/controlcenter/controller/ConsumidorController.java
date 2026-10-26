package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.ConsumidorRequestDTO;
import br.com.powergrid.controlcenter.dto.ConsumidorResponseDTO;
import br.com.powergrid.controlcenter.service.ConsumidorService;
import br.com.powergrid.controlcenter.service.MedidorInteligenteService;
import br.com.powergrid.controlcenter.dto.MedidorResponseDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/consumidores")
public class ConsumidorController {

	private final ConsumidorService consumidorService;
	private final MedidorInteligenteService medidorService;

	public ConsumidorController(ConsumidorService consumidorService, MedidorInteligenteService medidorService) {
		this.consumidorService = consumidorService;
		this.medidorService = medidorService;
	}

	@GetMapping
	public List<ConsumidorResponseDTO> listar() {
		return consumidorService.listar();
	}

	@GetMapping("/{id}")
	public ConsumidorResponseDTO buscarPorId(@PathVariable Long id) {
		return consumidorService.buscarPorId(id);
	}


	@GetMapping("/{id}/medidores")
	public List<MedidorResponseDTO> listarMedidores(@PathVariable Long id) {
		return medidorService.listarPorConsumidor(id);
	}

	@PostMapping
	public ResponseEntity<ConsumidorResponseDTO> criar(@Valid @RequestBody ConsumidorRequestDTO request) {
		ConsumidorResponseDTO response = consumidorService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
