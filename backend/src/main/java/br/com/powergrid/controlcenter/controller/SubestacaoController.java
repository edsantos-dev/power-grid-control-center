package br.com.powergrid.controlcenter.controller;

import java.net.URI;

import br.com.powergrid.controlcenter.dto.SubestacaoRequestDTO;
import br.com.powergrid.controlcenter.dto.SubestacaoResponseDTO;
import br.com.powergrid.controlcenter.service.SubestacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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

	@PostMapping
	public ResponseEntity<SubestacaoResponseDTO> criar(@Valid @RequestBody SubestacaoRequestDTO request) {
		SubestacaoResponseDTO response = subestacaoService.criar(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(response.id()).toUri();
		return ResponseEntity.created(location).body(response);
	}

}
