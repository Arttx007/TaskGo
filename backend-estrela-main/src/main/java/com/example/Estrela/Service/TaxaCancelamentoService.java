package com.example.Estrela.Service;

import com.example.Estrela.Entity.EnderecoCliente;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Localizacao;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Apura a taxa de cancelamento retida quando o cliente desiste de um atendimento já
 * iniciado (RN03).
 *
 * <p>A taxa vai <b>integralmente ao prestador</b>, que se deslocou até o local e teve o
 * tempo comprometido. A plataforma não retém nada nesse desfecho: a taxa de serviço da RN01
 * é devolvida ao cliente junto com o restante. Isso é deliberado — o TaskGo não deve ter
 * incentivo para tornar o cancelamento atraente.
 *
 * <p>O percentual cresce com a distância porque essa é a única variável de esforço que a
 * plataforma realmente conhece: quem dirigiu 30 km perdeu mais que quem andou 2 km. Quando a
 * distância não pode ser apurada — endereço sem coordenadas, solicitação sem endereço, ou
 * localização do prestador sem lat/lon — aplica-se o percentual <b>menor</b>: numa apuração
 * de dinheiro, a falta de dado favorece quem está pagando.
 *
 * <p>Lê os parâmetros a cada apuração, sem cache, como {@link TaxaService} já faz, para que
 * um admin possa ajustá-los sem deploy.
 */
@Service
public class TaxaCancelamentoService {

    private static final String CHAVE_CARENCIA = "cancelamento.carencia-minutos";
    private static final String CHAVE_PERCENTUAL_PERTO = "cancelamento.taxa-percentual-perto";
    private static final String CHAVE_PERCENTUAL_LONGE = "cancelamento.taxa-percentual-longe";
    private static final String CHAVE_LIMIAR_DISTANCIA = "cancelamento.limiar-distancia-km";
    private static final String CHAVE_TETO = "cancelamento.taxa-teto";

    private final ParametroNegocioService parametroNegocioService;
    private final GeoService geoService;

    public TaxaCancelamentoService(ParametroNegocioService parametroNegocioService,
                                   GeoService geoService) {
        this.parametroNegocioService = parametroNegocioService;
        this.geoService = geoService;
    }

    /**
     * Diz se a carência do atendimento já passou.
     *
     * <p>Solicitação que nunca foi iniciada não tem carência a vencer: devolve {@code false},
     * e o cancelamento é integral.
     *
     * @param servico solicitação avaliada
     * @return {@code true} se o atendimento começou e a carência já venceu
     */
    public boolean carenciaVencida(FatoServico servico) {
        LocalDateTime iniciadoEm = servico.getIniciadoEm();
        if (iniciadoEm == null) {
            return false;
        }
        long carenciaMinutos = parametroNegocioService.valor(CHAVE_CARENCIA).longValue();
        return LocalDateTime.now().isAfter(iniciadoEm.plusMinutes(carenciaMinutos));
    }

    /**
     * Apura a taxa de cancelamento de uma solicitação.
     *
     * <p>Nunca excede o valor pago: com teto configurado acima do valor do serviço, a taxa é o
     * próprio valor, e o cliente nunca fica devendo.
     *
     * @param servico solicitação sendo cancelada
     * @return taxa a reter para o prestador, arredondada a 2 casas
     */
    public BigDecimal calcular(FatoServico servico) {
        BigDecimal valorServico = servico.getValor() == null ? BigDecimal.ZERO : servico.getValor();
        if (valorServico.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal percentual = percentualPara(servico);
        BigDecimal taxa = valorServico.multiply(percentual).setScale(2, RoundingMode.HALF_UP);

        BigDecimal teto = parametroNegocioService.valor(CHAVE_TETO).setScale(2, RoundingMode.HALF_UP);
        if (taxa.compareTo(teto) > 0) {
            taxa = teto;
        }

        BigDecimal valorMaximo = valorServico.setScale(2, RoundingMode.HALF_UP);
        return taxa.compareTo(valorMaximo) > 0 ? valorMaximo : taxa;
    }

    /**
     * Taxa que seria retida se o cliente cancelasse agora, para a interface poder avisar antes
     * de confirmar. Zero quando o cancelamento seria integral.
     *
     * @param servico solicitação em andamento
     * @return valor previsto de retenção, ou zero
     */
    public BigDecimal retencaoPrevista(FatoServico servico) {
        return carenciaVencida(servico) ? calcular(servico) : BigDecimal.ZERO;
    }

    private BigDecimal percentualPara(FatoServico servico) {
        BigDecimal perto = parametroNegocioService.valor(CHAVE_PERCENTUAL_PERTO);
        Double distancia = distanciaKm(servico);

        if (distancia == null) {
            return perto;
        }

        BigDecimal limiar = parametroNegocioService.valor(CHAVE_LIMIAR_DISTANCIA);
        return distancia > limiar.doubleValue()
                ? parametroNegocioService.valor(CHAVE_PERCENTUAL_LONGE)
                : perto;
    }

    /**
     * Distância entre o local do atendimento e a localização do prestador, ou {@code null}
     * quando qualquer das duas pontas não tem coordenadas cadastradas.
     */
    private Double distanciaKm(FatoServico servico) {
        EnderecoCliente endereco = servico.getEnderecoCliente();
        Localizacao local = servico.getLocalizacao();

        if (endereco == null || local == null
                || endereco.getLatitude() == null || endereco.getLongitude() == null
                || local.getLatitude() == null || local.getLongitude() == null) {
            return null;
        }

        return geoService.distanciaKm(endereco.getLatitude(), endereco.getLongitude(),
                local.getLatitude(), local.getLongitude());
    }
}
