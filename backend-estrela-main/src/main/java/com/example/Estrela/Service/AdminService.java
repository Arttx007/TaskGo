package com.example.Estrela.Service;

import com.example.Estrela.Entity.ParametroNegocio;
import com.example.Estrela.Entity.Prestador;
import com.example.Estrela.Entity.StatusKyc;
import com.example.Estrela.exception.RecursoNaoEncontradoException;
import com.example.Estrela.repository.ParametroNegocioRepository;
import com.example.Estrela.repository.PrestadorRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

/**
 * Operações administrativas: aprovação/rejeição de KYC de prestadores e ajuste dos parâmetros de
 * negócio (RN01 e afins) sem deploy.
 */
@Service
public class AdminService {

    private final PrestadorRepository prestadorRepository;
    private final ParametroNegocioRepository parametroNegocioRepository;
    private final FileStorageService fileStorageService;

    public AdminService(PrestadorRepository prestadorRepository,
                         ParametroNegocioRepository parametroNegocioRepository,
                         FileStorageService fileStorageService) {
        this.prestadorRepository = prestadorRepository;
        this.parametroNegocioRepository = parametroNegocioRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<Prestador> listarPrestadoresPendentes() {
        return prestadorRepository.findAll().stream()
                .filter(p -> p.getStatusKyc() == StatusKyc.PENDENTE)
                .toList();
    }

    public Prestador aprovarKyc(Long prestadorId) {
        Prestador prestador = buscarPrestador(prestadorId);
        prestador.setStatusKyc(StatusKyc.APROVADO);
        return prestadorRepository.save(prestador);
    }

    public Prestador rejeitarKyc(Long prestadorId) {
        Prestador prestador = buscarPrestador(prestadorId);
        prestador.setStatusKyc(StatusKyc.REJEITADO);
        return prestadorRepository.save(prestador);
    }

    /**
     * Resolve o caminho absoluto de um documento de KYC de um prestador, para o admin conferir
     * antes de aprovar.
     *
     * @param tipo {@code "identidade"} ou {@code "pix"}
     * @throws RecursoNaoEncontradoException se o prestador ou o documento solicitado não existir
     */
    public Path caminhoDocumento(Long prestadorId, String tipo) {
        Prestador prestador = buscarPrestador(prestadorId);
        String caminhoRelativo = switch (tipo) {
            case "identidade" -> prestador.getDocumentoIdentidadeUrl();
            case "pix" -> prestador.getComprovantePixUrl();
            default -> null;
        };
        if (caminhoRelativo == null) {
            throw new RecursoNaoEncontradoException("Documento não encontrado para este prestador");
        }
        return fileStorageService.resolve(caminhoRelativo);
    }

    public List<ParametroNegocio> listarParametros() {
        return parametroNegocioRepository.findAll();
    }

    public ParametroNegocio atualizarParametro(String chave, BigDecimal valor) {
        ParametroNegocio parametro = parametroNegocioRepository.findById(chave)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Parâmetro não encontrado: " + chave));
        parametro.setValor(valor);
        return parametroNegocioRepository.save(parametro);
    }

    private Prestador buscarPrestador(Long prestadorId) {
        return prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestador não encontrado"));
    }
}
