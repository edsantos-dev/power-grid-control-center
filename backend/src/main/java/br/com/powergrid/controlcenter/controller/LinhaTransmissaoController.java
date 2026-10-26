package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.LinhaTransmissaoRequestDTO;
import br.com.powergrid.controlcenter.dto.LinhaTransmissaoResponseDTO;
import br.com.powergrid.controlcenter.service.LinhaTransmissaoService;
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
@RequestMapping("/api/linhas-transmissao")
public class LinhaTransmissaoController {

	private final LinhaTransmissaoService linhaTransmissaoService;

	public LinhaTransmissaoController(LinhaTransmissaoService linhaTransmissaoService) {
		this.linhaTransmissaoService = linhaTransmissaoService;
	}

	@GetMapping
	public List<LinhaTransmissaoResponseDTO> listar() {
		return linhaTransmissaoService.listar();
	}

	@GetMapping("/{id}")
	public LinhaTransmissaoResponseDTO buscarPorId(@PathVariable Long id) {
		return linhaTransmissaoService.buscarPorId(id);
	}

	@PutMapping("/{id}")
	public LinhaTransmissaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody LinhaTransmissaoRequestDTO request) {
		return linhaTransmissaoService.atualizar(id, request);
	}

	@PostMapping
	public ResponseEntity<LinhaTransmissaoResponseDTO> criar(@Valid @RequestBody LinhaTransmissaoRequestDTO request) {
		LinhaTransmissaoResponseDTO response = linhaTransmissaoService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
