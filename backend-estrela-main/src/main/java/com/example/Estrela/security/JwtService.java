package com.example.Estrela.security;

import com.example.Estrela.Entity.TipoUsuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Gera e valida os tokens JWT usados como mecanismo único de autenticação da API (Cliente/Prestador/Admin).
 */
@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracaoMinutos;

    public JwtService(@Value("${taskgo.jwt.secret}") String segredo,
                       @Value("${taskgo.jwt.expiracao-minutos}") long expiracaoMinutos) {
        this.chave = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        this.expiracaoMinutos = expiracaoMinutos;
    }

    /**
     * Gera um token assinado para o usuário informado.
     *
     * @param id    identificador do usuário (Cliente/Prestador/Administrador)
     * @param role  tipo de conta (claim {@code role})
     * @param nome  nome do usuário (claim {@code nome})
     * @return o token JWT compactado
     */
    public String gerarToken(Long id, TipoUsuario role, String nome) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoMinutos * 60_000);
        return Jwts.builder()
                .subject(String.valueOf(id))
                .claim("role", role.name())
                .claim("nome", nome)
                .issuedAt(agora)
                .expiration(expiracao)
                .signWith(chave)
                .compact();
    }

    /**
     * Valida o token e retorna suas claims.
     *
     * @param token token JWT recebido no header {@code Authorization}
     * @return as claims do token
     * @throws io.jsonwebtoken.JwtException se o token for inválido, malformado ou expirado
     */
    public Claims validarEExtrairClaims(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
