package br.com.powergrid.controlcenter.util;

import java.util.Locale;

public final class NormalizadorEmail {

	private NormalizadorEmail() {
	}

	public static String normalizar(String email) {
		return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
	}

}
