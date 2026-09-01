package com.example.Estrela.Service;

import com.example.Estrela.DTO.AtualizarPerfilClienteRequest;
import com.example.Estrela.Entity.Cliente;
import com.example.Estrela.Entity.FatoServico;
import com.example.Estrela.Entity.StatusSolicitacao;
import com.example.Estrela.exception.ArquivoInvalidoException;
import com.example.Estrela.exception.EstadoInvalidoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.ClienteRepository;
import com.example.Estrela.repository.FatoServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Manutenção da própria conta pelo cliente: perfil, foto e desativação.
 */
@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private FatoServicoRepository fatoServicoRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClienteService clienteService;

    private Cliente cliente;

    @BeforeEach
    void setUp() {
        cliente = new Cliente();
        cliente.setIdCliente(1L);
        cliente.setNome("Nome Antigo");
        cliente.setEmail("antigo@teste.com");
        cliente.setSenha("hash");
        cliente.setAtivo(true);

        lenient().when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void atualizaPerfilComDadosNovos() {
        when(clienteRepository.findByEmail("novo@teste.com")).thenReturn(Optional.empty());

        Cliente atualizado = clienteService.atualizarPerfil(1L, new AtualizarPerfilClienteRequest(
                "Nome Novo", "novo@teste.com", "(81) 99999-8888", 30, "Recife"));

        assertThat(atualizado.getNome()).isEqualTo("Nome Novo");
        assertThat(atualizado.getEmail()).isEqualTo("novo@teste.com");
        assertThat(atualizado.getTelefone()).isEqualTo("(81) 99999-8888");
        assertThat(atualizado.getCidade()).isEqualTo("Recife");
    }

    @Test
    void manterOProprioEmailNaoEConsideradoDuplicado() {
        when(clienteRepository.findByEmail("antigo@teste.com")).thenReturn(Optional.of(cliente));

        Cliente atualizado = clienteService.atualizarPerfil(1L, new AtualizarPerfilClienteRequest(
                "Nome Novo", "antigo@teste.com", null, null, null));

        assertThat(atualizado.getNome()).isEqualTo("Nome Novo");
    }

    @Test
    void emailDeOutraContaERecusadoENadaEAlterado() {
        Cliente outra = new Cliente();
        outra.setIdCliente(2L);
        outra.setEmail("ocupado@teste.com");
        when(clienteRepository.findByEmail("ocupado@teste.com")).thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> clienteService.atualizarPerfil(1L, new AtualizarPerfilClienteRequest(
                "Nome Novo", "ocupado@teste.com", null, null, null)))
                .isInstanceOf(EstadoInvalidoException.class);

        assertThat(cliente.getNome()).isEqualTo("Nome Antigo");
        assertThat(cliente.getEmail()).isEqualTo("antigo@teste.com");
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    void telefoneVazioEGravadoComoNulo() {
        when(clienteRepository.findByEmail("antigo@teste.com")).thenReturn(Optional.of(cliente));

        Cliente atualizado = clienteService.atualizarPerfil(1L, new AtualizarPerfilClienteRequest(
                "Nome", "antigo@teste.com", "   ", null, null));

        assertThat(atualizado.getTelefone()).isNull();
    }

    @Test
    void perfilDeContaInexistenteE404() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clienteService.atualizarPerfil(99L, new AtualizarPerfilClienteRequest(
                "Nome", "x@teste.com", null, null, null)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void fotoEnviadaSubstituiAReferenciaAnterior() {
        cliente.setFotoUrl("clientes/1/antiga.png");
        MockMultipartFile foto = new MockMultipartFile("foto", "nova.png", "image/png", new byte[]{1});
        when(fileStorageService.storeImagem(eq(foto), eq("clientes/1"))).thenReturn("clientes/1/nova.png");

        Cliente atualizado = clienteService.atualizarFoto(1L, foto);

        assertThat(atualizado.getFotoUrl()).isEqualTo("clientes/1/nova.png");
    }

    @Test
    void arquivoDeTipoNaoAceitoERecusado() {
        MockMultipartFile pdf = new MockMultipartFile("foto", "doc.pdf", "application/pdf", new byte[]{1});
        when(fileStorageService.storeImagem(any(), any()))
                .thenThrow(new ArquivoInvalidoException("Formato de imagem não aceito"));

        assertThatThrownBy(() -> clienteService.atualizarFoto(1L, pdf))
                .isInstanceOf(ArquivoInvalidoException.class);

        assertThat(cliente.getFotoUrl()).isNull();
    }

    @Test
    void desativaContaSemAtendimentoAberto() {
        when(fatoServicoRepository.findByCliente_IdCliente(1L)).thenReturn(List.of(
                solicitacaoEm(StatusSolicitacao.AVALIADO),
                solicitacaoEm(StatusSolicitacao.CANCELADO)));

        clienteService.desativar(1L);

        assertThat(cliente.getAtivo()).isFalse();
    }

    @Test
    void desativacaoERecusadaComAtendimentoEmAberto() {
        when(fatoServicoRepository.findByCliente_IdCliente(1L)).thenReturn(List.of(
                solicitacaoEm(StatusSolicitacao.EM_ANDAMENTO)));

        assertThatThrownBy(() -> clienteService.desativar(1L))
                .isInstanceOf(EstadoInvalidoException.class);

        assertThat(cliente.getAtivo()).isTrue();
    }

    @Test
    void desativacaoERecusadaComSolicitacaoAceita() {
        when(fatoServicoRepository.findByCliente_IdCliente(1L)).thenReturn(List.of(
                solicitacaoEm(StatusSolicitacao.ACEITO)));

        assertThatThrownBy(() -> clienteService.desativar(1L))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void desativacaoERecusadaComSolicitacaoAguardandoResposta() {
        when(fatoServicoRepository.findByCliente_IdCliente(1L)).thenReturn(List.of(
                solicitacaoEm(StatusSolicitacao.SOLICITADO)));

        assertThatThrownBy(() -> clienteService.desativar(1L))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    private FatoServico solicitacaoEm(StatusSolicitacao status) {
        FatoServico servico = new FatoServico();
        servico.setStatus(status);
        return servico;
    }
}
