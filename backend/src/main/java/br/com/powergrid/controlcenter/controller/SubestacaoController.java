package br.com.powergrid.controlcenter.controller;

import java.net.URI;
import java.util.List;

import br.com.powergrid.controlcenter.dto.SubestacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.SubestacaoResponseDTO;
import br.com.powergrid.controlcenter.service.SubestacaoService;
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
@RequestMapping("/api/subestacoes")
public class SubestacaoController {

	private final SubestacaoService subestacaoService;

	public SubestacaoController(SubestacaoService subestacaoService) {
		this.subestacaoService = subestacaoService;
	}

	@GetMapping
	public List<SubestacaoResponseDTO> listar() {
		return subestacaoService.listar();
	}

	@GetMapping("/{id}")
	public SubestacaoResponseDTO buscarPorId(@PathVariable Long id) {
		return subestacaoService.buscarPorId(id);
	}

	@PutMapping("/{id}")
	public SubestacaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody SubestacaoRequestDTO request) {
		return subestacaoService.atualizar(id, request);
	}

	@PostMapping
	public ResponseEntity<SubestacaoResponseDTO> criar(@Valid @RequestBody SubestacaoRequestDTO request) {
		SubestacaoResponseDTO response = subestacaoService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
