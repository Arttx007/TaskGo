package com.example.Estrela.Service;

import com.example.Estrela.DTO.LoginRequest;
import com.example.Estrela.DTO.LoginResponse;
import com.example.Estrela.Entity.Administrador;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.TipoUsuario;
import com.example.Estrela.exception.CredenciaisInvalidasException;
import com.example.Estrela.repository.AdministradorRepository;
import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.PrestadorRepository;
import com.example.Estrela.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.function.Function;

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
     * <p>Conta de cliente desativada é tratada como inexistente: a mesma mensagem genérica é
     * devolvida, para não revelar a terceiros que aquele e-mail já teve conta na plataforma.
     *
     * @throws CredenciaisInvalidasException se o e-mail não existir para o tipo informado, a conta
     *                                       estiver desativada, ou a senha não conferir
     */
    public LoginResponse login(LoginRequest request) {
        return switch (request.getTipoUsuario()) {
            case CLIENTE -> autenticar(
                    clienteRepository.findByEmail(request.getEmail())
                            .filter(c -> !Boolean.FALSE.equals(c.getAtivo())),
                    request.getSenha(),
                    Cliente::getSenha, Cliente::getIdCliente, Cliente::getNome, TipoUsuario.CLIENTE);
            case PRESTADOR -> autenticar(prestadorRepository.findByEmail(request.getEmail()), request.getSenha(),
                    Prestador::getSenha, Prestador::getIdPrestador, Prestador::getNome, TipoUsuario.PRESTADOR);
            case ADMIN -> autenticar(administradorRepository.findByEmail(request.getEmail()), request.getSenha(),
                    Administrador::getSenha, Administrador::getId, Administrador::getNome, TipoUsuario.ADMIN);
        };
    }

    /**
     * Autentica uma conta já localizada pelo e-mail e emite o token correspondente.
     *
     * <p>Os três tipos de conta compartilham exatamente a mesma regra — a diferença entre eles é
     * apenas de onde vêm os dados —, então parametrizar os extratores evita manter três cópias que
     * podem divergir em silêncio. A mensagem é sempre a genérica, para não revelar se foi o e-mail
     * ou a senha que falhou.
     *
     * @param conta        conta encontrada para o e-mail informado, ou vazio se não existir
     * @param senhaEnviada senha em texto puro recebida na requisição
     * @param senha        extrai do registro a senha armazenada (em hash)
     * @param id           extrai do registro o identificador
     * @param nome         extrai do registro o nome
     * @param tipo         tipo da conta, usado na claim do token e na resposta
     * @param <T>          tipo da entidade de conta (Cliente, Prestador ou Administrador)
     * @return o token emitido e os dados básicos da conta
     * @throws CredenciaisInvalidasException se a conta não existir ou a senha não conferir
     */
    private <T> LoginResponse autenticar(Optional<T> conta,
                                          String senhaEnviada,
                                          Function<T, String> senha,
                                          Function<T, Long> id,
                                          Function<T, String> nome,
                                          TipoUsuario tipo) {
        T registro = conta.orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(senhaEnviada, senha.apply(registro))) {
            throw new CredenciaisInvalidasException("E-mail ou senha inválidos");
        }

        String token = jwtService.gerarToken(id.apply(registro), tipo, nome.apply(registro));
        return new LoginResponse(token, tipo, id.apply(registro), nome.apply(registro));
    }
}
