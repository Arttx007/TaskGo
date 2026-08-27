package com.example.Estrela.Controller;

import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.FatoServicoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final FatoServicoRepository servicoRepo;
    private final ClienteRepository clienteRepo;
    private final PrestadorRepository prestadorRepo;

    public DashboardController(FatoServicoRepository servicoRepo,
                                ClienteRepository clienteRepo,
                                PrestadorRepository prestadorRepo) {
        this.servicoRepo = servicoRepo;
        this.clienteRepo = clienteRepo;
        this.prestadorRepo = prestadorRepo;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> dados = new HashMap<>();

        dados.put("totalServicos", servicoRepo.count());
        dados.put("totalClientes", clienteRepo.count());
        dados.put("totalPrestadores", prestadorRepo.count());

        return dados;
    }
}
