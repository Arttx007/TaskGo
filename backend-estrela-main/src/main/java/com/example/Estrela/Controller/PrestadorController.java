package com.example.Estrela.Controller;

import com.example.Estrela.DTO.*;
import com.example.Estrela.Service.PrestadorService;
import com.example.Estrela.Service.SaqueService;
import com.example.Estrela.security.TaskGoUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Cadastro, verificação (KYC) e carteira do prestador.
 */
@RestController
@RequestMapping("/prestadores")
public class PrestadorController {

    private final PrestadorService prestadorService;
    private final SaqueService saqueService;

    public PrestadorController(PrestadorService prestadorService, SaqueService saqueService) {
        this.prestadorService = prestadorService;
        this.saqueService = saqueService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrestadorResponse criar(@Valid @RequestBody CadastroPrestadorRequest request) {
        return PrestadorResponse.de(prestadorService.criar(request));
    }

    @GetMapping
    public List<PrestadorResponse> listar() {
        return prestadorService.listar().stream().map(PrestadorResponse::de).toList();
    }

    /**
     * Recebe os documentos de verificação (KYC) do prestador (US-01).
     *
     * @throws com.example.Estrela.exception.ArquivoInvalidoException se algum arquivo tiver tipo/tamanho inválido (HTTP 400)
     * @throws com.example.Estrela.exception.AcessoNegadoException    se não for o dono do cadastro (HTTP 403)
     */
    @PostMapping("/{id}/documentos")
    public PrestadorResponse enviarDocumentos(@PathVariable Long id,
                                               @RequestParam("documentoIdentidade") MultipartFile documentoIdentidade,
                                               @RequestParam("comprovantePix") MultipartFile comprovantePix,
                                               @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return PrestadorResponse.de(prestadorService.enviarDocumentos(id, usuario.getId(), documentoIdentidade, comprovantePix));
    }

    /**
     * Perfil de um prestador — usado pelo próprio painel do prestador para checar o status de KYC
     * após o cadastro (US-01), já que o cadastro não devolve token de sessão automaticamente.
     */
    @GetMapping("/{id}")
    public PrestadorResponse obterPorId(@PathVariable Long id) {
        return PrestadorResponse.de(prestadorService.buscarPorId(id));
    }

    @GetMapping("/{id}/saldo")
    public CarteiraResponse obterSaldo(@PathVariable Long id, @AuthenticationPrincipal TaskGoUserDetails usuario) {
        return new CarteiraResponse(prestadorService.obterSaldo(id, usuario.getId()));
    }

    /**
     * Solicita um saque Pix do saldo disponível (US-08).
     *
     * @throws com.example.Estrela.exception.SaldoInsuficienteException se o valor exceder o saldo disponível (HTTP 422)
     */
    @PostMapping("/{id}/saques")
    @ResponseStatus(HttpStatus.CREATED)
    public SaqueResponse solicitarSaque(@PathVariable Long id, @Valid @RequestBody SaqueRequest request,
                                         @AuthenticationPrincipal TaskGoUserDetails usuario) {
        if (!id.equals(usuario.getId())) {
            throw new com.example.Estrela.exception.AcessoNegadoException("Você só pode sacar do seu próprio saldo");
        }
        var saque = saqueService.solicitar(id, request.valor());
        return new SaqueResponse(saque.getId(), saque.getValor(), saque.getStatus(), saque.getPrestador().getSaldoDisponivel());
    }

}
