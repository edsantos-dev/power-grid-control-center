package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.IndicadoresOperacionaisResponseDTO;
import br.com.powergrid.controlcenter.dto.IndicadoresOperacionaisResponseDTO.*;
import br.com.powergrid.controlcenter.model.SituacaoDisponibilidade;
import br.com.powergrid.controlcenter.model.TipoAtivoIndicador;
import br.com.powergrid.controlcenter.repository.*;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IndicadoresService {
    private static final Set<String> PARAMETROS = Set.of("dataInicio", "dataFim", "ativoId", "tipos", "subestacaoId");
    private final IndicadoresRepository indicadores;
    private final AtivoRedeRepository ativos;
    private final SubestacaoRepository subestacoes;
    private final Clock clock;

    public IndicadoresService(IndicadoresRepository indicadores, AtivoRedeRepository ativos,
            SubestacaoRepository subestacoes, Clock clock) {
        this.indicadores = indicadores; this.ativos = ativos; this.subestacoes = subestacoes; this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public IndicadoresOperacionaisResponseDTO calcular(MultiValueMap<String, String> params) {
        for (String nome : params.keySet()) if (!PARAMETROS.contains(nome)) throw invalido("Parâmetro não suportado: " + nome);
        var periodo = ParametrosConsultaMedicao.from(params);
        if (periodo.inicio() == null || periodo.fim() == null) throw invalido("dataInicio e dataFim são obrigatórios, com fuso.");
        var agora = clock.instant();
        if (periodo.fim().toInstant().isAfter(agora)) throw invalido("dataFim não pode ser futura.");
        Long ativoId = id(params, "ativoId"), subestacaoId = id(params, "subestacaoId");
        var tipos = tipos(params);
        if (ativoId != null && !ativos.existsById(ativoId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ativo não encontrado.");
        if (subestacaoId != null && !subestacoes.existsById(subestacaoId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subestação não encontrada.");
        var inventario = indicadores.consultarInventario(ativoId, tipos, subestacaoId);
        var restabelecimento = indicadores.consultarRestabelecimento(ativoId, tipos, subestacaoId, periodo.inicio(), periodo.fim());
        var tempo = indicadores.consultarTempoInterrompido(ativoId, tipos, subestacaoId, periodo.inicio(), periodo.fim());
        var interrupcoes = indicadores.consultarInterrupcoes(ativoId, tipos, subestacaoId, periodo.inicio(), periodo.fim());
        long totalAtivos = inventario.stream().mapToLong(p -> p.quantidade()).sum();
        var duracao = Duration.between(periodo.inicio().toInstant(), periodo.fim().toInstant());
        var segundos = BigDecimal.valueOf(duracao.getSeconds()).add(BigDecimal.valueOf(duracao.getNano(), 9));
        var observado = segundos.multiply(BigDecimal.valueOf(totalAtivos));
        BigDecimal media = restabelecimento.interrupcoesConcluidas() == 0 ? null
                : restabelecimento.duracaoTotalSegundos().divide(BigDecimal.valueOf(restabelecimento.interrupcoesConcluidas()), 6, RoundingMode.HALF_EVEN);
        BigDecimal disponibilidade = null;
        SituacaoDisponibilidade situacao;
        if (totalAtivos == 0) situacao = SituacaoDisponibilidade.SEM_POPULACAO_ATIVA;
        else if (interrupcoes.registrosInconsistentes() > 0) situacao = SituacaoDisponibilidade.DADOS_INCONSISTENTES;
        else {
            disponibilidade = observado.subtract(tempo.totalSegundos()).multiply(new BigDecimal("100"))
                    .divide(observado, 6, RoundingMode.HALF_EVEN);
            situacao = SituacaoDisponibilidade.COBERTURA_NAO_AFERIDA;
        }
        return new IndicadoresOperacionaisResponseDTO(agora.atOffset(ZoneOffset.UTC), periodo.inicio(), periodo.fim(),
                new FiltrosAplicados(ativoId, tipos.stream().sorted().toList(), subestacaoId), inventario.stream().map(
                    p -> new QuantidadeAtivos(p.tipoAtivo(), p.estadoOperacional(), p.quantidade())).toList(),
                totalAtivos, interrupcoes.iniciadasNoPeriodo(), interrupcoes.sobrepostasAoPeriodo(), interrupcoes.semTerminoFisico(),
                restabelecimento.interrupcoesConcluidas(), interrupcoes.registrosInconsistentes(),
                tempo.totalSegundos().setScale(6, RoundingMode.HALF_EVEN), media, observado.setScale(6, RoundingMode.HALF_EVEN),
                disponibilidade, situacao);
    }

    private EnumSet<TipoAtivoIndicador> tipos(MultiValueMap<String, String> params) {
        String valor = ParametrosConsultaMedicao.parametro(params, "tipos");
        if (valor == null) return EnumSet.allOf(TipoAtivoIndicador.class);
        var resultado = EnumSet.noneOf(TipoAtivoIndicador.class);
        for (String item : valor.split(",", -1)) {
            try { resultado.add(TipoAtivoIndicador.valueOf(item.trim())); }
            catch (IllegalArgumentException ex) { throw invalido("Tipo de ativo inválido: " + item); }
        }
        return resultado;
    }

    private Long id(MultiValueMap<String, String> params, String nome) {
        String valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) { throw invalido(nome + " deve ser um inteiro positivo válido."); }
    }

    private ResponseStatusException invalido(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
}
