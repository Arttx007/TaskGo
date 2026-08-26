package com.example.Estrela.Controller;

import com.example.Estrela.DTO.LoginRequest;
import com.example.Estrela.DTO.LoginResponse;
import com.example.Estrela.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Autenticação de Cliente, Prestador e Administrador via JWT.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Autentica um usuário e emite um token JWT.
     *
     * @param request e-mail, senha e tipo de conta
     * @return token e dados básicos do usuário autenticado
     * @throws com.example.Estrela.exception.CredenciaisInvalidasException se as credenciais forem inválidas (HTTP 401)
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
