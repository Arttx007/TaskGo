package com.example.Estrela.DTO;

/**
 * @param motivo motivo da rejeição de KYC (usado apenas em {@code PUT /admin/prestadores/{id}/kyc/rejeitar})
 */
public record KycDecisionRequest(String motivo) {
}
