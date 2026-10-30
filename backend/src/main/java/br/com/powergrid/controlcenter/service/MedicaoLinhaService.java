package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;
import java.time.Instant;
import java.time.ZoneOffset;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaRequestDTO;
import br.com.powergrid.controlcenter.dto.MedicaoLinhaResponseDTO;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.model.MedicaoLinha;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import br.com.powergrid.controlcenter.repository.LinhaTransmissaoRepository;
import br.com.powergrid.controlcenter.repository.MedicaoLinhaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MedicaoLinhaService {
    private final MedicaoLinhaRepository repository;
    private final FonteMedicaoRepository fontes;
    private final LinhaTransmissaoRepository linhas;
    private final Clock clock;
    private final long toleranciaSegundos;

    public MedicaoLinhaService(MedicaoLinhaRepository repository, FonteMedicaoRepository fontes,
            LinhaTransmissaoRepository linhas, Clock clock,
            @Value("${medicoes.tolerancia-futura-segundos:60}") long toleranciaSegundos) {
        if (toleranciaSegundos < 0) throw new IllegalArgumentException("Tolerância temporal deve ser não negativa.");
        this.repository = repository;
        this.fontes = fontes;
        this.linhas = linhas;
        this.clock = clock;
        this.toleranciaSegundos = toleranciaSegundos;
    }

    @Transactional
    public MedicaoLinhaResponseDTO ingerir(Long linhaId, MedicaoLinhaRequestDTO request) {
        if (!linhas.existsById(linhaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha de transmissão não encontrada.");
        }
        FonteMedicao fonte = fontes.findById(request.fonteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fonte de medição não encontrada."));
        if (!fonte.getLinha().getId().equals(linhaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A fonte não pertence à linha informada.");
        }
        if (request.tensaoKv() == null && request.correnteA() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos uma grandeza.");
        }
        if ((request.tensaoKv() != null && request.tensaoKv().signum() < 0)
                || (request.correnteA() != null && request.correnteA().signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "As grandezas não podem ser negativas.");
        }
        Instant agora = Instant.now(clock);
        if (request.medidoEm().toInstant().isAfter(agora.plusSeconds(toleranciaSegundos))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Instante da medição excede a tolerância futura.");
        }
        MedicaoLinha medicao = new MedicaoLinha();
        medicao.setFonte(fonte);
        medicao.setMedidoEm(request.medidoEm().withOffsetSameInstant(ZoneOffset.UTC));
        medicao.setRecebidoEm(agora.atOffset(ZoneOffset.UTC));
        medicao.setTensaoKv(request.tensaoKv());
        medicao.setCorrenteA(request.correnteA());
        repository.save(medicao);
        return new MedicaoLinhaResponseDTO(medicao.getId(), linhaId, fonte.getId(), fonte.getOrigem(),
                medicao.getMedidoEm(), medicao.getRecebidoEm(), medicao.getTensaoKv(), medicao.getCorrenteA());
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<MedicaoLinhaResponseDTO> listar(Long linhaId, MultiValueMap<String, String> params) {
        if (params.containsKey("sort")) throw invalido("Ordenação personalizada não é permitida.");
        int page = inteiro(params, "page", 0);
        int size = inteiro(params, "size", 20);
        if (page < 0 || size < 1 || size > 100) throw invalido("Página deve ser não negativa e tamanho entre 1 e 100.");
        OffsetDateTime inicio = instante(params, "dataInicio");
        OffsetDateTime fim = instante(params, "dataFim");
        if (inicio != null && fim != null && !inicio.toInstant().isBefore(fim.toInstant())) {
            throw invalido("dataInicio deve ser anterior a dataFim.");
        }
        if (!linhas.existsById(linhaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha de transmissão não encontrada.");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "medidoEm", "id"));
        return PageResponseDTO.from(repository.buscarPorLinhaEPeriodo(linhaId, inicio, fim, pageable)
                .map(m -> new MedicaoLinhaResponseDTO(m.getId(), linhaId, m.getFonte().getId(),
                        m.getFonte().getOrigem(), m.getMedidoEm().withOffsetSameInstant(ZoneOffset.UTC),
                        m.getRecebidoEm().withOffsetSameInstant(ZoneOffset.UTC), m.getTensaoKv(), m.getCorrenteA())));
    }

    private String parametro(MultiValueMap<String, String> params, String nome) {
        if (!params.containsKey(nome)) return null;
        var values = params.get(nome);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()) {
            throw invalido("Parâmetro inválido: " + nome);
        }
        return values.getFirst();
    }

    private int inteiro(MultiValueMap<String, String> params, String nome, int padrao) {
        String valor = parametro(params, nome);
        if (valor == null) return padrao;
        try {
            if (!valor.matches("[0-9]+")) throw new NumberFormatException();
            return Integer.parseInt(valor);
        } catch (NumberFormatException ex) {
            throw invalido("Parâmetro inteiro inválido: " + nome);
        }
    }

    private OffsetDateTime instante(MultiValueMap<String, String> params, String nome) {
        String valor = parametro(params, nome);
        if (valor == null) return null;
        try {
            return OffsetDateTime.parse(valor).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw invalido("Informe " + nome + " em ISO 8601 com offset.");
        }
    }

    private ResponseStatusException invalido(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
