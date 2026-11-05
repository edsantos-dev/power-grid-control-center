package br.com.powergrid.controlcenter.controller;

import br.com.powergrid.controlcenter.dto.MonitoramentoSnapshotResponseDTO;
import br.com.powergrid.controlcenter.service.MonitoramentoSnapshotService;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoramento")
public class MonitoramentoSnapshotController {
    private final MonitoramentoSnapshotService service;

    public MonitoramentoSnapshotController(MonitoramentoSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/snapshot")
    public MonitoramentoSnapshotResponseDTO consultar(@RequestParam MultiValueMap<String, String> params) {
        return service.consultar(params);
    }
}
