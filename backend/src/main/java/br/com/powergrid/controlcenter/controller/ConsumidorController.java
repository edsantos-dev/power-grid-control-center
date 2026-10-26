package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.ConsumidorRequestDTO;
import br.com.powergrid.controlcenter.dto.ConsumidorResponseDTO;
import br.com.powergrid.controlcenter.service.ConsumidorService;
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

	public ConsumidorController(ConsumidorService consumidorService) {
		this.consumidorService = consumidorService;
	}

	@GetMapping
	public List<ConsumidorResponseDTO> listar() {
		return consumidorService.listar();
	}

	@GetMapping("/{id}")
	public ConsumidorResponseDTO buscarPorId(@PathVariable Long id) {
		return consumidorService.buscarPorId(id);
	}


	@PostMapping
	public ResponseEntity<ConsumidorResponseDTO> criar(@Valid @RequestBody ConsumidorRequestDTO request) {
		ConsumidorResponseDTO response = consumidorService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
