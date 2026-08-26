package com.example.Estrela.Service;

import com.example.Estrela.DTO.AvaliacaoRequest;
import com.example.Estrela.DTO.PagamentoRequest;
import com.example.Estrela.DTO.SolicitacaoRequest;
import com.example.Estrela.Entity.*;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoIndisponivelException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Dono da máquina de estados de uma solicitação de serviço (RN02): SOLICITADO -&gt; ACEITO -&gt;
 * CONCLUIDO -&gt; AVALIADO, com ramos para RECUSADO e CANCELADO. Toda transição valida que quem
 * chama é o cliente ou o prestador dono da solicitação.
 */
@Service
public class FatoServicoService {

    private final FatoServicoRepository repository;
    private final ClienteRepository clienteRepo;
    private final PrestadorRepository prestadorRepo;
    private final ServicoOfertadoRepository servicoOfertadoRepo;
    private final TempoRepository tempoRepo;
    private final PagamentoService pagamentoService;

    public FatoServicoService(FatoServicoRepository repository,
                               ClienteRepository clienteRepo,
                               PrestadorRepository prestadorRepo,
                               ServicoOfertadoRepository servicoOfertadoRepo,
                               TempoRepository tempoRepo,
                               PagamentoService pagamentoService) {
        this.repository = repository;
        this.clienteRepo = clienteRepo;
        this.prestadorRepo = prestadorRepo;
        this.servicoOfertadoRepo = servicoOfertadoRepo;
        this.tempoRepo = tempoRepo;
        this.pagamentoService = pagamentoService;
    }

