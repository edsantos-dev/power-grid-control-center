package br.com.powergrid.controlcenter.service;

import br.com.powergrid.controlcenter.dto.FonteMedicaoRequestDTO;
import br.com.powergrid.controlcenter.dto.FonteMedicaoResponseDTO;
import br.com.powergrid.controlcenter.model.FonteMedicao;
import br.com.powergrid.controlcenter.repository.FonteMedicaoRepository;
import br.com.powergrid.controlcenter.repository.LinhaTransmissaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FonteMedicaoService {
    private final FonteMedicaoRepository fontes;
    private final LinhaTransmissaoRepository linhas;

    public FonteMedicaoService(FonteMedicaoRepository fontes, LinhaTransmissaoRepository linhas) {
        this.fontes = fontes;
        this.linhas = linhas;
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
        fonte.setLinha(linha);
        fontes.saveAndFlush(fonte);
        return new FonteMedicaoResponseDTO(fonte.getId(), fonte.getIdentificador(), linhaId, fonte.getOrigem());
    }
}
