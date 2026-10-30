package br.com.powergrid.controlcenter.service;

import br.com.powergrid.controlcenter.dto.FonteMedicaoRequestDTO;
import br.com.powergrid.controlcenter.dto.FonteMedicaoResponseDTO;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import br.com.powergrid.controlcenter.repository.LinhaTransmissaoRepository;
import org.springframework.http.HttpStatus;
import br.com.powergrid.controlcenter.repository.SubestacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FonteMedicaoService {
    private final FonteMedicaoRepository fontes;
    private final LinhaTransmissaoRepository linhas;

    private final SubestacaoRepository subestacoes;

    public FonteMedicaoService(FonteMedicaoRepository fontes, LinhaTransmissaoRepository linhas, SubestacaoRepository subestacoes) {
        this.fontes = fontes;
        this.linhas = linhas;
        this.subestacoes = subestacoes;
    }

    @Transactional
    public FonteMedicaoResponseDTO criar(Long linhaId, FonteMedicaoRequestDTO request) {
        var linha = linhas.findById(linhaId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha de transmissão não encontrada."));
        if (fontes.existsByIdentificador(request.identificador())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identificador de fonte já cadastrado.");
        }
        FonteMedicao fonte = new FonteMedicao();
        fonte.setIdentificador(request.identificador());
        fonte.setOrigem(request.origem());
        fonte.setAtivo(linha);
        if (request.subestacaoReferenciaId() != null) {
            var referencia = subestacoes.findById(request.subestacaoReferenciaId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Subestação de referência não encontrada."));
            if (!referencia.getId().equals(linha.getSubestacaoOrigem().getId())
                    && !referencia.getId().equals(linha.getSubestacaoDestino().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Referência deve ser uma extremidade da linha.");
            }
            fonte.setSubestacaoReferencia(referencia);
        }
        fontes.saveAndFlush(fonte);
        return new FonteMedicaoResponseDTO(fonte.getId(), fonte.getIdentificador(), linhaId, fonte.getOrigem(),
                fonte.getSubestacaoReferencia() == null ? null : fonte.getSubestacaoReferencia().getId());
    }
}
