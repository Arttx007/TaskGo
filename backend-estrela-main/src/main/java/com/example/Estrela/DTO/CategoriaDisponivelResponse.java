package com.example.Estrela.DTO;

/**
 * Categoria com ao menos um serviço disponível ao público, com a quantidade de ofertas.
 *
 * <p>A categoria é texto livre digitado pelo prestador ao publicar — não existe enum nem tabela de
 * categorias no modelo. Por isso este catálogo reflete o que foi efetivamente cadastrado, e não uma
 * lista fixa.
 *
 * @param categoria      nome da categoria como cadastrado pelo prestador
 * @param totalServicos  quantidade de serviços `ATIVO` de prestador `APROVADO` nessa categoria (RN04)
 */
public record CategoriaDisponivelResponse(String categoria, long totalServicos) {
}
