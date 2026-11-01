package br.com.powergrid.controlcenter.service;

import java.util.Set;
import br.com.powergrid.controlcenter.dto.EquipeRequestDTO;
import br.com.powergrid.controlcenter.dto.EquipeResponseDTO;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import br.com.powergrid.controlcenter.model.EquipeManutencao;
import br.com.powergrid.controlcenter.model.SituacaoEquipe;
import br.com.powergrid.controlcenter.repository.EquipeManutencaoRepository;
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
public class EquipeManutencaoService {
    private static final Set<String> PARAMETROS = Set.of("page", "size", "nome", "situacao", "sort");
    private final EquipeManutencaoRepository repository;

    public EquipeManutencaoService(EquipeManutencaoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'TECNICO_MANUTENCAO')")
    public EquipeResponseDTO criarEquipe(EquipeRequestDTO request) {
        var equipe = new EquipeManutencao();
        preencher(equipe, request);
        equipe.setSituacao(SituacaoEquipe.FORA_DE_SERVICO);
        return resposta(repository.save(equipe));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'TECNICO_MANUTENCAO')")
    public EquipeResponseDTO atualizarEquipe(Long id, EquipeRequestDTO request) {
        var equipe = buscar(id);
        preencher(equipe, request);
        return resposta(repository.save(equipe));
    }

    @Transactional(readOnly = true)
    public EquipeResponseDTO obterPorId(Long id) {
        return resposta(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<EquipeResponseDTO> listarEquipes(MultiValueMap<String, String> params) {
        for (String parametro : params.keySet()) {
            if (!PARAMETROS.contains(parametro)) throw invalido("Parâmetro não suportado: " + parametro);
        }
        var consulta = ParametrosConsultaMedicao.from(params, "id");
        String nome = ParametrosConsultaMedicao.parametro(params, "nome");
        String valorSituacao = ParametrosConsultaMedicao.parametro(params, "situacao");
        SituacaoEquipe situacao = null;
        if (valorSituacao != null) {
            try { situacao = SituacaoEquipe.valueOf(valorSituacao); }
            catch (IllegalArgumentException ex) { throw invalido("Situação de equipe inválida."); }
        }
        var pageable = PageRequest.of(consulta.pageable().getPageNumber(), consulta.pageable().getPageSize(), Sort.by("id").ascending());
        return PageResponseDTO.from(repository.buscarFiltradas(nome, situacao, pageable).map(this::resposta));
    }

    private EquipeManutencao buscar(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada."));
    }

    private void preencher(EquipeManutencao equipe, EquipeRequestDTO request) {
        equipe.setNome(request.nome());
        equipe.setResponsabilidades(request.responsabilidades());
        equipe.setEspecialidades(request.especialidades());
    }

    private EquipeResponseDTO resposta(EquipeManutencao equipe) {
        return new EquipeResponseDTO(equipe.getId(), equipe.getNome(), equipe.getResponsabilidades(),
                equipe.getEspecialidades(), equipe.getSituacao());
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
