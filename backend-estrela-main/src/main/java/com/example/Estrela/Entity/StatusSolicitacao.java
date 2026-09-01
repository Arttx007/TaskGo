package com.example.Estrela.Entity;

/**
 * Ciclo de vida de uma solicitação de serviço ({@link FatoServico}), conforme RN02:
 * SOLICITADO -&gt; ACEITO -&gt; EM_ANDAMENTO -&gt; CONCLUIDO -&gt; AVALIADO, com ramos para
 * RECUSADO (pelo prestador) e CANCELADO (pelo cliente ou prestador).
 *
 * <p>A passagem de ACEITO para EM_ANDAMENTO depende do código de confirmação entregue
 * ao cliente e não é contornável: não existe transição direta de ACEITO para CONCLUIDO.
 */
public enum StatusSolicitacao {
    SOLICITADO,
    ACEITO,
    EM_ANDAMENTO,
    RECUSADO,
    CANCELADO,
    CONCLUIDO,
    AVALIADO
}
