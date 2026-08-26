package com.example.Estrela.Service;

import com.example.Estrela.DTO.CadastroClienteRequest;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.ClienteRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cadastro e consulta de clientes. Existe como Service (em vez de o Controller acessar o
 * repository direto, como antes) porque agora há uma regra real a aplicar: hashear a senha.
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;

    public ClienteService(ClienteRepository clienteRepository, PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Cliente criar(CadastroClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.nome());
        cliente.setIdade(request.idade());
        cliente.setCidade(request.cidade());
        cliente.setTipo_cliente(request.tipoCliente());
        cliente.setEmail(request.email());
        cliente.setSenha(passwordEncoder.encode(request.senha()));
        return clienteRepository.save(cliente);
    }

    public List<Cliente> listar() {
        return clienteRepository.findAll();
    }

    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
    }
}
