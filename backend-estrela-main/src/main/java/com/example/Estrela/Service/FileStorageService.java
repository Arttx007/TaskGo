package com.example.Estrela.Service;

import com.example.Estrela.exception.ArquivoInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Armazena documentos de KYC (US-01) no filesystem local. O diretório é configurável
 * (`taskgo.storage.kyc-dir`) para permitir trocar por armazenamento em nuvem no futuro sem
 * mudar quem chama este serviço.
 */
@Service
public class FileStorageService {

    private static final Set<String> TIPOS_ACEITOS = Set.of("image/png", "image/jpeg", "application/pdf");

    private final Path diretorioBase;

    public FileStorageService(@Value("${taskgo.storage.kyc-dir}") String diretorioBase) {
        this.diretorioBase = Path.of(diretorioBase);
    }

    /**
     * Valida e persiste um arquivo enviado, em um subdiretório dedicado.
     *
     * @param file   arquivo enviado via multipart
     * @param subdir subdiretório de destino (ex.: id do prestador)
     * @return caminho relativo do arquivo salvo
     * @throws ArquivoInvalidoException se o tipo do arquivo não for aceito (HTTP 400)
     */
    public String store(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new ArquivoInvalidoException("Arquivo não enviado");
        }
        if (!TIPOS_ACEITOS.contains(file.getContentType())) {
            throw new ArquivoInvalidoException("Formato de arquivo não aceito. Envie PNG, JPEG ou PDF");
        }

        try {
            Path diretorioDestino = diretorioBase.resolve(subdir);
            Files.createDirectories(diretorioDestino);

            String extensao = extensaoPara(file.getContentType());
            String nomeArquivo = UUID.randomUUID() + extensao;
            Path destino = diretorioDestino.resolve(nomeArquivo);

            file.transferTo(destino);

            return diretorioDestino.relativize(destino).toString().isEmpty()
                    ? nomeArquivo
                    : subdir + "/" + nomeArquivo;
        } catch (IOException e) {
            throw new ArquivoInvalidoException("Falha ao salvar o arquivo enviado");
        }
    }

    /**
     * Resolve um caminho relativo salvo anteriormente por {@link #store} para um caminho absoluto,
     * usado ao servir o arquivo de volta (ex.: admin conferindo um documento de KYC).
     */
    public Path resolve(String caminhoRelativo) {
        return diretorioBase.resolve(caminhoRelativo);
    }

    private String extensaoPara(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}