    /**
     * Cria uma solicitação de serviço (US-04).
     *
     * @throws RecursoNaoEncontradoException  se cliente ou serviço ofertado não existirem
     * @throws RecursoIndisponivelException   se o serviço estiver inativo ou o prestador sem KYC aprovado
     * @throws EstadoInvalidoException        se já houver uma solicitação em aberto do cliente com o mesmo prestador
     */
    public FatoServico solicitar(Long clienteId, SolicitacaoRequest request) {
        Cliente cliente = clienteRepo.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));

        ServicoOfertado servicoOfertado = servicoOfertadoRepo.findById(request.servicoOfertadoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço ofertado não encontrado"));

        Prestador prestador = servicoOfertado.getPrestador();

        if (servicoOfertado.getStatus() != StatusServico.ATIVO || prestador.getStatusKyc() != StatusKyc.APROVADO) {
            throw new RecursoIndisponivelException("Este serviço não está mais disponível");
        }

        boolean duplicado = repository.findByCliente_IdCliente(clienteId).stream()
                .anyMatch(s -> s.getPrestador().getIdPrestador().equals(prestador.getIdPrestador())
                        && (s.getStatus() == StatusSolicitacao.SOLICITADO || s.getStatus() == StatusSolicitacao.ACEITO));
        if (duplicado) {
            throw new EstadoInvalidoException("Você já tem uma solicitação em aberto com este prestador");
        }

        FatoServico servico = new FatoServico();
        servico.setCliente(cliente);
        servico.setPrestador(prestador);
        servico.setServicoOfertado(servicoOfertado);
        servico.setLocalizacao(servicoOfertado.getLocalizacao());
        servico.setValor(servicoOfertado.getPreco());
        servico.setTempo(obterOuCriarTempoDeHoje());
        servico.setStatus(StatusSolicitacao.SOLICITADO);

        return repository.save(servico);
    }

    public List<FatoServico> listarMinhas(Long usuarioId, TipoUsuario papel) {
        return papel == TipoUsuario.PRESTADOR
                ? repository.findByPrestador_IdPrestador(usuarioId)
                : repository.findByCliente_IdCliente(usuarioId);
    }

    /**
     * @throws AcessoNegadoException    se o prestador autenticado não for o dono da solicitação
     * @throws EstadoInvalidoException  se a solicitação não estiver em SOLICITADO
     */
    public FatoServico aceitar(Long id, Long prestadorIdAutenticado) {
        FatoServico servico = buscarEValidarDonoPrestador(id, prestadorIdAutenticado);
        exigirStatus(servico, StatusSolicitacao.SOLICITADO);
        servico.setStatus(StatusSolicitacao.ACEITO);
        return repository.save(servico);
    }

    public FatoServico recusar(Long id, Long prestadorIdAutenticado) {
        FatoServico servico = buscarEValidarDonoPrestador(id, prestadorIdAutenticado);
        exigirStatus(servico, StatusSolicitacao.SOLICITADO);
        servico.setStatus(StatusSolicitacao.RECUSADO);
        return repository.save(servico);
    }

    /**
     * @throws com.example.Estrela.exception.PagamentoRecusadoException se a cobrança for recusada
     */
    @Transactional
    public com.example.Estrela.Entity.Pagamento pagar(Long id, Long clienteIdAutenticado, PagamentoRequest request) {
        FatoServico servico = buscarEValidarDonoCliente(id, clienteIdAutenticado);
        exigirStatus(servico, StatusSolicitacao.ACEITO);
        return pagamentoService.pagar(servico, request);
    }

    /**
     * @throws EstadoInvalidoException se não houver pagamento confirmado em custódia (US-07, caso extremo)
     * @throws AcessoNegadoException   se quem chama não for o prestador dono (US-07, exceção)
     */
    @Transactional
    public FatoServico concluir(Long id, Long prestadorIdAutenticado) {
        FatoServico servico = buscarEValidarDonoPrestador(id, prestadorIdAutenticado);
        exigirStatus(servico, StatusSolicitacao.ACEITO);
        pagamentoService.liberar(servico);
        servico.setStatus(StatusSolicitacao.CONCLUIDO);
        return repository.save(servico);
    }

    /**
     * @throws EstadoInvalidoException se a solicitação já foi avaliada ou ainda não está concluída
     */
    public FatoServico avaliar(Long id, Long clienteIdAutenticado, AvaliacaoRequest request) {
        FatoServico servico = buscarEValidarDonoCliente(id, clienteIdAutenticado);
        if (servico.getStatus() != StatusSolicitacao.CONCLUIDO) {
            throw new EstadoInvalidoException("Solicitação já foi avaliada ou ainda não pode ser avaliada");
        }

        servico.setAvaliacao(request.nota());
        servico.setComentarioAvaliacao(request.comentario());
        servico.setStatus(StatusSolicitacao.AVALIADO);
        repository.save(servico);

        recalcularNotaMedia(servico.getPrestador());

        return servico;
    }

    /**
     * Cancela uma solicitação (US-10). Cliente ou prestador dono podem cancelar; se já havia
     * pagamento retido, é estornado integralmente sem cobrança de taxa (RN03).
     *
     * @throws EstadoInvalidoException se a solicitação já estiver concluída/avaliada
     */
    @Transactional
    public FatoServico cancelar(Long id, Long usuarioIdAutenticado, TipoUsuario papelAutenticado) {
        FatoServico servico = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));

        boolean donoComoCliente = papelAutenticado == TipoUsuario.CLIENTE
                && servico.getCliente().getIdCliente().equals(usuarioIdAutenticado);
        boolean donoComoPrestador = papelAutenticado == TipoUsuario.PRESTADOR
                && servico.getPrestador().getIdPrestador().equals(usuarioIdAutenticado);

        if (!donoComoCliente && !donoComoPrestador) {
            throw new AcessoNegadoException("Você não participa desta solicitação");
        }

        if (servico.getStatus() == StatusSolicitacao.CONCLUIDO || servico.getStatus() == StatusSolicitacao.AVALIADO) {
            throw new EstadoInvalidoException("Solicitações concluídas não podem ser canceladas");
        }

        if (servico.getStatus() == StatusSolicitacao.ACEITO) {
            pagamentoService.estornarSeRetido(servico);
        }

        servico.setStatus(StatusSolicitacao.CANCELADO);
        return repository.save(servico);
    }

    private FatoServico buscarEValidarDonoPrestador(Long id, Long prestadorIdAutenticado) {
        FatoServico servico = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));
        if (!servico.getPrestador().getIdPrestador().equals(prestadorIdAutenticado)) {
            throw new AcessoNegadoException("Esta solicitação pertence a outro prestador");
        }
        return servico;
    }

    private FatoServico buscarEValidarDonoCliente(Long id, Long clienteIdAutenticado) {
        FatoServico servico = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));
        if (!servico.getCliente().getIdCliente().equals(clienteIdAutenticado)) {
            throw new AcessoNegadoException("Esta solicitação pertence a outro cliente");
        }
        return servico;
    }

    private void exigirStatus(FatoServico servico, StatusSolicitacao esperado) {
        if (servico.getStatus() != esperado) {
            throw new EstadoInvalidoException("Ação inválida para o estado atual da solicitação: " + servico.getStatus());
        }
    }

    private void recalcularNotaMedia(Prestador prestador) {
        List<FatoServico> servicos = repository.findByPrestador_IdPrestador(prestador.getIdPrestador());

        double media = servicos.stream()
                .filter(s -> s.getAvaliacao() != null)
                .mapToInt(FatoServico::getAvaliacao)
                .average()
                .orElse(0);

        prestador.setNota_media(BigDecimal.valueOf(media));
        prestadorRepo.save(prestador);
    }

    private Tempo obterOuCriarTempoDeHoje() {
        LocalDate hoje = LocalDate.now();
        return tempoRepo.findByData(hoje).orElseGet(() -> {
            Tempo tempo = new Tempo();
            tempo.setData(hoje);
            tempo.setDia(hoje.getDayOfMonth());
            tempo.setMes(hoje.getMonthValue());
            tempo.setAno(hoje.getYear());
            tempo.setDia_semana(hoje.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("pt", "BR")));
            return tempoRepo.save(tempo);
        });
    }
}
