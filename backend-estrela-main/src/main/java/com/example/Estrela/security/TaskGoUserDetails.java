package com.example.Estrela.security;

import com.example.Estrela.Entity.TipoUsuario;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Identidade do usuário autenticado, extraída do JWT e disponibilizada via {@link org.springframework.security.core.context.SecurityContextHolder}.
 * Não é resolvida a partir de um repositório em cada requisição — o próprio token já carrega
 * id/role/nome, então a autenticação permanece stateless.
 */
public class TaskGoUserDetails implements UserDetails {

    private final Long id;
    private final TipoUsuario role;
    private final String nome;

    public TaskGoUserDetails(Long id, TipoUsuario role, String nome) {
        this.id = id;
        this.role = role;
        this.nome = nome;
    }

    public Long getId() {
        return id;
    }

    public TipoUsuario getRole() {
        return role;
    }

    public String getNome() {
        return nome;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(id);
    }
}
