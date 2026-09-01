package com.example.Estrela.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Endereço de atendimento informado pelo cliente.
 *
 * <p>{@code padrao} é uma intenção, não um fato: marcar este endereço como padrão faz o
 * anterior deixar de ser, invariante garantida no serviço. O primeiro endereço de uma
 * conta nasce padrão independentemente do que vier aqui.
 *
 * <p>As coordenadas são opcionais. Sem elas, a busca por proximidade e a apuração da taxa
 * de cancelamento por distância caem para o comportamento sem distância, nunca falham.
 *
 * @param apelido     nome curto para reconhecer o endereço, obrigatório
 * @param cep         CEP no formato 00000-000 ou 00000000, obrigatório
 * @param rua         logradouro, obrigatório
 * @param numero      número, obrigatório
 * @param complemento complemento, opcional
 * @param bairro      bairro, obrigatório
 * @param cidade      cidade, obrigatória
 * @param uf          sigla do estado, duas letras, obrigatória
 * @param latitude    latitude, opcional
 * @param longitude   longitude, opcional
 * @param padrao      pedido para tornar este o endereço padrão da conta
 */
public record EnderecoClienteRequest(
        @NotBlank(message = "apelido é obrigatório") String apelido,
        @NotBlank(message = "cep é obrigatório")
        @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "cep inválido") String cep,
        @NotBlank(message = "rua é obrigatória") String rua,
        @NotBlank(message = "numero é obrigatório") String numero,
        String complemento,
        @NotBlank(message = "bairro é obrigatório") String bairro,
        @NotBlank(message = "cidade é obrigatória") String cidade,
        @NotBlank(message = "uf é obrigatória")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "uf inválida") String uf,
        Double latitude,
        Double longitude,
        Boolean padrao
) {
}
