package br.com.powergrid.controlcenter.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.powergrid.controlcenter.dto.AmostraSnapshotDTO;
import br.com.powergrid.controlcenter.dto.AtivoSnapshotDTO;
import br.com.powergrid.controlcenter.dto.AtivoSnapshotDTO.Tipo;
import br.com.powergrid.controlcenter.dto.LinhaSnapshotDTO;
import br.com.powergrid.controlcenter.dto.MonitoramentoSnapshotResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.dto.TransformadorSnapshotDTO;
import br.com.powergrid.controlcenter.repository.MonitoramentoSnapshotRepository;
import br.com.powergrid.controlcenter.repository.MonitoramentoSnapshotRepository.Ativo;
import br.com.powergrid.controlcenter.repository.MonitoramentoSnapshotRepository.Grandeza;
import br.com.powergrid.controlcenter.repository.MonitoramentoSnapshotRepository.GrandezaLinha;
import br.com.powergrid.controlcenter.repository.MonitoramentoSnapshotRepository.LeituraTransformador;
import br.com.powergrid.controlcenter.repository.MonitoramentoSnapshotRepository.Referencia;
import br.com.powergrid.controlcenter.util.CalculosTelemetria;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonitoramentoSnapshotService {
    private static final Set<String> PARAMETROS = Set.of("tipos", "ativoId", "subestacaoId", "page", "size", "sort");

    private final MonitoramentoSnapshotRepository repository;
    private final Clock clock;

    public MonitoramentoSnapshotService(MonitoramentoSnapshotRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public MonitoramentoSnapshotResponseDTO consultar(MultiValueMap<String, String> params) {
        if (!PARAMETROS.containsAll(params.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parâmetro de filtro desconhecido.");
        }
        var pagina = ParametrosConsultaMedicao.from(params).pageable();
        var tipos = tipos(ParametrosConsultaMedicao.parametro(params, "tipos"));
        var ativoId = id(params, "ativoId");
        var subestacaoId = id(params, "subestacaoId");
        var consultadoEm = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        var ativos = repository.buscarAtivos(tipos, ativoId, subestacaoId, pagina);

        Map<Long, Map<Grandeza, GrandezaLinha>> grandezas = new HashMap<>();
        var idsLinhas = ids(ativos.ativos(), Tipo.LINHA);
        if (!idsLinhas.isEmpty()) {
            for (var leitura : repository.buscarGrandezasLinha(idsLinhas, consultadoEm)) {
                grandezas.computeIfAbsent(leitura.ativoId(), id -> new EnumMap<>(Grandeza.class))
                        .put(leitura.grandeza(), leitura);
            }
        }

        Map<Long, LeituraTransformador> transformadores = new HashMap<>();
        var idsTransformadores = ids(ativos.ativos(), Tipo.TRANSFORMADOR);
        if (!idsTransformadores.isEmpty()) {
            for (var leitura : repository.buscarLeiturasTransformador(idsTransformadores, consultadoEm)) {
                transformadores.put(leitura.ativoId(), leitura);
            }
        }

        var itens = ativos.ativos().stream().<AtivoSnapshotDTO>map(ativo -> switch (ativo.tipo()) {
            case LINHA -> linha(ativo, grandezas.getOrDefault(ativo.id(), Map.of()));
            case TRANSFORMADOR -> transformador(ativo, transformadores.get(ativo.id()));
        }).toList();
        return new MonitoramentoSnapshotResponseDTO(consultadoEm,
                PageResponseDTO.from(new PageImpl<>(itens, pagina, ativos.total())));
    }

    private List<Long> ids(List<Ativo> ativos, Tipo tipo) {
        return ativos.stream().filter(ativo -> ativo.tipo() == tipo).map(Ativo::id).toList();
    }

    private LinhaSnapshotDTO linha(Ativo ativo, Map<Grandeza, GrandezaLinha> leituras) {
        var par = leituras.get(Grandeza.FATOR);
        var fator = par == null ? CalculosTelemetria.fatorPotencia(null, null)
                : CalculosTelemetria.fatorPotencia(par.valor(), par.reativa());
        var ultima = leituras.values().stream().map(GrandezaLinha::referencia)
                .max(Comparator.comparing(Referencia::medidoEm).thenComparing(Referencia::medicaoId)).orElse(null);
        return new LinhaSnapshotDTO(ativo.id(), ativo.nome(), ativo.tipo(), ativo.subestacaoId(), ativo.subestacaoDestinoId(),
                ultima == null ? null : ultima.origemLeitura(), ultima == null ? null : ultima.terminalReferenciaId(),
                ativo.tensaoOperacao(), amostra(leituras.get(Grandeza.TENSAO)), amostra(leituras.get(Grandeza.CORRENTE)),
                amostra(leituras.get(Grandeza.FREQUENCIA)), amostra(leituras.get(Grandeza.ATIVA)),
                amostra(leituras.get(Grandeza.REATIVA)),
                fator.valor() == null ? null : amostra(fator.valor(), par.referencia()), fator.situacao());
    }

    private TransformadorSnapshotDTO transformador(Ativo ativo, LeituraTransformador leitura) {
        if (leitura == null) {
            return new TransformadorSnapshotDTO(ativo.id(), ativo.nome(), ativo.tipo(), ativo.subestacaoId(),
                    null, ativo.tensaoOperacao(), null, null, null);
        }
        return new TransformadorSnapshotDTO(ativo.id(), ativo.nome(), ativo.tipo(), ativo.subestacaoId(),
                leitura.referencia().origemLeitura(), ativo.tensaoOperacao(), amostra(leitura.potencia(), leitura.referencia()), amostra(leitura.capacidade(), leitura.referencia()),
                amostra(CalculosTelemetria.cargaPercentual(leitura.potencia(), leitura.capacidade()), leitura.referencia()));
    }

    private AmostraSnapshotDTO<BigDecimal> amostra(GrandezaLinha grandeza) {
        return grandeza == null ? null : amostra(grandeza.valor(), grandeza.referencia());
    }

    private AmostraSnapshotDTO<BigDecimal> amostra(BigDecimal valor, Referencia referencia) {
        return new AmostraSnapshotDTO<>(valor, referencia.medidoEm(), referencia.recebidoEm(), referencia.fonteId(),
                referencia.origemLeitura(), referencia.terminalReferenciaId());
    }

    private Set<Tipo> tipos(String filtro) {
        if (filtro == null) {
            return EnumSet.allOf(Tipo.class);
        }
        var tipos = EnumSet.noneOf(Tipo.class);
        try {
            for (String tipo : filtro.split(",", -1)) {
                tipos.add(Tipo.valueOf(tipo.trim()));
            }
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipos permitidos: LINHA e TRANSFORMADOR.");
        }
        return tipos;
    }

    private Long id(MultiValueMap<String, String> params, String nome) {
        String valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) {
            return null;
        }
        try {
            long id = Long.parseLong(valor);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // O mesmo erro cobre valores fora do intervalo de IDs e valores malformados.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, nome + " deve ser um inteiro positivo.");
    }
}
