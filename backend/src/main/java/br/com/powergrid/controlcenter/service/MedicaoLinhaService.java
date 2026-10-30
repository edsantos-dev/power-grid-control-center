package br.com.powergrid.controlcenter.service;

import java.time.Clock;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import br.com.powergrid.controlcenter.model.SituacaoFatorPotencia;
import br.com.powergrid.controlcenter.dto.PageResponseDTO;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        if (!fonte.getAtivo().getId().equals(linhaId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A fonte não pertence à linha informada.");
        }
        if (request.tensaoKv() == null && request.correnteA() == null
                && request.potenciaAtivaMw() == null && request.potenciaReativaMvar() == null
                && request.frequenciaHz() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos uma grandeza.");
        }
        if ((request.tensaoKv() != null && request.tensaoKv().signum() < 0)
                || (request.correnteA() != null && request.correnteA().signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "As grandezas não podem ser negativas.");
        }
        if (request.potenciaAtivaMw() != null || request.potenciaReativaMvar() != null) {
            var referencia = fonte.getSubestacaoReferencia();
            var linha = linhas.getReferenceById(linhaId);
            if (referencia == null || (!referencia.getId().equals(linha.getSubestacaoOrigem().getId())
                    && !referencia.getId().equals(linha.getSubestacaoDestino().getId()))) {
                throw invalido("Ingestão de potência exige fonte com terminal de referência válido.");
            }
        }
        if (request.frequenciaHz() != null && request.frequenciaHz().signum() <= 0) {
            throw invalido("Frequência deve ser estritamente positiva.");
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
        medicao.setPotenciaAtivaMw(request.potenciaAtivaMw());
        medicao.setPotenciaReativaMvar(request.potenciaReativaMvar());
        medicao.setFrequenciaHz(request.frequenciaHz());
        try {
            repository.saveAndFlush(medicao);
        } catch (DataIntegrityViolationException ex) {
            throw invalido("Medição incompatível com as restrições de integridade.");
        }
        return toResponse(medicao);
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<MedicaoLinhaResponseDTO> listar(Long linhaId, MultiValueMap<String, String> params) {
        var consulta = br.com.powergrid.controlcenter.util.ParametrosConsultaMedicao.from(params);
        if (!linhas.existsById(linhaId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha de transmissão não encontrada.");
        }
        return PageResponseDTO.from(repository.buscarPorLinhaEPeriodo(linhaId, consulta.inicio(), consulta.fim(), consulta.pageable())
                .map(this::toResponse));
    }


    private MedicaoLinhaResponseDTO toResponse(MedicaoLinha medicao) {
        BigDecimal p = medicao.getPotenciaAtivaMw();
        BigDecimal q = medicao.getPotenciaReativaMvar();
        BigDecimal fp = null;
        SituacaoFatorPotencia situacao = SituacaoFatorPotencia.DADOS_INSUFICIENTES;
        if (p != null && q != null) {
            BigDecimal quadradoAparente = p.multiply(p).add(q.multiply(q));
            if (quadradoAparente.signum() == 0) {
                situacao = SituacaoFatorPotencia.POTENCIA_APARENTE_NULA;
            } else {
                BigDecimal aparente = quadradoAparente.sqrt(MathContext.DECIMAL128);
                fp = p.abs().divide(aparente, 6, RoundingMode.HALF_EVEN);
                situacao = SituacaoFatorPotencia.CALCULADO;
            }
        }
        FonteMedicao fonte = medicao.getFonte();
        return new MedicaoLinhaResponseDTO(medicao.getId(), fonte.getAtivo().getId(), fonte.getId(), fonte.getOrigem(),
                medicao.getMedidoEm().withOffsetSameInstant(ZoneOffset.UTC),
                medicao.getRecebidoEm().withOffsetSameInstant(ZoneOffset.UTC), medicao.getTensaoKv(), medicao.getCorrenteA(),
                p, q, fonte.getSubestacaoReferencia() == null ? null : fonte.getSubestacaoReferencia().getId(),
                medicao.getFrequenciaHz(), fp, situacao);
    }

    private ResponseStatusException invalido(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
