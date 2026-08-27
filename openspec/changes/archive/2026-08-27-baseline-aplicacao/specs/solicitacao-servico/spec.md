## Purpose

Cobre o ciclo de vida de uma solicitação de serviço, do pedido do cliente até a avaliação, incluindo recusa e cancelamento, e quem pode executar cada transição.

## ADDED Requirements

### Requirement: Ciclo de vida da solicitação

Uma solicitação SHALL percorrer os estados `SOLICITADO` para `ACEITO`, `ACEITO` para `CONCLUIDO` e `CONCLUIDO` para `AVALIADO`, com os ramos `SOLICITADO` para `RECUSADO` e a passagem para `CANCELADO`. O sistema MUST recusar qualquer transição fora dessas, informando o estado atual.

#### Scenario: Transição inválida para o estado atual
- **WHEN** uma transição é solicitada a partir de um estado que não a permite, por exemplo aceitar uma solicitação já aceita
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e a solicitação permanece no estado em que estava

### Requirement: Abertura de solicitação pelo cliente

Um cliente autenticado SHALL abrir uma solicitação via `POST /servicos`, informando o serviço ofertado desejado. A solicitação SHALL nascer no estado `SOLICITADO`, herdando do serviço ofertado o prestador, a localização e o valor vigente no momento do pedido.

#### Scenario: Solicitação aberta com sucesso
- **WHEN** um cliente autenticado solicita um serviço ativo de um prestador aprovado
- **THEN** a resposta é 201, o estado é `SOLICITADO` e o valor registrado é o preço do serviço no momento do pedido

#### Scenario: Confirmação da solicitação usa diálogos nativos do navegador
- **WHEN** o cliente aciona "Solicitar" na tela de busca
- **THEN** a confirmação e o aviso de sucesso são apresentados como diálogos nativos do navegador, bloqueantes, e não como elementos da própria página

#### Scenario: Serviço indisponível no momento do pedido
- **WHEN** o serviço escolhido está inativo, ou o prestador dono não está com verificação aprovada
- **THEN** a resposta é 422 com código `RECURSO_INDISPONIVEL` e nenhuma solicitação é criada

#### Scenario: Solicitação duplicada para o mesmo prestador
- **WHEN** o cliente já tem uma solicitação em `SOLICITADO` ou `ACEITO` com aquele mesmo prestador e abre outra
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e nenhuma solicitação nova é criada

#### Scenario: Serviço ofertado inexistente
- **WHEN** a solicitação referencia um serviço que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Consulta das próprias solicitações

O sistema SHALL devolver em `GET /servicos/minhas` as solicitações da conta autenticada, interpretando o papel do token: um prestador recebe as solicitações dirigidas a ele; um cliente recebe as que ele abriu. Cada item SHALL trazer o estado da solicitação e o estado do pagamento associado, quando houver.

#### Scenario: Prestador consulta suas solicitações
- **WHEN** um prestador autenticado consulta suas solicitações
- **THEN** recebe as solicitações dirigidas a ele, e nenhuma de outro prestador

#### Scenario: Cliente consulta suas solicitações
- **WHEN** um cliente autenticado consulta suas solicitações
- **THEN** recebe as que ele mesmo abriu, cada uma com o estado do pagamento correspondente ou sem estado de pagamento quando ainda não houve pagamento

### Requirement: Aceite e recusa pelo prestador

O prestador dono SHALL aceitar (`PUT /servicos/{id}/aceitar`) ou recusar (`PUT /servicos/{id}/recusar`) uma solicitação que esteja em `SOLICITADO`.

#### Scenario: Aceite pelo prestador dono
- **WHEN** o prestador dono aceita uma solicitação em `SOLICITADO`
- **THEN** o estado passa a `ACEITO`

#### Scenario: Recusa pelo prestador dono
- **WHEN** o prestador dono recusa uma solicitação em `SOLICITADO`
- **THEN** o estado passa a `RECUSADO`, encerrando a solicitação

#### Scenario: Aceite por prestador que não é o dono
- **WHEN** um prestador tenta aceitar uma solicitação dirigida a outro prestador
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e a solicitação permanece em `SOLICITADO`

### Requirement: Conclusão pelo prestador depende de pagamento em custódia

