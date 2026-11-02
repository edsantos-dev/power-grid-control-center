package br.com.powergrid.controlcenter.geografia;

import br.com.powergrid.controlcenter.model.*;

public enum TipoAtivoMapa {
    USINA(UsinaGeradora.class),
    SUBESTACAO(Subestacao.class),
    TRANSFORMADOR(Transformador.class),
    LINHA(LinhaTransmissao.class);

    private final Class<? extends AtivoRede> classe;

    TipoAtivoMapa(Class<? extends AtivoRede> classe) { this.classe = classe; }
    public Class<? extends AtivoRede> classe() { return classe; }
}
