package br.com.powergrid.controlcenter.config;

import java.util.UUID;

import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!prod")
@ConditionalOnProperty(prefix = "scada.provisionamento.admin-local", name = "enabled", havingValue = "true")
public class ProvisionadorAdminLocal implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionadorAdminLocal.class);
    private static final String EMAIL = "admin@powergrid.com";
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public ProvisionadorAdminLocal(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (usuarios.existsByEmail(EMAIL)) {
            return;
        }
        var usuario = new Usuario();
        usuario.setNome("Administrador Local");
        usuario.setEmail(EMAIL);
        usuario.setMatricula("LOCAL-ADMIN-" + UUID.randomUUID());
        usuario.setPerfil(PerfilAcesso.GERENTE_OPERACOES);
        usuario.setAtivo(true);
        usuario.setSenhaHash(encoder.encode("Admin@Power2026"));
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            // Uma segunda inicialização pode ter criado a conta entre a consulta e a inserção.
            if (usuarios.existsByEmail(EMAIL)) {
                return;
            }
            throw ex;
        }
        LOGGER.info("Conta de administração local criada para {}. Provisionamento local habilitado explicitamente.", EMAIL);
    }
}
