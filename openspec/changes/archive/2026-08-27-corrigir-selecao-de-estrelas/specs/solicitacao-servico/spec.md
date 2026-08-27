## MODIFIED Requirements

### Requirement: Avaliação pelo cliente após a conclusão

O cliente dono SHALL avaliar a solicitação via `PUT /servicos/{id}/avaliar`, informando nota de 1 a 5 e comentário opcional, somente quando ela estiver em `CONCLUIDO`. A avaliação SHALL atualizar a nota média do prestador, considerando todas as suas solicitações avaliadas.

O seletor de estrelas da interface SHALL registrar a nota correspondente à posição clicada, seguindo a convenção corrente: a estrela mais à esquerda vale 1 e a mais à direita vale 5. O preenchimento visual SHALL refletir a seleção, acendendo da primeira estrela até a clicada e nenhuma além dela.

#### Scenario: Avaliação de solicitação concluída
- **WHEN** o cliente dono envia nota entre 1 e 5 para uma solicitação concluída
- **THEN** o estado passa a `AVALIADO`, a nota e o comentário ficam registrados e a nota média do prestador é recalculada

#### Scenario: Nota fora da faixa permitida
- **WHEN** a avaliação envia nota menor que 1, maior que 5 ou ausente
- **THEN** a resposta é 400 com código `VALIDACAO` e a solicitação não é avaliada

#### Scenario: Nota registrada ao clicar numa estrela
- **WHEN** o cliente clica na estrela mais à direita do seletor de avaliação, posição que a convenção corrente associa à melhor nota
- **THEN** a nota registrada é 5, e clicar na mais à esquerda registra 1, com as posições intermediárias seguindo a mesma ordem

#### Scenario: Preenchimento visual reflete a seleção
- **WHEN** o cliente clica em uma estrela qualquer do seletor
- **THEN** acendem apenas as estrelas da primeira até a clicada, e nenhuma além dela, permitindo conferir a nota antes de enviar

#### Scenario: Avaliação repetida ou fora de hora
- **WHEN** o cliente tenta avaliar uma solicitação que já foi avaliada, ou que ainda não foi concluída
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO`
