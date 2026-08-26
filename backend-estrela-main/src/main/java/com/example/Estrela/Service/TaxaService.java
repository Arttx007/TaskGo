package com.example.Estrela.Service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula a taxa de serviço cobrada em cada atendimento (RN01): taxa fixa para valores abaixo do
 * limiar configurado, percentual a partir dele. Lê os parâmetros via {@link ParametroNegocioService}
 * a cada chamada — sem cache — para que um admin possa ajustá-los sem precisar de deploy.
 */
@Service
public class TaxaService {

    private static final String CHAVE_LIMIAR = "taxa.limiar";
    private static final String CHAVE_FIXA = "taxa.fixa";
    private static final String CHAVE_PERCENTUAL = "taxa.percentual";

    private final ParametroNegocioService parametroNegocioService;

    public TaxaService(ParametroNegocioService parametroNegocioService) {
        this.parametroNegocioService = parametroNegocioService;
    }

    /**
     * Calcula a taxa e o valor líquido para um valor de serviço, conforme RN01.
     *
     * @param valorServico valor total cobrado do cliente
     * @return taxa cobrada e valor líquido resultante para o prestador
     */
    public ResultadoTaxa calcular(BigDecimal valorServico) {
        BigDecimal limiar = parametroNegocioService.valor(CHAVE_LIMIAR);
        BigDecimal taxa;

        if (valorServico.compareTo(limiar) < 0) {
            taxa = parametroNegocioService.valor(CHAVE_FIXA);
        } else {
            BigDecimal percentual = parametroNegocioService.valor(CHAVE_PERCENTUAL);
            taxa = valorServico.multiply(percentual).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal valorLiquido = valorServico.subtract(taxa);
        return new ResultadoTaxa(taxa, valorLiquido);
    }
}
