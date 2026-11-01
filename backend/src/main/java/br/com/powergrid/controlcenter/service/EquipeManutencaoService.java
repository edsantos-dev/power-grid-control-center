package br.com.powergrid.controlcenter.service;

import java.util.Set;
import java.util.List;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import br.com.powergrid.controlcenter.dto.IntegranteResponseDTO;
import br.com.powergrid.controlcenter.model.VinculoEquipe;
import br.com.powergrid.controlcenter.repository.UsuarioRepository;
import br.com.powergrid.controlcenter.repository.VinculoEquipeRepository;
import org.springframework.data.domain.PageImpl;
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

    private final VinculoEquipeRepository vinculos;
    private final UsuarioRepository usuarios;
    private final Clock clock;

    public EquipeManutencaoService(EquipeManutencaoRepository repository, VinculoEquipeRepository vinculos,
            UsuarioRepository usuarios, Clock clock) {
        this.repository = repository;
        this.vinculos = vinculos;
        this.usuarios = usuarios;
        this.clock = clock;
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
        var equipe = bloquear(id);
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

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'TECNICO_MANUTENCAO')")
    public InclusaoIntegrante adicionarIntegrante(Long equipeId, Long usuarioId) {
        var equipe = bloquear(equipeId);
        var usuario = usuarios.findById(usuarioId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        var existente = vinculos.findFirstByEquipeIdAndUsuarioIdAndFimVigenciaIsNullOrderByIdAsc(equipeId, usuarioId);
        if (existente.isPresent()) return new InclusaoIntegrante(integrante(existente.get()), false);
        var vinculo = new VinculoEquipe();
        vinculo.setEquipe(equipe);
        vinculo.setUsuario(usuario);
        vinculo.setInicioVigencia(agora());
        return new InclusaoIntegrante(integrante(vinculos.save(vinculo)), true);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'TECNICO_MANUTENCAO')")
    public void removerIntegrante(Long equipeId, Long usuarioId) {
        var equipe = bloquear(equipeId);
        if (!usuarios.existsById(usuarioId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        var existente = vinculos.findFirstByEquipeIdAndUsuarioIdAndFimVigenciaIsNullOrderByIdAsc(equipeId, usuarioId);
        if (existente.isEmpty()) return;
        if (exigeIntegrante(equipe.getSituacao()) && vinculos.countByEquipeIdAndFimVigenciaIsNull(equipeId) <= 1) {
            throw conflito("Coloque a equipe FORA_DE_SERVICO antes de remover o último integrante ativo.");
        }
        var vinculo = existente.get();
        var fim = agora();
        if (vinculo.getInicioVigencia() != null && fim.isBefore(vinculo.getInicioVigencia())) {
            throw conflito("O fim da vigência não pode ser anterior ao início.");
        }
        vinculo.setFimVigencia(fim);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('GERENTE_OPERACOES', 'TECNICO_MANUTENCAO')")
    public EquipeResponseDTO atualizarSituacao(Long equipeId, SituacaoEquipe situacao) {
        var equipe = bloquear(equipeId);
        if (situacao == null) throw invalido("Situação obrigatória.");
        if (exigeIntegrante(situacao) && vinculos.countByEquipeIdAndFimVigenciaIsNull(equipeId) == 0) {
            throw conflito("A equipe precisa de ao menos um integrante ativo para essa situação.");
        }
        equipe.setSituacao(situacao);
        return resposta(equipe);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<IntegranteResponseDTO> listarIntegrantes(Long equipeId, MultiValueMap<String, String> params) {
        buscar(equipeId);
        for (String parametro : params.keySet()) {
            if (!Set.of("page", "size", "sort").contains(parametro)) throw invalido("Parâmetro não suportado: " + parametro);
        }
        var consulta = ParametrosConsultaMedicao.from(params, "id");
        var pageable = PageRequest.of(consulta.pageable().getPageNumber(), consulta.pageable().getPageSize(), Sort.by("id"));
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            long total = vinculos.countByEquipeIdAndFimVigenciaIsNull(equipeId);
            if (pageable.getOffset() >= total) return PageResponseDTO.from(new PageImpl<IntegranteResponseDTO>(List.of(), pageable, total));
        }
        return PageResponseDTO.from(vinculos.findByEquipeIdAndFimVigenciaIsNull(equipeId, pageable).map(this::integrante));
    }

    public record InclusaoIntegrante(IntegranteResponseDTO integrante, boolean criado) { }

    private boolean exigeIntegrante(SituacaoEquipe situacao) {
        return situacao == SituacaoEquipe.DISPONIVEL || situacao == SituacaoEquipe.EM_ATENDIMENTO;
    }

    private OffsetDateTime agora() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private IntegranteResponseDTO integrante(VinculoEquipe vinculo) {
        var usuario = vinculo.getUsuario();
        return new IntegranteResponseDTO(vinculo.getId(), vinculo.getEquipe().getId(), usuario.getId(), usuario.getNome(),
                usuario.getPerfil(), utc(vinculo.getInicioVigencia()), utc(vinculo.getFimVigencia()));
    }

    private OffsetDateTime utc(OffsetDateTime instante) {
        return instante == null ? null : instante.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private EquipeManutencao bloquear(Long id) {
        return repository.buscarPorIdParaAlteracao(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipe não encontrada."));
    }

    private ResponseStatusException conflito(String mensagem) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensagem);
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
