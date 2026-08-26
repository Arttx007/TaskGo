package com.example.Estrela.Controller;

import com.example.Estrela.DTO.ResultadoBuscaServico;
import com.example.Estrela.DTO.ServicoOfertadoRequest;
import com.example.Estrela.DTO.ServicoOfertadoResponse;
import com.example.Estrela.Entity.ServicoOfertado;
import com.example.Estrela.Service.ServicoOfertadoService;
import com.example.Estrela.security.TaskGoUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catálogo de serviços publicados por prestadores (US-02) e busca por geolocalização (US-03).
 */
@RestController
@RequestMapping("/servicos-ofertados")
public class ServicoOfertadoController {

    private final ServicoOfertadoService servicoOfertadoService;

    public ServicoOfertadoController(ServicoOfertadoService servicoOfertadoService) {
        this.servicoOfertadoService = servicoOfertadoService;
    }

    /**
     * @throws com.example.Estrela.exception.KycPendenteException se o prestador não tiver KYC aprovado (HTTP 422)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServicoOfertadoResponse criar(@Valid @RequestBody ServicoOfertadoRequest request,
                                          @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(servicoOfertadoService.criar(usuario.getId(), request));
    }

    @GetMapping("/meus")
    public List<ServicoOfertadoResponse> listarMeus(@AuthenticationPrincipal TaskGoUserDetails usuario) {
        return servicoOfertadoService.listarMeus(usuario.getId()).stream().map(this::paraResposta).toList();
    }

    @PutMapping("/{id}")
    public ServicoOfertadoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ServicoOfertadoRequest request,
                                              @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(servicoOfertadoService.atualizar(id, usuario.getId(), request));
    }

    @PutMapping("/{id}/ativo")
    public ServicoOfertadoResponse alternarAtivo(@PathVariable Long id, @RequestParam boolean ativo,
                                                  @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return paraResposta(servicoOfertadoService.alternarAtivo(id, usuario.getId(), ativo));
    }

    @DeleteMapping("/{id}")
    public void excluir(@PathVariable Long id, @AuthenticationPrincipal TaskGoUserDetails usuario) {
        servicoOfertadoService.excluir(id, usuario.getId());
    }

    /**
     * Busca serviços ativos por proximidade (US-03). Sem {@code lat}/{@code lon}, cai no fallback
     * por {@code cidade}.
     */
    @GetMapping("/buscar")
    public ResultadoBuscaServico buscar(@RequestParam String categoria,
                                         @RequestParam(required = false) Double lat,
                                         @RequestParam(required = false) Double lon,
                                         @RequestParam(required = false) Double raioKm,
                                         @RequestParam(required = false) String cidade) {
        return servicoOfertadoService.buscar(categoria, lat, lon, raioKm, cidade);
    }

    private ServicoOfertadoResponse paraResposta(ServicoOfertado servico) {
        return new ServicoOfertadoResponse(servico.getId(), servico.getPrestador().getIdPrestador(),
                servico.getCategoria(), servico.getDescricao(), servico.getPreco(), servico.getStatus());
    }
}
