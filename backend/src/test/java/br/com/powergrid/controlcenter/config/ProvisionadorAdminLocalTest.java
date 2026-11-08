package br.com.powergrid.controlcenter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.powergrid.controlcenter.model.PerfilAcesso;
import br.com.powergrid.controlcenter.model.Usuario;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class ProvisionadorAdminLocalTest {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private ApplicationContextRunner contexto() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ProvisionadorAdminLocal.class)
                .withBean(UsuarioRepository.class, () -> usuarios)
                .withBean(PasswordEncoder.class, () -> encoder);
    }

    @Test
    void ficaDesabilitadoPorPadrao() {
        contexto().run(app -> assertThat(app).doesNotHaveBean(ProvisionadorAdminLocal.class));
        verifyNoInteractions(usuarios);
    }

    @Test
    void perfilProdBloqueiaProvisionamentoMesmoQuandoSolicitado() {
        contexto().withInitializer(app -> app.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("scada.provisionamento.admin-local.enabled=true")
                .run(app -> assertThat(app).doesNotHaveBean(ProvisionadorAdminLocal.class));
        verifyNoInteractions(usuarios);
    }

    @Test
    void criaGerenteAtivoComSenhaEmBcryptQuandoHabilitado() {
        contexto().withPropertyValues("scada.provisionamento.admin-local.enabled=true").run(app -> {
            assertThat(app).hasSingleBean(ProvisionadorAdminLocal.class);
            app.getBean(ProvisionadorAdminLocal.class).run();
        });
        var captura = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).saveAndFlush(captura.capture());
        var usuario = captura.getValue();
        assertThat(usuario.getEmail()).isEqualTo("admin@powergrid.com");
        assertThat(usuario.getPerfil()).isEqualTo(PerfilAcesso.GERENTE_OPERACOES);
        assertThat(usuario.isAtivo()).isTrue();
        assertThat(usuario.getMatricula()).startsWith("LOCAL-ADMIN-").hasSizeLessThanOrEqualTo(64);
        assertThat(usuario.getSenhaHash()).isNotEqualTo("Admin@Power2026");
        assertThat(encoder.matches("Admin@Power2026", usuario.getSenhaHash())).isTrue();
        assertThat(usuario.getCriadoEm()).isNull();
        assertThat(usuario.getCriadoPorUsuarioId()).isNull();
    }

    @Test
    void preservaContaExistenteSemRedefinirSenhaOuPerfil() {
        var encoderEspiao = mock(PasswordEncoder.class);
        when(usuarios.existsByEmail("admin@powergrid.com")).thenReturn(true);
        new ProvisionadorAdminLocal(usuarios, encoderEspiao).run();
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(encoderEspiao);
    }

    @Test
    void aceitaContaCriadaPorInicializacaoConcorrente() {
        when(usuarios.existsByEmail("admin@powergrid.com")).thenReturn(false, true);
        when(usuarios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Concorrência"));
        new ProvisionadorAdminLocal(usuarios, encoder).run();
    }

    @Test
    void propagaFalhaDeBancoQuandoContaNaoFoiCriada() {
        when(usuarios.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Falha de banco"));
        assertThatThrownBy(() -> new ProvisionadorAdminLocal(usuarios, encoder).run())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
