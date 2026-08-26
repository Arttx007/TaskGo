package com.example.Estrela.Service;

import com.example.Estrela.DTO.LoginRequest;
import com.example.Estrela.DTO.LoginResponse;
import com.example.Estrela.Entity.Administrador;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.exception.CredenciaisInvalidasException;
import com.example.Estrela.repository.AdministradorRepository;
import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.PrestadorRepository;
import com.example.Estrela.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autentica Cliente, Prestador ou Administrador a partir de um único endpoint de login,
 * delegando ao repositório correto conforme {@link LoginRequest#getTipoUsuario()}.
 */
@Service
public class AuthService {

    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final AdministradorRepository administradorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(ClienteRepository clienteRepository,
                        PrestadorRepository prestadorRepository,
                        AdministradorRepository administradorRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.clienteRepository = clienteRepository;
        this.prestadorRepository = prestadorRepository;
        this.administradorRepository = administradorRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Autentica um usuário e emite um token JWT.
     *
     * @param request e-mail, senha e tipo de conta (CLIENTE/PRESTADOR/ADMIN)
     * @return o token emitido e os dados básicos do usuário
     * @throws CredenciaisInvalidasException se o e-mail não existir para o tipo informado ou a senha não confere
     */
    public LoginResponse login(LoginRequest request) {
        return switch (request.getTipoUsuario()) {
            case CLIENTE -> autenticarCliente(request);
            case PRESTADOR -> autenticarPrestador(request);
            case ADMIN -> autenticarAdmin(request);
        };
    }

    private LoginResponse autenticarCliente(LoginRequest request) {
        Cliente cliente = clienteRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos"));
        if (!passwordEncoder.matches(request.getSenha(), cliente.getSenha())) {
            throw new CredenciaisInvalidasException("E-mail ou senha inválidos");
        }
        String token = jwtService.gerarToken(cliente.getIdCliente(), com.example.Estrela.Entity.TipoUsuario.CLIENTE, cliente.getNome());
        return new LoginResponse(token, com.example.Estrela.Entity.TipoUsuario.CLIENTE, cliente.getIdCliente(), cliente.getNome());
    }

    private LoginResponse autenticarPrestador(LoginRequest request) {
        Prestador prestador = prestadorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos"));
        if (prestador.getSenha() == null || !passwordEncoder.matches(request.getSenha(), prestador.getSenha())) {
            throw new CredenciaisInvalidasException("E-mail ou senha inválidos");
        }
        String token = jwtService.gerarToken(prestador.getIdPrestador(), com.example.Estrela.Entity.TipoUsuario.PRESTADOR, prestador.getNome());
        return new LoginResponse(token, com.example.Estrela.Entity.TipoUsuario.PRESTADOR, prestador.getIdPrestador(), prestador.getNome());
    }

    private LoginResponse autenticarAdmin(LoginRequest request) {
        Administrador admin = administradorRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos"));
        if (!passwordEncoder.matches(request.getSenha(), admin.getSenha())) {
            throw new CredenciaisInvalidasException("E-mail ou senha inválidos");
        }
        String token = jwtService.gerarToken(admin.getId(), com.example.Estrela.Entity.TipoUsuario.ADMIN, admin.getNome());
        return new LoginResponse(token, com.example.Estrela.Entity.TipoUsuario.ADMIN, admin.getId(), admin.getNome());
    }
}
