package br.com.powergrid.controlcenter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TratativaAlertaRequestDTO(@NotBlank @Size(max = 1000) String comentario) { }
