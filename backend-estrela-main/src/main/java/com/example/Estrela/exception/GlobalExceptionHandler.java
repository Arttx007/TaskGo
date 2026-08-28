package com.example.Estrela.exception;

import com.example.Estrela.DTO.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centraliza o tratamento de exceções da API, convertendo cada uma em um {@link ErrorResponse}
 * consistente em vez do 500 genérico que o Spring devolve para exceções não tratadas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErrorResponse> tratarCredenciaisInvalidas(CredenciaisInvalidasException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS", ex.getMessage(), request, null);
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> tratarNaoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErrorResponse> tratarAcessoNegado(AcessoNegadoException ex, HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, "ACESSO_NEGADO", ex.getMessage(), request, null);
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> tratarEstadoInvalido(EstadoInvalidoException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, "ESTADO_INVALIDO", ex.getMessage(), request, null);
    }

    @ExceptionHandler(RecursoIndisponivelException.class)
    public ResponseEntity<ErrorResponse> tratarRecursoIndisponivel(RecursoIndisponivelException ex, HttpServletRequest request) {
        return construir(HttpStatus.valueOf(422), "RECURSO_INDISPONIVEL", ex.getMessage(), request, null);
    }

    @ExceptionHandler(KycPendenteException.class)
    public ResponseEntity<ErrorResponse> tratarKycPendente(KycPendenteException ex, HttpServletRequest request) {
        return construir(HttpStatus.valueOf(422), "KYC_PENDENTE", ex.getMessage(), request, null);
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<ErrorResponse> tratarSaldoInsuficiente(SaldoInsuficienteException ex, HttpServletRequest request) {
        return construir(HttpStatus.valueOf(422), "SALDO_INSUFICIENTE", ex.getMessage(), request, null);
    }

    @ExceptionHandler(PagamentoRecusadoException.class)
    public ResponseEntity<ErrorResponse> tratarPagamentoRecusado(PagamentoRecusadoException ex, HttpServletRequest request) {
        return construir(HttpStatus.PAYMENT_REQUIRED, "PAGAMENTO_RECUSADO", ex.getMessage(), request, null);
    }

    @ExceptionHandler(ArquivoInvalidoException.class)
    public ResponseEntity<ErrorResponse> tratarArquivoInvalido(ArquivoInvalidoException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "ARQUIVO_INVALIDO", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> tratarValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(erro.getField(), erro.getDefaultMessage());
        }
        return construir(HttpStatus.BAD_REQUEST, "VALIDACAO", "Dados inválidos", request, fieldErrors);
    }

    /**
     * Parâmetro de consulta obrigatório ausente.
     *
     * <p>Sem este tratamento a exceção caía no {@code @ExceptionHandler(Exception.class)} abaixo e
     * virava 500 — o catch-all engolia o 400 que o Spring devolveria por conta própria, fazendo uma
     * requisição malformada do cliente parecer falha do servidor.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> tratarParametroObrigatorioAusente(MissingServletRequestParameterException ex,
                                                                            HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        fieldErrors.put(ex.getParameterName(), "Parâmetro obrigatório");
        return construir(HttpStatus.BAD_REQUEST, "VALIDACAO", "Dados inválidos", request, fieldErrors);
    }

    /**
     * Combinação de parâmetros inconsistente, recusada por regra de negócio.
     */
    @ExceptionHandler(ValidacaoException.class)
    public ResponseEntity<ErrorResponse> tratarValidacaoDeNegocio(ValidacaoException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDACAO", ex.getMessage(), request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> tratarErroGenerico(Exception ex, HttpServletRequest request) {
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Ocorreu um erro inesperado", request, null);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String codigo, String mensagem,
                                                      HttpServletRequest request, Map<String, String> fieldErrors) {
        ErrorResponse corpo = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                codigo,
                mensagem,
                request.getRequestURI(),
                fieldErrors == null ? Map.of() : fieldErrors
        );
        return ResponseEntity.status(status).body(corpo);
    }
}
