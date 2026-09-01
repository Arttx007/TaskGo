package com.example.Estrela.Controller;

import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.repository.FatoServicoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/relatorio")
public class RelatorioController {

    private final FatoServicoRepository repository;

    public RelatorioController(FatoServicoRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> relatorio() {

        Map<String, Object> dados = new HashMap<>();

        dados.put("totalServicos", repository.count());

        // "agendados" mantém o nome do campo de resposta por compatibilidade; RN02 renomeou o status
        // subjacente de AGENDADO para ACEITO (ver V5__fato_servico_status_enum_and_fk.sql).
        dados.put("agendados", repository.countByStatus(StatusSolicitacao.ACEITO));
        dados.put("cancelados", repository.countByStatus(StatusSolicitacao.CANCELADO));
        dados.put("concluidos", repository.countByStatus(StatusSolicitacao.CONCLUIDO));

        // Estados que existiam antes de EM_ANDAMENTO entrar na RN02 e não eram contados por
        // bucket algum. Sem eles a soma dos buckets não fecha com totalServicos, e uma
        // solicitação em atendimento simplesmente desaparecia do relatório.
        dados.put("emAndamento", repository.countByStatus(StatusSolicitacao.EM_ANDAMENTO));
        dados.put("solicitados", repository.countByStatus(StatusSolicitacao.SOLICITADO));
        dados.put("recusados", repository.countByStatus(StatusSolicitacao.RECUSADO));
        dados.put("avaliados", repository.countByStatus(StatusSolicitacao.AVALIADO));

        return dados;
    }
}