O prestador dono SHALL concluir a solicitação via `PUT /servicos/{id}/concluir` somente quando ela estiver em `ACEITO` e houver pagamento retido em custódia. A conclusão SHALL liberar o valor líquido ao prestador antes de registrar o novo estado.

#### Scenario: Conclusão com pagamento retido
- **WHEN** o prestador dono conclui uma solicitação aceita e paga
- **THEN** o estado passa a `CONCLUIDO` e o valor líquido é creditado no saldo do prestador

#### Scenario: Conclusão sem pagamento em custódia
- **WHEN** o prestador dono tenta concluir uma solicitação aceita para a qual não há pagamento retido
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO`, o estado permanece `ACEITO` e nenhum saldo é creditado

### Requirement: Avaliação pelo cliente após a conclusão

O cliente dono SHALL avaliar a solicitação via `PUT /servicos/{id}/avaliar`, informando nota de 1 a 5 e comentário opcional, somente quando ela estiver em `CONCLUIDO`. A avaliação SHALL atualizar a nota média do prestador, considerando todas as suas solicitações avaliadas.

#### Scenario: Avaliação de solicitação concluída
- **WHEN** o cliente dono envia nota entre 1 e 5 para uma solicitação concluída
- **THEN** o estado passa a `AVALIADO`, a nota e o comentário ficam registrados e a nota média do prestador é recalculada

#### Scenario: Nota fora da faixa permitida
- **WHEN** a avaliação envia nota menor que 1, maior que 5 ou ausente
- **THEN** a resposta é 400 com código `VALIDACAO` e a solicitação não é avaliada

#### Scenario: Widget de estrelas envia nota invertida
- **WHEN** o cliente clica na estrela mais à direita do seletor de avaliação, posição que a convenção corrente associa à melhor nota
- **THEN** a nota registrada é 1, e clicar na estrela mais à esquerda registra 5: o seletor é exibido em ordem invertida em relação à ordem em que as estrelas são contadas

#### Scenario: Avaliação repetida ou fora de hora
- **WHEN** o cliente tenta avaliar uma solicitação que já foi avaliada, ou que ainda não foi concluída
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO`

### Requirement: Superfícies decorativas nos painéis

Os painéis SHALL exibir, ao lado dos fluxos reais, superfícies que imitam etapas do ciclo de atendimento sem executar operação alguma. Essas superfícies MUST NOT ser confundidas com os fluxos reais: elas não realizam nenhuma chamada à API e nada do que exibem é persistido.

#### Scenario: Áreas decorativas do painel do cliente
- **WHEN** o cliente usa o checkout de demonstração, o chat, o recibo ou o simulador de custo
- **THEN** nenhuma requisição de rede é feita e nenhum estado é alterado no servidor, embora a interface apresente confirmações de sucesso

#### Scenario: Indicadores fixos nos painéis
- **WHEN** o painel do prestador exibe visitas no perfil e taxa de conversão, ou o painel do cliente exibe a lista de atividades recentes
- **THEN** os valores mostrados são fixos no próprio HTML e não refletem dado algum da conta autenticada

### Requirement: Cancelamento por qualquer uma das partes

Cliente dono ou prestador dono SHALL cancelar a solicitação via `PUT /servicos/{id}/cancelar` enquanto ela não estiver concluída ou avaliada. Se houver pagamento retido no momento do cancelamento, ele SHALL ser estornado integralmente.

#### Scenario: Cancelamento antes do aceite
- **WHEN** o cliente dono cancela uma solicitação ainda em `SOLICITADO`
- **THEN** o estado passa a `CANCELADO`

#### Scenario: Cancelamento após o aceite, com pagamento retido
- **WHEN** cliente ou prestador dono cancela uma solicitação em `ACEITO` cujo pagamento está retido
- **THEN** o estado passa a `CANCELADO` e o valor retido é estornado integralmente, sem retenção de taxa

#### Scenario: Cancelamento de solicitação já encerrada
- **WHEN** alguém tenta cancelar uma solicitação em `CONCLUIDO` ou `AVALIADO`
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e o estado permanece o mesmo

#### Scenario: Cancelamento por quem não participa
- **WHEN** uma conta que não é o cliente nem o prestador da solicitação tenta cancelá-la
- **THEN** a resposta é 403 com código `ACESSO_NEGADO`
