package com.example.Estrela.DTO;

import com.example.Estrela.Entity.EnderecoCliente;

/**
 * Endereço de atendimento devolvido ao cliente dono.
 *
 * @param id          identificador do endereço
 * @param apelido     nome curto dado pelo cliente
 * @param cep         CEP
 * @param rua         logradouro
 * @param numero      número
 * @param complemento complemento, ou {@code null}
 * @param bairro      bairro
 * @param cidade      cidade
 * @param uf          sigla do estado
 * @param latitude    latitude, ou {@code null} quando desconhecida
 * @param longitude   longitude, ou {@code null} quando desconhecida
 * @param padrao      se é o endereço padrão da conta
 */
public record EnderecoClienteResponse(Long id,
                                      String apelido,
                                      String cep,
                                      String rua,
                                      String numero,
                                      String complemento,
                                      String bairro,
                                      String cidade,
                                      String uf,
                                      Double latitude,
                                      Double longitude,
                                      Boolean padrao) {

    /**
     * Converte a entidade em resposta.
     *
     * @param e endereço persistido
     * @return a resposta correspondente
     */
    public static EnderecoClienteResponse de(EnderecoCliente e) {
        return new EnderecoClienteResponse(e.getId(), e.getApelido(), e.getCep(), e.getRua(),
                e.getNumero(), e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf(),
                e.getLatitude(), e.getLongitude(), e.getPadrao());
    }
}
