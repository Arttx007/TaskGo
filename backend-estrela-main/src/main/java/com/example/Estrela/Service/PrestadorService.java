package com.example.Estrela.Service;

import com.example.Estrela.DTO.CadastroPrestadorRequest;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.exception.AcessoNegadoException;
import com.example.Estrela.exception.ArquivoInvalidoException;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.PrestadorRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cadastro, consulta e verificação (KYC) de prestadores.
 */
@Service
public class PrestadorService {

    private final PrestadorRepository prestadorRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public PrestadorService(PrestadorRepository prestadorRepository,
                             PasswordEncoder passwordEncoder,
                             FileStorageService fileStorageService) {
        this.prestadorRepository = prestadorRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    public Prestador criar(CadastroPrestadorRequest request) {
        Prestador prestador = new Prestador();
        prestador.setNome(request.nome());
        prestador.setEspecialidade(request.especialidade());
        prestador.setCidade(request.cidade());
        prestador.setEmail(request.email());
        prestador.setSenha(passwordEncoder.encode(request.senha()));
        prestador.setStatusKyc(StatusKyc.PENDENTE);
        prestador.setSaldoDisponivel(BigDecimal.ZERO);
        return prestadorRepository.save(prestador);
    }

    public List<Prestador> listar() {
        return prestadorRepository.findAll();
    }

    public Prestador buscarPorId(Long id) {
        return prestadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestador não encontrado"));
    }

    /**
     * Recebe os documentos de KYC do prestador (US-01). Sempre volta o cadastro para PENDENTE —
     * inclusive no reenvio após uma rejeição, para que o admin reavalie.
     *
     * @throws AcessoNegadoException    se {@code prestadorIdAutenticado} não for o dono do cadastro
     * @throws ArquivoInvalidoException se algum arquivo tiver tipo/tamanho inválido
     */
    public Prestador enviarDocumentos(Long prestadorId, Long prestadorIdAutenticado,
                                       MultipartFile documentoIdentidade, MultipartFile comprovantePix) {
        if (!prestadorId.equals(prestadorIdAutenticado)) {
            throw new AcessoNegadoException("Você só pode enviar documentos para o seu próprio cadastro");
        }
        Prestador prestador = buscarPorId(prestadorId);

        String caminhoIdentidade = fileStorageService.store(documentoIdentidade, String.valueOf(prestadorId));
        String caminhoPix = fileStorageService.store(comprovantePix, String.valueOf(prestadorId));

        prestador.setDocumentoIdentidadeUrl(caminhoIdentidade);
        prestador.setComprovantePixUrl(caminhoPix);
        prestador.setStatusKyc(StatusKyc.PENDENTE);

        return prestadorRepository.save(prestador);
    }

    public BigDecimal obterSaldo(Long prestadorId, Long prestadorIdAutenticado) {
        if (!prestadorId.equals(prestadorIdAutenticado)) {
            throw new AcessoNegadoException("Você só pode consultar o próprio saldo");
        }
        return buscarPorId(prestadorId).getSaldoDisponivel();
    }
}
