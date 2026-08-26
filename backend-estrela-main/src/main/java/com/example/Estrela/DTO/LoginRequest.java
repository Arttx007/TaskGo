package com.example.Estrela.DTO;

import com.example.Estrela.Entity.TipoUsuario;
import jakarta.validation.constraints.NotNull;

public class LoginRequest {
    private String email;
    private String senha;

    @NotNull(message = "tipoUsuario é obrigatório")
    private TipoUsuario tipoUsuario;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public TipoUsuario getTipoUsuario() { return tipoUsuario; }
    public void setTipoUsuario(TipoUsuario tipoUsuario) { this.tipoUsuario = tipoUsuario; }
}
