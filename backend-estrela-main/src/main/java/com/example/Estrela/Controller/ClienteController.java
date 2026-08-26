package com.example.Estrela.Controller;

import com.example.Estrela.DTO.CadastroClienteRequest;
import com.example.Estrela.DTO.ClienteResponse;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Service.ClienteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Cadastro e consulta de clientes.
 */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    public ClienteResponse criar(@Valid @RequestBody CadastroClienteRequest request) {
        return paraResposta(clienteService.criar(request));
    }

    @GetMapping
    public List<ClienteResponse> listar() {
        return clienteService.listar().stream().map(this::paraResposta).toList();
    }

    private ClienteResponse paraResposta(Cliente cliente) {
        return new ClienteResponse(cliente.getIdCliente(), cliente.getNome(), cliente.getIdade(),
                cliente.getCidade(), cliente.getTipo_cliente(), cliente.getEmail());
    }
}
