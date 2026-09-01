package com.example.Estrela.Service;

import com.example.Estrela.DTO.AvaliacaoRequest;
import com.example.Estrela.DTO.PagamentoRequest;
import com.example.Estrela.DTO.EnderecoClienteResponse;
import com.example.Estrela.DTO.IniciarAtendimentoRequest;
import com.example.Estrela.DTO.SolicitacaoRequest;
import com.example.Estrela.DTO.SolicitacaoResponse;
import com.example.Estrela.Entity.*;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoIndisponivelException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Dono da máquina de estados de uma solicitação de serviço (RN02): SOLICITADO -&gt; ACEITO -&gt;
 * EM_ANDAMENTO -&gt; CONCLUIDO -&gt; AVALIADO, com ramos para RECUSADO e CANCELADO. Toda transição
 * valida que quem chama é o cliente ou o prestador dono da solicitação.
 *
 * <p>A passagem de ACEITO para EM_ANDAMENTO exige o código de confirmação entregue ao cliente,
 * e não é contornável: não existe transição direta de ACEITO para CONCLUIDO. Sem isso o código
 * seria opcional na prática, e um mecanismo de segurança que se pode contornar não é mecanismo
 * de segurança.
 */
@Service
public class FatoServicoService {

    private final FatoServicoRepository repository;
    private final ClienteRepository clienteRepo;
    private final PrestadorRepository prestadorRepo;
    private final ServicoOfertadoRepository servicoOfertadoRepo;
    private final TempoRepository tempoRepo;
    private final PagamentoService pagamentoService;
    private final EnderecoClienteService enderecoClienteService;
    private final TaxaCancelamentoService taxaCancelamentoService;

    /** Gerador do código de confirmação. Criptográfico para o código não ser previsível. */
    private static final SecureRandom ALEATORIO = new SecureRandom();

    public FatoServicoService(FatoServicoRepository repository,
                               ClienteRepository clienteRepo,
                               PrestadorRepository prestadorRepo,
                               ServicoOfertadoRepository servicoOfertadoRepo,
                               TempoRepository tempoRepo,
                               PagamentoService pagamentoService,
                               EnderecoClienteService enderecoClienteService,
                               TaxaCancelamentoService taxaCancelamentoService) {
        this.repository = repository;
        this.clienteRepo = clienteRepo;
        this.prestadorRepo = prestadorRepo;
        this.servicoOfertadoRepo = servicoOfertadoRepo;
        this.tempoRepo = tempoRepo;
        this.pagamentoService = pagamentoService;
        this.enderecoClienteService = enderecoClienteService;
        this.taxaCancelamentoService = taxaCancelamentoService;
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
                        && (s.getStatus() == StatusSolicitacao.SOLICITADO
                            || s.getStatus() == StatusSolicitacao.ACEITO
                            || s.getStatus() == StatusSolicitacao.EM_ANDAMENTO));
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
        servico.setCriadoEm(LocalDateTime.now());

