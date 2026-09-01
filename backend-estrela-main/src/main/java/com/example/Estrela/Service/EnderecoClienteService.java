package com.example.Estrela.Service;

import com.example.Estrela.DTO.EnderecoClienteRequest;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.EnderecoCliente;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.EnderecoClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Endereços de atendimento de um cliente.
 *
 * <p>Duas invariantes vivem aqui, e não no banco:
 *
 * <ul>
 *   <li><b>Um único endereço padrão por conta.</b> Garantida ao gravar, e não por restrição
 *       de unicidade, porque a condição é "no máximo um com padrao = true por cliente",
 *       que exigiria índice parcial e não é portável entre Postgres e H2.</li>
 *   <li><b>Remoção é lógica.</b> Um endereço já referenciado por uma solicitação não pode
 *       desaparecer: a FK precisa continuar resolvendo para o histórico não perder o local
 *       onde o atendimento foi combinado.</li>
 * </ul>
 */
@Service
public class EnderecoClienteService {

    private final EnderecoClienteRepository enderecoRepository;
    private final ClienteService clienteService;

    public EnderecoClienteService(EnderecoClienteRepository enderecoRepository,
                                  ClienteService clienteService) {
        this.enderecoRepository = enderecoRepository;
        this.clienteService = clienteService;
    }

    /**
     * Endereços visíveis do cliente, do mais antigo para o mais recente.
     *
     * @param clienteId cliente autenticado
     * @return endereços ativos, lista vazia quando ele não cadastrou nenhum
     */
    public List<EnderecoCliente> listar(Long clienteId) {
        return enderecoRepository.findByCliente_IdClienteAndAtivoTrueOrderByIdAsc(clienteId);
    }

    /**
     * Cadastra um endereço para o cliente.
     *
     * <p>O primeiro endereço de uma conta nasce padrão, qualquer que seja o pedido: uma conta
     * com endereços mas sem padrão deixaria a busca sem localidade de referência.
     *
     * @param clienteId cliente autenticado
     * @param request   dados do endereço
     * @return o endereço criado
     * @throws RecursoNaoEncontradoException se o cliente não existir (HTTP 404)
     */
    @Transactional
    public EnderecoCliente criar(Long clienteId, EnderecoClienteRequest request) {
        Cliente cliente = clienteService.buscarPorId(clienteId);

        boolean primeiro = listar(clienteId).isEmpty();
        boolean deveSerPadrao = primeiro || Boolean.TRUE.equals(request.padrao());

        EnderecoCliente endereco = new EnderecoCliente();
        endereco.setCliente(cliente);
        aplicar(endereco, request);
        endereco.setAtivo(true);
        endereco.setPadrao(deveSerPadrao);

        EnderecoCliente salvo = enderecoRepository.save(endereco);

        if (deveSerPadrao) {
            desmarcarOutrosPadroes(clienteId, salvo.getId());
        }
        return salvo;
    }

    /**
     * Atualiza um endereço do próprio cliente.
     *
     * @param clienteId  cliente autenticado
     * @param enderecoId endereço a alterar
     * @param request    dados novos
     * @return o endereço atualizado
     * @throws RecursoNaoEncontradoException se o endereço não existir (HTTP 404)
     * @throws AcessoNegadoException         se o endereço pertencer a outra conta (HTTP 403)
     */
    @Transactional
    public EnderecoCliente atualizar(Long clienteId, Long enderecoId, EnderecoClienteRequest request) {
        EnderecoCliente endereco = buscarEValidarDono(clienteId, enderecoId);

        aplicar(endereco, request);

        if (Boolean.TRUE.equals(request.padrao())) {
            endereco.setPadrao(true);
        }

        EnderecoCliente salvo = enderecoRepository.save(endereco);

        if (Boolean.TRUE.equals(salvo.getPadrao())) {
            desmarcarOutrosPadroes(clienteId, salvo.getId());
        }
        return salvo;
    }

    /**
     * Remove logicamente um endereço do próprio cliente.
     *
     * <p>Se o removido era o padrão, o mais antigo dos restantes assume, para que a conta não
     * fique com endereços e nenhum padrão.
     *
     * @param clienteId  cliente autenticado
     * @param enderecoId endereço a remover
     * @throws RecursoNaoEncontradoException se o endereço não existir (HTTP 404)
     * @throws AcessoNegadoException         se o endereço pertencer a outra conta (HTTP 403)
     */
    @Transactional
    public void remover(Long clienteId, Long enderecoId) {
        EnderecoCliente endereco = buscarEValidarDono(clienteId, enderecoId);

        boolean eraPadrao = Boolean.TRUE.equals(endereco.getPadrao());
        endereco.setAtivo(false);
        endereco.setPadrao(false);
        enderecoRepository.save(endereco);

        if (eraPadrao) {
            listar(clienteId).stream().findFirst().ifPresent(proximo -> {
                proximo.setPadrao(true);
                enderecoRepository.save(proximo);
            });
        }
    }

    /**
     * Endereço padrão do cliente, usado pela busca quando o navegador não informa posição.
     *
     * @param clienteId cliente autenticado
     * @return o endereço padrão, ou {@code null} quando a conta não tem endereço
     */
    public EnderecoCliente buscarPadrao(Long clienteId) {
        return enderecoRepository.findByCliente_IdClienteAndPadraoTrueAndAtivoTrue(clienteId)
                .orElse(null);
    }

    /**
     * Localiza um endereço exigindo que pertença ao cliente informado.
     *
     * <p>Aceita endereço removido logicamente, porque uma solicitação antiga pode referenciá-lo
     * e o histórico precisa continuar legível.
     *
     * @param clienteId  cliente autenticado
     * @param enderecoId endereço procurado
     * @return o endereço, quando pertence ao cliente
     * @throws RecursoNaoEncontradoException se não existir (HTTP 404)
     * @throws AcessoNegadoException         se pertencer a outra conta (HTTP 403)
     */
    public EnderecoCliente buscarEValidarDono(Long clienteId, Long enderecoId) {
        EnderecoCliente endereco = enderecoRepository.findById(enderecoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Endereço não encontrado"));

        if (!endereco.getCliente().getIdCliente().equals(clienteId)) {
            throw new AcessoNegadoException("Este endereço não pertence à sua conta");
        }
        return endereco;
    }

    private void desmarcarOutrosPadroes(Long clienteId, Long manterId) {
        enderecoRepository.findByCliente_IdCliente(clienteId).stream()
                .filter(e -> !e.getId().equals(manterId))
                .filter(e -> Boolean.TRUE.equals(e.getPadrao()))
                .forEach(e -> {
                    e.setPadrao(false);
                    enderecoRepository.save(e);
                });
    }

    private void aplicar(EnderecoCliente endereco, EnderecoClienteRequest request) {
        endereco.setApelido(request.apelido());
        endereco.setCep(request.cep());
        endereco.setRua(request.rua());
        endereco.setNumero(request.numero());
        endereco.setComplemento(request.complemento());
        endereco.setBairro(request.bairro());
        endereco.setCidade(request.cidade());
        endereco.setUf(request.uf().toUpperCase());
        endereco.setLatitude(request.latitude());
        endereco.setLongitude(request.longitude());
    }
}
