package com.example.Estrela.Controller;

import com.example.Estrela.DTO.AvaliacaoPublicaResponse;
import com.example.Estrela.Service.AvaliacaoPublicaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Leitura pública das avaliações, para a prova social do site (US-09).
 *
 * <p>Fica sob prefixo próprio, e não sob {@code /servicos}, de propósito: aquele prefixo é território
 * autenticado e validado por dono do recurso, e pendurar uma rota pública lá convidaria a erro de
 * configuração no {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/avaliacoes")
public class AvaliacaoPublicaController {

    private final AvaliacaoPublicaService avaliacaoPublicaService;

    public AvaliacaoPublicaController(AvaliacaoPublicaService avaliacaoPublicaService) {
        this.avaliacaoPublicaService = avaliacaoPublicaService;
    }

    /**
     * Avaliações mais recentes exibíveis publicamente, identificando quem avaliou apenas pelo
     * primeiro nome. Rota pública.
     *
     * <p>Lista vazia é resposta bem-sucedida: não há avaliação com comentário a exibir.
     *
     * @param limite quantidade desejada, opcional; valor acima do teto aceito é truncado, nunca recusado
     * @return avaliações da mais recente para a mais antiga
     */
    @GetMapping("/recentes")
    public List<AvaliacaoPublicaResponse> recentes(@RequestParam(required = false) Integer limite) {
        return avaliacaoPublicaService.listarRecentes(limite);
    }
}
