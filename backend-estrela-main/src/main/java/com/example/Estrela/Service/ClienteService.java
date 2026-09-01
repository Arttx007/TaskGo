package com.example.Estrela.Service;

import com.example.Estrela.DTO.AtualizarPerfilClienteRequest;
import com.example.Estrela.DTO.CadastroClienteRequest;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.exception.ArquivoInvalidoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.FatoServicoRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

/**
 * Cadastro do cliente e manutenção da própria conta: perfil, foto e desativação.
 *
 * <p>Existe como Service (em vez de o Controller acessar o repository direto, como antes)
 * porque há regras reais a aplicar: hashear a senha, manter o e-mail único entre as contas
 * de cliente e impedir que uma conta com atendimento em curso desapareça.
 */
@Service
public class ClienteService {

    /**
     * Estados em que a conta ainda tem compromisso aberto — atendimento combinado e
     * possivelmente dinheiro em custódia. Desativar aqui exigiria decidir se cancela e
     * estorna automaticamente, o que é decisão de produto sobre dinheiro de terceiro.
     */
    private static final Set<StatusSolicitacao> EM_ABERTO = Set.of(
            StatusSolicitacao.SOLICITADO, StatusSolicitacao.ACEITO, StatusSolicitacao.EM_ANDAMENTO);

    private final ClienteRepository clienteRepository;
    private final FatoServicoRepository fatoServicoRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    public ClienteService(ClienteRepository clienteRepository,
                          FatoServicoRepository fatoServicoRepository,
                          FileStorageService fileStorageService,
                          PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.fatoServicoRepository = fatoServicoRepository;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
    }

    public Cliente criar(CadastroClienteRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.nome());
        cliente.setIdade(request.idade());
        cliente.setCidade(request.cidade());
        cliente.setTipo_cliente(request.tipoCliente());
        cliente.setEmail(request.email());
        cliente.setSenha(passwordEncoder.encode(request.senha()));
        cliente.setAtivo(true);
        return clienteRepository.save(cliente);
    }

    public List<Cliente> listar() {
        return clienteRepository.findAll();
    }

    public Cliente buscarPorId(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado"));
    }

    /**
     * Atualiza o perfil do próprio cliente.
     *
     * <p>Não altera a senha, o saldo, o histórico nem as solicitações: o formulário de perfil
     * só mexe em dado cadastral.
     *
     * @param clienteId cliente autenticado
     * @param request   dados novos
     * @return o cliente atualizado
     * @throws RecursoNaoEncontradoException se a conta não existir (HTTP 404)
     * @throws EstadoInvalidoException       se o e-mail já pertencer a outra conta (HTTP 409)
     */
    @Transactional
    public Cliente atualizarPerfil(Long clienteId, AtualizarPerfilClienteRequest request) {
        Cliente cliente = buscarPorId(clienteId);

        exigirEmailDisponivel(request.email(), clienteId);

        cliente.setNome(request.nome());
        cliente.setEmail(request.email());
        cliente.setTelefone(vazioComoNulo(request.telefone()));
        cliente.setIdade(request.idade());
        cliente.setCidade(request.cidade());
        return clienteRepository.save(cliente);
    }

    /**
     * Substitui a foto de perfil do próprio cliente.
     *
     * @param clienteId cliente autenticado
     * @param foto      imagem enviada via multipart
     * @return o cliente com a referência da foto atualizada
     * @throws RecursoNaoEncontradoException se a conta não existir (HTTP 404)
     * @throws ArquivoInvalidoException      se o arquivo não for imagem aceita (HTTP 400)
     */
    @Transactional
    public Cliente atualizarFoto(Long clienteId, MultipartFile foto) {
        Cliente cliente = buscarPorId(clienteId);
        cliente.setFotoUrl(fileStorageService.storeImagem(foto, "clientes/" + clienteId));
        return clienteRepository.save(cliente);
    }

    /**
     * Desativa a própria conta.
     *
     * <p>Desativação lógica, não exclusão: apagar quebraria as FKs de {@code fato_servicos} e
     * {@code pagamento} e destruiria histórico financeiro do prestador. As avaliações escritas
     * pela conta continuam contando para a nota média de quem foi avaliado.
     *
     * @param clienteId cliente autenticado
     * @throws RecursoNaoEncontradoException se a conta não existir (HTTP 404)
     * @throws EstadoInvalidoException       se houver solicitação em aberto (HTTP 409)
     */
    @Transactional
    public void desativar(Long clienteId) {
        Cliente cliente = buscarPorId(clienteId);

        boolean temAtendimentoAberto = fatoServicoRepository.findByCliente_IdCliente(clienteId).stream()
                .anyMatch(s -> EM_ABERTO.contains(s.getStatus()));

        if (temAtendimentoAberto) {
            throw new EstadoInvalidoException(
                    "Não é possível excluir a conta com atendimento em aberto. Conclua ou cancele antes");
        }

        cliente.setAtivo(false);
        clienteRepository.save(cliente);
    }

    private void exigirEmailDisponivel(String email, Long clienteId) {
        clienteRepository.findByEmail(email).ifPresent(existente -> {
            if (!existente.getIdCliente().equals(clienteId)) {
                throw new EstadoInvalidoException("Este e-mail já está em uso por outra conta");
            }
        });
    }

    private String vazioComoNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }
}
