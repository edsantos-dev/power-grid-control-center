package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.TransformadorRequestDTO;
import br.com.powergrid.controlcenter.dto.TransformadorResponseDTO;
import br.com.powergrid.controlcenter.service.TransformadorService;
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
@RequestMapping("/api/transformadores")
public class TransformadorController {

	private final TransformadorService transformadorService;

	public TransformadorController(TransformadorService transformadorService) {
		this.transformadorService = transformadorService;
	}

	@GetMapping
	public List<TransformadorResponseDTO> listar() {
		return transformadorService.listar();
	}

	@GetMapping("/{id}")
	public TransformadorResponseDTO buscarPorId(@PathVariable Long id) {
		return transformadorService.buscarPorId(id);
	}

	@PutMapping("/{id}")
	public TransformadorResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody TransformadorRequestDTO request) {
		return transformadorService.atualizar(id, request);
	}

	@PostMapping
	public ResponseEntity<TransformadorResponseDTO> criar(@Valid @RequestBody TransformadorRequestDTO request) {
		TransformadorResponseDTO response = transformadorService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
