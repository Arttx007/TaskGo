package com.example.Estrela.Service;

import com.example.Estrela.DTO.PagamentoRequest;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.Pagamento;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.StatusPagamento;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.PagamentoRecusadoException;
import com.example.Estrela.repository.PagamentoRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Orquestra custódia de pagamento (RN03): cálculo de taxa (RN01), cobrança via {@link PagamentoGateway}
 * e liberação/estorno do valor retido. Não integra um PSP real (spec.md coloca essa escolha fora do
 * escopo do MVP) — a cobrança é sempre mockada, mas o estado de custódia é real e persistido.
 */
@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final PrestadorRepository prestadorRepository;
    private final TaxaService taxaService;
    private final PagamentoGateway pagamentoGateway;

    public PagamentoService(PagamentoRepository pagamentoRepository,
                             PrestadorRepository prestadorRepository,
                             TaxaService taxaService,
                             PagamentoGateway pagamentoGateway) {
        this.pagamentoRepository = pagamentoRepository;
        this.prestadorRepository = prestadorRepository;
        this.taxaService = taxaService;
        this.pagamentoGateway = pagamentoGateway;
    }

    /**
     * Cobra o cliente e retém o valor em custódia.
     *
     * @throws EstadoInvalidoException    se já existir um pagamento retido/liberado para esta solicitação
     * @throws PagamentoRecusadoException se a cobrança for recusada (HTTP 402); nenhum {@link Pagamento} é persistido
     */
    public Pagamento pagar(FatoServico servico, PagamentoRequest request) {
        buscarPorServicoOpcional(servico).ifPresent(pagamentoExistente -> {
            if (pagamentoExistente.getStatus() == StatusPagamento.RETIDO || pagamentoExistente.getStatus() == StatusPagamento.LIBERADO) {
                throw new EstadoInvalidoException("Esta solicitação já foi paga");
            }
        });

        ResultadoTaxa resultadoTaxa = taxaService.calcular(servico.getValor());

        PagamentoGateway.ResultadoCobranca cobranca = pagamentoGateway.cobrar(
                servico.getValor(), request.metodoPagamento(), request.simularFalha());

        if (!cobranca.aprovado()) {
            throw new PagamentoRecusadoException(cobranca.motivoRecusa());
        }

        LocalDateTime agora = LocalDateTime.now();
        Pagamento pagamento = new Pagamento();
        pagamento.setFatoServico(servico);
        pagamento.setValorBruto(servico.getValor());
        pagamento.setValorTaxa(resultadoTaxa.valorTaxa());
        pagamento.setValorLiquido(resultadoTaxa.valorLiquido());
        pagamento.setStatus(StatusPagamento.RETIDO);
        pagamento.setMetodoPagamento(request.metodoPagamento());
        pagamento.setCriadoEm(agora);
        pagamento.setAtualizadoEm(agora);

        return pagamentoRepository.save(pagamento);
    }

    /**
     * Libera o valor retido para o saldo do prestador, na conclusão do atendimento (US-07).
     *
     * @throws EstadoInvalidoException se não houver pagamento em custódia para esta solicitação
     */
    @Transactional
    public void liberar(FatoServico servico) {
        Pagamento pagamento = buscarPorServico(servico);
        if (pagamento.getStatus() != StatusPagamento.RETIDO) {
            throw new EstadoInvalidoException("Conclusão depende de um pagamento confirmado em custódia");
        }

        pagamento.setStatus(StatusPagamento.LIBERADO);
        pagamento.setAtualizadoEm(LocalDateTime.now());
        pagamentoRepository.save(pagamento);

        Prestador prestador = servico.getPrestador();
        prestador.setSaldoDisponivel(prestador.getSaldoDisponivel().add(pagamento.getValorLiquido()));
        prestadorRepository.save(prestador);
    }

    /**
     * Estorna integralmente o valor retido ao cliente, sem cobrar taxa, no cancelamento de uma
     * solicitação já aceita e paga (RN03, US-10). Não debita o prestador — o crédito só ocorre em
     * {@link #liberar}, então nunca há nada a reverter do lado dele.
     */
    public void estornarSeRetido(FatoServico servico) {
        buscarPorServicoOpcional(servico).ifPresent(pagamento -> {
            if (pagamento.getStatus() == StatusPagamento.RETIDO) {
                pagamento.setStatus(StatusPagamento.ESTORNADO);
                pagamento.setAtualizadoEm(LocalDateTime.now());
                pagamentoRepository.save(pagamento);
            }
        });
    }

    public boolean possuiPagamentoRetido(FatoServico servico) {
        return buscarPorServicoOpcional(servico)
                .map(p -> p.getStatus() == StatusPagamento.RETIDO)
                .orElse(false);
    }

    /**
     * @return o status do pagamento desta solicitação, ou {@code null} se nenhum pagamento foi criado ainda
     */
    public StatusPagamento obterStatus(FatoServico servico) {
        return buscarPorServicoOpcional(servico).map(Pagamento::getStatus).orElse(null);
    }

    private Pagamento buscarPorServico(FatoServico servico) {
        return buscarPorServicoOpcional(servico)
                .orElseThrow(() -> new EstadoInvalidoException("Não há pagamento registrado para esta solicitação"));
    }

    private java.util.Optional<Pagamento> buscarPorServicoOpcional(FatoServico servico) {
        return pagamentoRepository.findByFatoServico(servico);
    }
}
