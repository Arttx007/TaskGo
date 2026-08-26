package com.example.Estrela.Entity;

/**
 * Ciclo de vida de uma solicitação de serviço ({@link FatoServico}), conforme RN02:
 * SOLICITADO -&gt; ACEITO -&gt; CONCLUIDO -&gt; AVALIADO, com ramos para RECUSADO (pelo prestador)
 * e CANCELADO (pelo cliente ou prestador).
 */
public enum StatusSolicitacao {
    SOLICITADO,
    ACEITO,
    RECUSADO,
    CANCELADO,
    CONCLUIDO,
    AVALIADO
}
