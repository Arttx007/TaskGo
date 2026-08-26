package com.example.Estrela.Service;

import com.example.Estrela.Entity.ParametroNegocio;
import com.example.Estrela.repository.ParametroNegocioRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Leitura de parâmetros de negócio ajustáveis pelo admin sem deploy (ex.: RN01, raio padrão de
 * busca). Sem cache — o volume de leituras é baixo o suficiente para não justificar a complexidade.
 */
@Service
public class ParametroNegocioService {

    private final ParametroNegocioRepository parametroNegocioRepository;

    public ParametroNegocioService(ParametroNegocioRepository parametroNegocioRepository) {
        this.parametroNegocioRepository = parametroNegocioRepository;
    }

    /**
     * @param chave chave do parâmetro
     * @return valor atual do parâmetro
     * @throws IllegalStateException se o parâmetro não estiver cadastrado (falha de seed/migração, não de usuário)
     */
    public BigDecimal valor(String chave) {
        ParametroNegocio parametro = parametroNegocioRepository.findById(chave)
                .orElseThrow(() -> new IllegalStateException("Parâmetro de negócio ausente: " + chave));
        return parametro.getValor();
    }
}
