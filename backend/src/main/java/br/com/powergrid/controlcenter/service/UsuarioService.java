package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

import br.com.powergrid.controlcenter.dto.UsuarioRequestDTO;
import br.com.powergrid.controlcenter.dto.UsuarioResponseDTO;
import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.util.NormalizadorEmail;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder, Clock clock) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    @Transactional
    public UsuarioResponseDTO criar(UsuarioRequestDTO request) {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        var autor = usuarios.findByEmail(NormalizadorEmail.normalizar(autenticacao.getName()))
                .filter(usuario -> usuario.isAtivo() && usuario.getPerfil() == PerfilAcesso.GERENTE_OPERACOES)
                .orElseThrow(() -> new AccessDeniedException("Gerente ativo não encontrado."));
        String email = request.email();
        if (usuarios.existsByEmail(email)) {
            throw emailDuplicado();
        }

        var usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(email);
        usuario.setMatricula("USR-" + UUID.randomUUID());
        usuario.setPerfil(request.perfil());
        usuario.setSenhaHash(encoder.encode(request.senha()));
        usuario.setCriadoPorUsuarioId(autor.getId());
        usuario.setCriadoEm(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
                if (causa instanceof ConstraintViolationException violacao
                        && "23505".equals(violacao.getSQLState())
                        && violacao.getConstraintName() != null
                        && violacao.getConstraintName().toLowerCase(Locale.ROOT).contains("uq_usuario_email")) {
                    throw emailDuplicado();
                }
            }
            throw ex;
        }
        return new UsuarioResponseDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getMatricula(),
                usuario.getPerfil(), usuario.getCriadoPorUsuarioId(), usuario.getCriadoEm());
    }

    private ResponseStatusException emailDuplicado() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "E-mail já cadastrado.");
    }
}
