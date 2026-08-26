package com.example.Estrela.Controller;

import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.FatoServicoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private FatoServicoRepository servicoRepo;

    @Autowired
    private ClienteRepository clienteRepo;

    @Autowired
    private PrestadorRepository prestadorRepo;

    @GetMapping
    public Map<String, Object> dashboard() {
        Map<String, Object> dados = new HashMap<>();

        dados.put("totalServicos", servicoRepo.count());
        dados.put("totalClientes", clienteRepo.count());
        dados.put("totalPrestadores", prestadorRepo.count());

        return dados;
    }
}
