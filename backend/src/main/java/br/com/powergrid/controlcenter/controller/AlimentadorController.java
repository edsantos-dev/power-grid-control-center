package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.AlimentadorRequestDTO;
import br.com.powergrid.controlcenter.dto.AlimentadorResponseDTO;
import br.com.powergrid.controlcenter.service.AlimentadorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/alimentadores")
public class AlimentadorController {

	private final AlimentadorService alimentadorService;

	public AlimentadorController(AlimentadorService alimentadorService) {
		this.alimentadorService = alimentadorService;
	}

	@GetMapping
	public List<AlimentadorResponseDTO> listar() {
		return alimentadorService.listar();
	}

	@GetMapping("/{id}")
	public AlimentadorResponseDTO buscarPorId(@PathVariable Long id) {
		return alimentadorService.buscarPorId(id);
	}

	@PutMapping("/{id}")
	public AlimentadorResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody AlimentadorRequestDTO request) {
		return alimentadorService.atualizar(id, request);
	}

	@PostMapping
	public ResponseEntity<AlimentadorResponseDTO> criar(@Valid @RequestBody AlimentadorRequestDTO request) {
		AlimentadorResponseDTO response = alimentadorService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
