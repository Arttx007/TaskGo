package com.example.Estrela.Controller;

import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.repository.FatoServicoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/relatorio")
public class RelatorioController {

    @Autowired
    private FatoServicoRepository repository;

    @GetMapping
    public Map<String, Object> relatorio() {

        Map<String, Object> dados = new HashMap<>();

        dados.put("totalServicos", repository.count());

        // "agendados" mantém o nome do campo de resposta por compatibilidade; RN02 renomeou o status
        // subjacente de AGENDADO para ACEITO (ver V5__fato_servico_status_enum_and_fk.sql).
        dados.put("agendados", repository.countByStatus(StatusSolicitacao.ACEITO));
        dados.put("cancelados", repository.countByStatus(StatusSolicitacao.CANCELADO));
        dados.put("concluidos", repository.countByStatus(StatusSolicitacao.CONCLUIDO));

        return dados;
    }
}