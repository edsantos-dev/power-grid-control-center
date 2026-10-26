package br.com.powergrid.controlcenter.service;

import java.util.List;
import java.util.Locale;
import br.com.powergrid.controlcenter.dto.ConsumidorRequestDTO;
import br.com.powergrid.controlcenter.dto.ConsumidorResponseDTO;
import br.com.powergrid.controlcenter.model.Consumidor;
import br.com.powergrid.controlcenter.model.Consumidor.TipoPessoa;
import br.com.powergrid.controlcenter.repository.ConsumidorRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConsumidorService {
    private final ConsumidorRepository repository;

    public ConsumidorService(ConsumidorRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ConsumidorResponseDTO criar(ConsumidorRequestDTO request) {
        String documento = normalizarEValidar(request.documento(), request.tipoPessoa());
        if (repository.existsByDocumento(documento)) throw documentoDuplicado();
        Consumidor consumidor = new Consumidor();
        consumidor.setNomeRazaoSocial(request.nomeRazaoSocial());
        consumidor.setTipoPessoa(request.tipoPessoa());
        consumidor.setDocumento(documento);
        consumidor.setEnderecoCorrespondencia(request.enderecoCorrespondencia());
        try {
            return toResponse(repository.saveAndFlush(consumidor));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable causa = exception; causa != null; causa = causa.getCause()) {
                if (causa instanceof ConstraintViolationException violacao
                        && "23505".equals(violacao.getSQLState())
                        && violacao.getConstraintName() != null
                        && violacao.getConstraintName().toLowerCase(Locale.ROOT).contains("uq_consumidor_documento")) {
                    throw documentoDuplicado();
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ConsumidorResponseDTO> listar() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ConsumidorResponseDTO buscarPorId(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado.")));
    }

    private String normalizarEValidar(String entrada, TipoPessoa tipo) {
        if (entrada == null || tipo == null) throw documentoInvalido();
        String valor = entrada.trim();
        String formato = tipo == TipoPessoa.PF
                ? "(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})"
                : "(?:[A-Za-z0-9]{12}[0-9]{2}|[A-Za-z0-9]{2}\\.[A-Za-z0-9]{3}\\.[A-Za-z0-9]{3}/[A-Za-z0-9]{4}-[0-9]{2})";
        if (!valor.matches(formato)) throw documentoInvalido();
        String documento = valor.replace(".", "").replace("-", "").replace("/", "").toUpperCase(Locale.ROOT);
        if (documento.chars().allMatch(c -> c == documento.charAt(0))) throw documentoInvalido();
        int tamanhoBase = tipo == TipoPessoa.PF ? 9 : 12;
        for (int digito = 0; digito < 2; digito++) {
            int tamanho = tamanhoBase + digito;
            int soma = 0;
            for (int i = 0; i < tamanho; i++) {
                int peso = tipo == TipoPessoa.PF ? tamanho + 1 - i : 2 + (tamanho - 1 - i) % 8;
                soma += (documento.charAt(i) - '0') * peso;
            }
            int resto = soma % 11;
            int esperado = resto < 2 ? 0 : 11 - resto;
            if (documento.charAt(tamanho) - '0' != esperado) throw documentoInvalido();
        }
        return documento;
    }

    private ResponseStatusException documentoInvalido() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documento inválido para o tipo de pessoa.");
    }

    private ResponseStatusException documentoDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Documento já cadastrado.");
    }

    private ConsumidorResponseDTO toResponse(Consumidor consumidor) {
        return new ConsumidorResponseDTO(consumidor.getId(), consumidor.getNomeRazaoSocial(),
                consumidor.getTipoPessoa(), consumidor.getDocumento(), consumidor.getEnderecoCorrespondencia());
    }
}
