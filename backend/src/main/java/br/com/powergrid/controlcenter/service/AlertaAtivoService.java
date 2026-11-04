package br.com.powergrid.controlcenter.service;

import java.time.ZoneOffset;
import java.util.Set;
import br.com.powergrid.controlcenter.dto.AlertaAtivoResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.mapper.AlertaDashboardMapper;
import br.com.powergrid.controlcenter.model.*;
import br.com.powergrid.controlcenter.repository.AlertaAtivoRepository;
import br.com.powergrid.controlcenter.repository.FiltroAlertasAtivos;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AlertaAtivoService {
    private static final Set<String> PARAMETROS = Set.of("page", "size", "dataInicio", "dataFim", "tipoAlerta",
            "criticidade", "escopo", "ativoId", "fonteId", "tipoAtivo", "subestacaoId");
    private final AlertaAtivoRepository repository;

    public AlertaAtivoService(AlertaAtivoRepository repository) { this.repository = repository; }

    public PageResponseDTO<AlertaAtivoResponseDTO> listar(MultiValueMap<String, String> params) {
        for (var nome : params.keySet()) {
            if (!PARAMETROS.contains(nome)) throw invalido("Parâmetro não permitido: " + nome);
            ParametrosConsultaMedicao.parametro(params, nome);
        }
        var consulta = ParametrosConsultaMedicao.from(params, "ocorridoEm");
        var filtro = new FiltroAlertasAtivos(enumeracao(params, "tipoAlerta", TipoAlertaDashboard.class),
                enumeracao(params, "criticidade", Criticidade.class), enumeracao(params, "escopo", EscopoAlerta.class),
                identificador(params, "ativoId"), identificador(params, "fonteId"),
                enumeracao(params, "tipoAtivo", TipoAtivoAlerta.class), identificador(params, "subestacaoId"),
                consulta.inicio(), consulta.fim());
        return listar(filtro, PageRequest.of(consulta.pageable().getPageNumber(), consulta.pageable().getPageSize()));
    }

    public PageResponseDTO<AlertaAtivoResponseDTO> listar(FiltroAlertasAtivos filtro, Pageable pageable) {
        if (filtro == null) throw invalido("Filtros de consulta obrigatórios.");
        if (pageable == null || pageable.isUnpaged() || pageable.getPageNumber() < 0
                || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw invalido("Página deve ser não negativa e tamanho entre 1 e 100.");
        }
        if (pageable.getSort().isSorted()) throw invalido("Ordenação personalizada não é permitida.");
        if (filtro.dataInicio() != null && filtro.dataFim() != null
                && !filtro.dataInicio().toInstant().isBefore(filtro.dataFim().toInstant())) {
            throw invalido("dataInicio deve ser anterior a dataFim.");
        }
        validarId(filtro.ativoId(), "ativoId"); validarId(filtro.fonteId(), "fonteId");
        validarId(filtro.subestacaoId(), "subestacaoId");
        var normalizado = new FiltroAlertasAtivos(filtro.tipoAlerta(), filtro.criticidade(), filtro.escopo(),
                filtro.ativoId(), filtro.fonteId(), filtro.tipoAtivo(), filtro.subestacaoId(),
                filtro.dataInicio() == null ? null : filtro.dataInicio().withOffsetSameInstant(ZoneOffset.UTC),
                filtro.dataFim() == null ? null : filtro.dataFim().withOffsetSameInstant(ZoneOffset.UTC));
        var pagina = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponseDTO.from(repository.buscar(normalizado, pagina).map(AlertaDashboardMapper::toAtivoResponse));
    }

    private <E extends Enum<E>> E enumeracao(MultiValueMap<String, String> params, String nome, Class<E> tipo) {
        var valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try { return Enum.valueOf(tipo, valor); }
        catch (IllegalArgumentException ex) { throw invalido("Valor inválido para " + nome + "."); }
    }

    private Long identificador(MultiValueMap<String, String> params, String nome) {
        var valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            validarId(id, nome);
            return id;
        } catch (NumberFormatException ex) { throw invalido(nome + " deve ser um inteiro positivo válido."); }
    }

    private void validarId(Long valor, String nome) {
        if (valor != null && valor <= 0) throw invalido(nome + " deve ser um inteiro positivo válido.");
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
