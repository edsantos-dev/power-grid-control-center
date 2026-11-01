package br.com.powergrid.controlcenter.service;

import java.util.Set;
import br.com.powergrid.controlcenter.dto.*;
import br.com.powergrid.controlcenter.model.UsinaGeradora;
import br.com.powergrid.controlcenter.model.TipoGeracao;
import br.com.powergrid.controlcenter.model.AtivoRede.EstadoOperacional;
import br.com.powergrid.controlcenter.repository.UsinaGeradoraRepository;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UsinaGeradoraService {
    private static final Set<String> PARAMETROS = Set.of("page", "size", "sort", "nome", "tipoGeracao", "estadoOperacional", "subestacaoId");
    private final UsinaGeradoraRepository repository;
    private final SubestacaoRepository subestacoes;

    public UsinaGeradoraService(UsinaGeradoraRepository repository, SubestacaoRepository subestacoes) {
        this.repository = repository;
        this.subestacoes = subestacoes;
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    public UsinaResponseDTO criar(UsinaRequestDTO request) {
        var usina = new UsinaGeradora();
        preencher(usina, request);
        return resposta(repository.save(usina));
    }

    @Transactional
    @PreAuthorize("hasRole('GERENTE_OPERACOES')")
    public UsinaResponseDTO atualizar(Long id, UsinaRequestDTO request) {
        var usina = buscar(id);
        preencher(usina, request);
        return resposta(repository.save(usina));
    }

    @Transactional(readOnly = true)
    public UsinaResponseDTO obterPorId(Long id) {
        return resposta(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<UsinaResponseDTO> listarUsinas(MultiValueMap<String, String> params) {
        for (String parametro : params.keySet()) {
            if (!PARAMETROS.contains(parametro)) throw invalido("Parâmetro não suportado: " + parametro);
        }
        var consulta = ParametrosConsultaMedicao.from(params, "id");
        String nome = ParametrosConsultaMedicao.parametro(params, "nome");
        var tipo = enumerado(params, "tipoGeracao", TipoGeracao.class);
        var estado = enumerado(params, "estadoOperacional", EstadoOperacional.class);
        var pageable = PageRequest.of(consulta.pageable().getPageNumber(), consulta.pageable().getPageSize(), Sort.by("id"));
        return PageResponseDTO.from(repository.buscarFiltradas(nome, tipo, estado, subestacaoId(params), pageable).map(this::resposta));
    }

    private UsinaGeradora buscar(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usina não encontrada."));
    }

    private void preencher(UsinaGeradora usina, UsinaRequestDTO request) {
        var subestacao = subestacoes.findById(request.subestacaoId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Subestação não encontrada."));
        usina.setNome(request.nome());
        usina.setLatitude(request.latitude());
        usina.setLongitude(request.longitude());
        usina.setTipoGeracao(request.tipoGeracao());
        usina.setCapacidadeMaximaMw(request.capacidadeMaximaMw());
        usina.setSubestacao(subestacao);
        usina.setDataInstalacao(request.dataInstalacao());
        usina.setEstadoOperacional(request.estadoOperacional() == null ? EstadoOperacional.OPERACIONAL : request.estadoOperacional());
    }

    private UsinaResponseDTO resposta(UsinaGeradora usina) {
        return new UsinaResponseDTO(usina.getId(), usina.getNome(), usina.getLatitude(), usina.getLongitude(),
                usina.getTipoGeracao(), usina.getCapacidadeMaximaMw(), usina.getSubestacao().getId(),
                usina.getDataInstalacao(), usina.getEstadoOperacional());
    }

    private <E extends Enum<E>> E enumerado(MultiValueMap<String, String> params, String nome, Class<E> tipo) {
        String valor = ParametrosConsultaMedicao.parametro(params, nome);
        if (valor == null) return null;
        try { return Enum.valueOf(tipo, valor); }
        catch (IllegalArgumentException ex) { throw invalido("Valor inválido para " + nome + "."); }
    }

    private Long subestacaoId(MultiValueMap<String, String> params) {
        String valor = ParametrosConsultaMedicao.parametro(params, "subestacaoId");
        if (valor == null) return null;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(valor);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) { throw invalido("subestacaoId deve ser um inteiro positivo válido."); }
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