        if (request.enderecoClienteId() != null) {
            servico.setEnderecoCliente(
                    enderecoClienteService.buscarEValidarDono(clienteId, request.enderecoClienteId()));
        }

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
        servico.setAceitoEm(LocalDateTime.now());
        servico.setPinConfirmacao(gerarPin());
        return repository.save(servico);
    }

    /**
     * Inicia o atendimento, com o código de confirmação que o cliente passou ao prestador no
     * local (RN02).
     *
     * <p>Exige pagamento retido em custódia: o prestador não deve se deslocar e começar o
     * serviço sem o dinheiro estar retido. Código incorreto responde 403 e não altera o código
     * armazenado, então uma tentativa errada não invalida o atendimento.
     *
     * @param id                    identificador da solicitação
     * @param prestadorIdAutenticado prestador autenticado
     * @param request               código informado
     * @return a solicitação em EM_ANDAMENTO
     * @throws AcessoNegadoException   se não for o prestador dono, ou se o código estiver errado (HTTP 403)
     * @throws EstadoInvalidoException se não estiver em ACEITO, ou não houver pagamento retido (HTTP 409)
     */
    @Transactional
    public FatoServico iniciar(Long id, Long prestadorIdAutenticado, IniciarAtendimentoRequest request) {
        FatoServico servico = buscarEValidarDonoPrestador(id, prestadorIdAutenticado);
        exigirStatus(servico, StatusSolicitacao.ACEITO);

        if (!pagamentoService.possuiPagamentoRetido(servico)) {
            throw new EstadoInvalidoException(
                    "O atendimento só pode começar depois de o pagamento estar retido em custódia");
        }

        String informado = request.pin() == null ? "" : request.pin().trim();
        if (servico.getPinConfirmacao() == null || !servico.getPinConfirmacao().equals(informado)) {
            throw new AcessoNegadoException("Código de confirmação inválido");
        }

        servico.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        servico.setIniciadoEm(LocalDateTime.now());
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
        exigirStatus(servico, StatusSolicitacao.EM_ANDAMENTO);
        pagamentoService.liberar(servico);
        servico.setStatus(StatusSolicitacao.CONCLUIDO);
        servico.setConcluidoEm(LocalDateTime.now());
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
        FatoServico servico = buscarEValidarParte(id, usuarioIdAutenticado, papelAutenticado);

        if (servico.getStatus() == StatusSolicitacao.CONCLUIDO || servico.getStatus() == StatusSolicitacao.AVALIADO) {
            throw new EstadoInvalidoException("Solicitações concluídas não podem ser canceladas");
        }

        // O estorno é processado sempre, e não só a partir de ACEITO. A guarda de bloqueio
        // acima é lista negra, então EM_ANDAMENTO passou a ser cancelável de graça — e com a
        // condição antiga o pagamento ficaria preso em RETIDO para sempre, com a requisição
        // respondendo 200. estornarSeRetido e estornarComTaxa são idempotentes: nada fazem
        // quando não há pagamento retido.
        boolean clienteDesistiuDepoisDaCarencia = papelAutenticado == TipoUsuario.CLIENTE
                && servico.getStatus() == StatusSolicitacao.EM_ANDAMENTO
                && taxaCancelamentoService.carenciaVencida(servico);

        if (clienteDesistiuDepoisDaCarencia) {
            // RN03: o prestador se deslocou e teve o tempo comprometido, então a taxa vai
            // integralmente para ele. A plataforma não retém nada neste desfecho.
            pagamentoService.estornarComTaxa(servico, taxaCancelamentoService.calcular(servico));
        } else {
            pagamentoService.estornarSeRetido(servico);
        }

        servico.setStatus(StatusSolicitacao.CANCELADO);
        return repository.save(servico);
    }

    /**
     * Monta a resposta de uma solicitação para quem a está vendo.
     *
     * <p>Vive no service, e não no controller, por causa de um único campo: o código de
     * confirmação é preenchido <b>somente para o cliente dono</b>. Como
     * {@link SolicitacaoResponse} é um record único usado por todas as rotas — listagem,
     * detalhe e cada transição —, deixar essa decisão espalhada pelos chamadores criaria
     * caminhos onde o código vaza para o prestador, anulando o mecanismo. Aqui existe um
     * ponto único a auditar.
     *
     * @param servico          solicitação a serializar
     * @param papelDeQuemVe    papel da conta que está consultando
     * @return a resposta, com o código presente apenas para o cliente dono
     */
    public SolicitacaoResponse paraResposta(FatoServico servico, TipoUsuario papelDeQuemVe) {
        boolean clienteVendo = papelDeQuemVe == TipoUsuario.CLIENTE;

        return new SolicitacaoResponse(
                servico.getId_servico(),
                servico.getStatus(),
                servico.getCliente() != null ? servico.getCliente().getIdCliente() : null,
                servico.getCliente() != null ? servico.getCliente().getNome() : null,
                servico.getPrestador() != null ? servico.getPrestador().getIdPrestador() : null,
                servico.getPrestador() != null ? servico.getPrestador().getNome() : null,
                servico.getServicoOfertado() != null ? servico.getServicoOfertado().getId() : null,
                servico.getServicoOfertado() != null ? servico.getServicoOfertado().getCategoria() : null,
                servico.getValor(),
                servico.getAvaliacao(),
                servico.getComentarioAvaliacao(),
                pagamentoService.obterStatus(servico),
                clienteVendo ? servico.getPinConfirmacao() : null,
                servico.getEnderecoCliente() != null
                        ? EnderecoClienteResponse.de(servico.getEnderecoCliente())
                        : null,
                clienteVendo ? taxaCancelamentoService.retencaoPrevista(servico) : null,
                servico.getCriadoEm(),
                servico.getAceitoEm(),
                servico.getIniciadoEm(),
                servico.getConcluidoEm());
    }

    /**
     * Gera o código de confirmação do atendimento: quatro dígitos, com zeros à esquerda
     * preservados para que todos tenham o mesmo comprimento na tela.
     */
    private String gerarPin() {
        return String.format("%04d", ALEATORIO.nextInt(10000));
    }

    /**
     * Busca a solicitação exigindo que quem chama seja o cliente dono ou o prestador dono.
     *
     * <p>É a única checagem de "sou parte desta solicitação" do projeto, e é pública porque
     * o detalhe da solicitação, a conversa e o cancelamento partilham exatamente esse
     * critério — duplicá-la deixaria as três podendo divergir em silêncio.
     *
     * @param id                   identificador da solicitação
     * @param usuarioIdAutenticado id da conta autenticada
     * @param papelAutenticado     papel da conta autenticada
     * @return a solicitação, quando quem chama participa dela
     * @throws RecursoNaoEncontradoException se a solicitação não existir (HTTP 404)
     * @throws AcessoNegadoException         se quem chama não é cliente nem prestador dela (HTTP 403)
     */
    public FatoServico buscarEValidarParte(Long id, Long usuarioIdAutenticado, TipoUsuario papelAutenticado) {
        FatoServico servico = buscarSolicitacao(id);

        boolean donoComoCliente = papelAutenticado == TipoUsuario.CLIENTE
                && servico.getCliente() != null
                && servico.getCliente().getIdCliente().equals(usuarioIdAutenticado);
        boolean donoComoPrestador = papelAutenticado == TipoUsuario.PRESTADOR
                && servico.getPrestador() != null
                && servico.getPrestador().getIdPrestador().equals(usuarioIdAutenticado);

        if (!donoComoCliente && !donoComoPrestador) {
            throw new AcessoNegadoException("Você não participa desta solicitação");
        }
        return servico;
    }

    /**
     * Busca a solicitação por id.
     *
     * @param id identificador da solicitação
     * @return a solicitação encontrada
     * @throws RecursoNaoEncontradoException se não existir solicitação com esse id
     */
    private FatoServico buscarSolicitacao(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Solicitação não encontrada"));
    }

    private FatoServico buscarEValidarDonoPrestador(Long id, Long prestadorIdAutenticado) {
        FatoServico servico = buscarSolicitacao(id);
        if (!servico.getPrestador().getIdPrestador().equals(prestadorIdAutenticado)) {
            throw new AcessoNegadoException("Esta solicitação pertence a outro prestador");
        }
        return servico;
    }

    private FatoServico buscarEValidarDonoCliente(Long id, Long clienteIdAutenticado) {
        FatoServico servico = buscarSolicitacao(id);
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
