package com.example.Estrela.Controller;

import com.example.Estrela.DTO.ParametroNegocioRequest;
import com.example.Estrela.DTO.ParametroNegocioResponse;
import com.example.Estrela.DTO.PrestadorResponse;
import com.example.Estrela.Service.AdminService;
import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.FatoServicoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.List;

/**
 * Operações administrativas: dashboard de contagem (já existente), aprovação/rejeição de KYC de
 * prestadores e parâmetros de negócio ajustáveis sem deploy (RN01 e afins).
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final FatoServicoRepository fatoServicoRepository;
    private final AdminService adminService;

    public AdminController(ClienteRepository clienteRepository,
                            PrestadorRepository prestadorRepository,
                            FatoServicoRepository fatoServicoRepository,
                            AdminService adminService) {
        this.clienteRepository = clienteRepository;
        this.prestadorRepository = prestadorRepository;
        this.fatoServicoRepository = fatoServicoRepository;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        long clientes = clienteRepository.count();
        long prestadores = prestadorRepository.count();
        long servicos = fatoServicoRepository.count();

        return "Clientes: " + clientes +
               " | Prestadores: " + prestadores +
               " | Serviços: " + servicos;
    }

    @GetMapping("/prestadores/pendentes")
    public List<PrestadorResponse> listarPrestadoresPendentes() {
        return adminService.listarPrestadoresPendentes().stream().map(PrestadorResponse::de).toList();
    }

    @PutMapping("/prestadores/{id}/kyc/aprovar")
    public PrestadorResponse aprovarKyc(@PathVariable Long id) {
        return PrestadorResponse.de(adminService.aprovarKyc(id));
    }

    @PutMapping("/prestadores/{id}/kyc/rejeitar")
    public PrestadorResponse rejeitarKyc(@PathVariable Long id, @RequestBody(required = false) com.example.Estrela.DTO.KycDecisionRequest request) {
        return PrestadorResponse.de(adminService.rejeitarKyc(id));
    }

    /**
     * Serve um documento de KYC de um prestador para o admin conferir antes de aprovar.
     *
     * @param tipo {@code "identidade"} ou {@code "pix"}
     * @throws com.example.Estrela.exception.RecursoNaoEncontradoException se o documento não existir (HTTP 404)
     */
    @GetMapping("/prestadores/{id}/documentos/{tipo}")
    public ResponseEntity<Resource> obterDocumento(@PathVariable Long id, @PathVariable String tipo) throws java.io.IOException {
        var caminho = adminService.caminhoDocumento(id, tipo);
        String contentType = Files.probeContentType(caminho);
        Resource recurso = new FileSystemResource(caminho);
        return ResponseEntity.ok()
                .header("Content-Type", contentType != null ? contentType : "application/octet-stream")
                .body(recurso);
    }

    @GetMapping("/parametros")
    public List<ParametroNegocioResponse> listarParametros() {
        return adminService.listarParametros().stream()
                .map(p -> new ParametroNegocioResponse(p.getChave(), p.getValor(), p.getDescricao()))
                .toList();
    }

    @PutMapping("/parametros/{chave}")
    public ParametroNegocioResponse atualizarParametro(@PathVariable String chave, @Valid @RequestBody ParametroNegocioRequest request) {
        var parametro = adminService.atualizarParametro(chave, request.valor());
        return new ParametroNegocioResponse(parametro.getChave(), parametro.getValor(), parametro.getDescricao());
    }

}
