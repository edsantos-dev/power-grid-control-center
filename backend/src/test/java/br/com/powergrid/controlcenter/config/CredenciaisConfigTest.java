package br.com.powergrid.controlcenter.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(CredenciaisConfig.class)
class CredenciaisConfigTest {

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void deveDisponibilizarBCryptEValidarSenhaCorreta() {
		String senha = "SenhaTeste123!";
		String hash = passwordEncoder.encode(senha);

		assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
		assertThat(hash).isNotEqualTo(senha);
		assertThat(passwordEncoder.matches(senha, hash)).isTrue();
	}

	@Test
	void deveRejeitarSenhaIncorreta() {
		String hash = passwordEncoder.encode("SenhaTeste123!");

		assertThat(passwordEncoder.matches("OutraSenha123!", hash)).isFalse();
	}

	@Test
	void deveGerarHashesDistintosParaMesmaSenha() {
		String senha = "SenhaTeste123!";
		String primeiroHash = passwordEncoder.encode(senha);
		String segundoHash = passwordEncoder.encode(senha);

		assertThat(primeiroHash).isNotEqualTo(segundoHash);
		assertThat(passwordEncoder.matches(senha, primeiroHash)).isTrue();
		assertThat(passwordEncoder.matches(senha, segundoHash)).isTrue();
	}

}
