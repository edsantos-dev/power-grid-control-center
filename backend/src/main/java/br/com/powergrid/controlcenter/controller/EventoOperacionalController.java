package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.EventoOperacionalRequestDTO;
import br.com.powergrid.controlcenter.dto.EventoOperacionalResponseDTO;
import br.com.powergrid.controlcenter.service.EventoOperacionalService;
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
@RequestMapping("/api/eventos-operacionais")
public class EventoOperacionalController {

	private final EventoOperacionalService eventoOperacionalService;

	public EventoOperacionalController(EventoOperacionalService eventoOperacionalService) {
		this.eventoOperacionalService = eventoOperacionalService;
	}

	@GetMapping
	public List<EventoOperacionalResponseDTO> listar() {
		return eventoOperacionalService.listar();
	}

	@GetMapping("/{id}")
	public EventoOperacionalResponseDTO buscarPorId(@PathVariable Long id) {
		return eventoOperacionalService.buscarPorId(id);
	}


	@PostMapping
	public ResponseEntity<EventoOperacionalResponseDTO> criar(@Valid @RequestBody EventoOperacionalRequestDTO request) {
		EventoOperacionalResponseDTO response = eventoOperacionalService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
