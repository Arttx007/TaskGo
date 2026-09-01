## ADDED Requirements

### Requirement: Consulta de uma solicitação específica

O sistema SHALL devolver em `GET /servicos/{id}` a solicitação indicada, apenas para o cliente dono ou o prestador dono. Qualquer outra conta SHALL receber 403 com código `ACESSO_NEGADO`, e solicitação inexistente SHALL responder 404 com código `RECURSO_NAO_ENCONTRADO`.

A resposta SHALL trazer o estado da solicitação, o estado do pagamento quando houver, a categoria, o valor, o prestador, o cliente, o endereço de atendimento quando informado, e os momentos em que a solicitação foi aberta, aceita, iniciada e concluída, para que o acompanhamento possa ser apresentado com horário real em vez de sequência presumida.

Momento ainda não ocorrido SHALL vir sem valor, e a interface SHALL apresentar a etapa correspondente como não cumprida, em vez de exibir horário estimado.

#### Scenario: Cliente dono consulta a própria solicitação
- **WHEN** o cliente dono consulta uma solicitação sua
- **THEN** recebe o estado, o valor, o prestador, o estado do pagamento e os momentos já ocorridos do atendimento

#### Scenario: Prestador dono consulta a solicitação
- **WHEN** o prestador dono consulta uma solicitação dirigida a ele
- **THEN** recebe a mesma solicitação

#### Scenario: Consulta por quem não participa
- **WHEN** uma conta que não é o cliente nem o prestador da solicitação a consulta
- **THEN** a resposta é 403 com código `ACESSO_NEGADO`

#### Scenario: Solicitação inexistente
- **WHEN** a consulta referencia uma solicitação que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

#### Scenario: Etapas ainda não cumpridas
- **WHEN** o cliente dono consulta uma solicitação que foi aceita mas cujo atendimento não começou
- **THEN** o momento da abertura e o do aceite vêm preenchidos, e os de início e conclusão vêm sem valor

### Requirement: Início do atendimento por código de confirmação

Ao aceitar uma solicitação, o sistema SHALL gerar um código de confirmação de quatro dígitos para aquele atendimento. O código SHALL ser devolvido **apenas ao cliente dono**; a solicitação vista pelo prestador MUST NOT conter o código, em consulta alguma, porque ele existe justamente para o cliente confirmar que quem chegou é quem foi contratado.

O prestador dono SHALL iniciar o atendimento via `PUT /servicos/{id}/iniciar`, informando o código que o cliente lhe passou. O início SHALL exigir que a solicitação esteja em `ACEITO` e que haja pagamento retido em custódia, e SHALL registrar o momento em que ocorreu, levando a solicitação a `EM_ANDAMENTO`.

Código incorreto SHALL responder 403 com código `ACESSO_NEGADO`, e a solicitação SHALL permanecer em `ACEITO`. O código MUST NOT ser alterado por tentativa incorreta.

#### Scenario: Atendimento iniciado com o código correto
- **WHEN** o prestador dono informa o código correto de uma solicitação aceita e paga
- **THEN** o estado passa a `EM_ANDAMENTO` e o momento do início fica registrado

#### Scenario: Código incorreto
- **WHEN** o prestador dono informa código diferente do gerado para aquela solicitação
- **THEN** a resposta é 403 com código `ACESSO_NEGADO`, o estado permanece `ACEITO` e o código continua o mesmo

#### Scenario: Código visível apenas ao cliente
- **WHEN** o prestador dono consulta a solicitação aceita
- **THEN** o código de confirmação não aparece em nenhum campo da resposta

#### Scenario: Código entregue ao cliente
- **WHEN** o cliente dono consulta uma solicitação sua já aceita
- **THEN** o código de confirmação daquele atendimento é devolvido a ele

#### Scenario: Início sem pagamento em custódia
- **WHEN** o prestador dono tenta iniciar uma solicitação aceita para a qual não há pagamento retido
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e o estado permanece `ACEITO`

#### Scenario: Início a partir de estado que não permite
- **WHEN** o prestador dono tenta iniciar uma solicitação que não está em `ACEITO`
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e o estado permanece o mesmo

#### Scenario: Início por prestador que não é o dono
- **WHEN** um prestador tenta iniciar uma solicitação dirigida a outro prestador
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e o estado permanece `ACEITO`

#### Scenario: Solicitação sem código antes do aceite
- **WHEN** o cliente dono consulta uma solicitação ainda em `SOLICITADO`
- **THEN** nenhum código de confirmação é devolvido, porque ele só é gerado no aceite

### Requirement: Superfícies decorativas no painel do prestador

O painel do prestador SHALL exibir, ao lado dos fluxos reais, indicadores de desempenho que não correspondem a dado apurado da conta — visitas no perfil e taxa de conversão. Esses indicadores MUST NOT ser confundidos com os fluxos reais: eles não realizam nenhuma chamada à API e nada do que exibem é persistido.

Nenhuma superfície decorativa SHALL existir no painel do cliente. Todo dado que ele exibe corresponde à conta autenticada e todo controle que ele oferece produz efeito observável, conforme a capability que rege o painel do cliente.

#### Scenario: Indicadores fixos no painel do prestador
- **WHEN** o painel do prestador exibe visitas no perfil e taxa de conversão
- **THEN** os valores mostrados são fixos no próprio HTML e não refletem dado algum da conta autenticada

#### Scenario: Painel do cliente sem área decorativa
- **WHEN** o cliente usa o checkout, o acompanhamento, o recibo, o chat ou o simulador de custo do painel
- **THEN** cada um deles consulta ou altera a plataforma, e nenhuma confirmação de sucesso é apresentada sem que a operação tenha ocorrido

## MODIFIED Requirements

### Requirement: Ciclo de vida da solicitação

Uma solicitação SHALL percorrer os estados `SOLICITADO` para `ACEITO`, `ACEITO` para `EM_ANDAMENTO`, `EM_ANDAMENTO` para `CONCLUIDO` e `CONCLUIDO` para `AVALIADO`, com os ramos `SOLICITADO` para `RECUSADO` e a passagem para `CANCELADO`. O sistema MUST recusar qualquer transição fora dessas, informando o estado atual.

A passagem de `ACEITO` para `EM_ANDAMENTO` SHALL depender do código de confirmação entregue ao cliente, e MUST NOT ser contornável: uma solicitação MUST NOT passar de `ACEITO` diretamente para `CONCLUIDO`.

#### Scenario: Transição inválida para o estado atual
- **WHEN** uma transição é solicitada a partir de um estado que não a permite, por exemplo aceitar uma solicitação já aceita
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e a solicitação permanece no estado em que estava

#### Scenario: Conclusão sem o atendimento ter começado
- **WHEN** o prestador dono tenta concluir uma solicitação que está em `ACEITO`
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e o estado permanece `ACEITO`

### Requirement: Abertura de solicitação pelo cliente

Um cliente autenticado SHALL abrir uma solicitação via `POST /servicos`, informando o serviço ofertado desejado e, opcionalmente, qual dos seus endereços é o local do atendimento. A solicitação SHALL nascer no estado `SOLICITADO`, herdando do serviço ofertado o prestador, a localização e o valor vigente no momento do pedido, e SHALL registrar o momento da abertura.

O endereço informado SHALL pertencer ao cliente que abre a solicitação; endereço de outra conta SHALL ser recusado com 403 e código `ACESSO_NEGADO`, e endereço inexistente com 404 e código `RECURSO_NAO_ENCONTRADO`. Solicitação aberta sem endereço SHALL ser aceita, e o acompanhamento dela SHALL apresentar-se sem local de atendimento em vez de presumir um.

#### Scenario: Solicitação aberta com sucesso
- **WHEN** um cliente autenticado solicita um serviço ativo de um prestador aprovado
- **THEN** a resposta é 201, o estado é `SOLICITADO`, o valor registrado é o preço do serviço no momento do pedido e o momento da abertura fica registrado

#### Scenario: Solicitação com endereço de atendimento
- **WHEN** o cliente informa um dos seus endereços ao abrir a solicitação
- **THEN** aquele endereço fica registrado como local do atendimento e é devolvido no acompanhamento da solicitação

#### Scenario: Solicitação sem endereço de atendimento
- **WHEN** o cliente abre a solicitação sem informar endereço
- **THEN** a solicitação é criada normalmente e o acompanhamento não apresenta local de atendimento

#### Scenario: Endereço pertencente a outra conta
- **WHEN** o cliente informa um endereço que pertence a outra conta
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e nenhuma solicitação é criada

#### Scenario: Confirmação da solicitação usa diálogos nativos do navegador
- **WHEN** o cliente aciona "Solicitar" na tela pública de busca de profissionais
- **THEN** a confirmação e o aviso de sucesso são apresentados como diálogos nativos do navegador, bloqueantes, e não como elementos da própria página

#### Scenario: Serviço indisponível no momento do pedido
- **WHEN** o serviço escolhido está inativo, ou o prestador dono não está com verificação aprovada
- **THEN** a resposta é 422 com código `RECURSO_INDISPONIVEL` e nenhuma solicitação é criada

#### Scenario: Solicitação duplicada para o mesmo prestador
- **WHEN** o cliente já tem uma solicitação em `SOLICITADO`, `ACEITO` ou `EM_ANDAMENTO` com aquele mesmo prestador e abre outra
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e nenhuma solicitação nova é criada

#### Scenario: Serviço ofertado inexistente
- **WHEN** a solicitação referencia um serviço que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Conclusão pelo prestador depende de pagamento em custódia

O prestador dono SHALL concluir a solicitação via `PUT /servicos/{id}/concluir` somente quando ela estiver em `EM_ANDAMENTO` e houver pagamento retido em custódia. A conclusão SHALL liberar o valor líquido ao prestador antes de registrar o novo estado, e SHALL registrar o momento em que ocorreu.

#### Scenario: Conclusão com pagamento retido
- **WHEN** o prestador dono conclui uma solicitação em atendimento e paga
- **THEN** o estado passa a `CONCLUIDO`, o valor líquido é creditado no saldo do prestador e o momento da conclusão fica registrado

#### Scenario: Conclusão sem pagamento em custódia
- **WHEN** o prestador dono tenta concluir uma solicitação em atendimento para a qual não há pagamento retido
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO`, o estado permanece `EM_ANDAMENTO` e nenhum saldo é creditado

#### Scenario: Conclusão de solicitação que não começou
- **WHEN** o prestador dono tenta concluir uma solicitação em `ACEITO`, cujo atendimento não foi iniciado
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO`, o estado permanece `ACEITO` e nenhum saldo é creditado

### Requirement: Cancelamento por qualquer uma das partes

Cliente dono ou prestador dono SHALL cancelar a solicitação via `PUT /servicos/{id}/cancelar` enquanto ela não estiver concluída ou avaliada. Se houver pagamento retido no momento do cancelamento, ele SHALL ser estornado integralmente.

O estorno SHALL ser processado qualquer que seja o estado de onde se cancela, e não apenas a partir de `ACEITO`: uma solicitação cujo atendimento já começou tem necessariamente pagamento retido, porque o início do atendimento o exige, e cancelá-la sem processar o estorno deixaria o valor preso em custódia, sem caminho de liberação nem de devolução. O sistema MUST NOT levar uma solicitação a `CANCELADO` deixando pagamento algum em `RETIDO`.

O estorno SHALL ser integral, exceto num caso: quando **o cliente** cancela uma solicitação em `EM_ANDAMENTO` depois de decorrida a carência contada do início do atendimento. Nesse caso o sistema SHALL retirar do valor devolvido uma taxa de cancelamento, que SHALL ser creditada integralmente ao prestador — ele se deslocou até o local e teve o tempo comprometido. A plataforma MUST NOT retirar nada nesse cancelamento: a taxa de serviço da RN01 SHALL ser devolvida ao cliente junto com o restante.

A taxa de cancelamento SHALL ser proporcional ao valor do serviço, com percentual maior quando a distância entre o local do atendimento e a localização do prestador ultrapassar o limiar configurado, e SHALL respeitar um teto em reais. Quando a distância não puder ser apurada, o sistema SHALL aplicar o percentual menor. Carência, percentuais, limiar de distância e teto SHALL vir dos parâmetros de negócio ajustáveis por administrador, sem novo deploy.

A taxa MUST NOT exceder o valor pago. Cancelamento **pelo prestador** em `EM_ANDAMENTO` SHALL estornar integralmente, sem taxa alguma: quem desistiu do atendimento não é compensado por ele.

Antes de confirmar o cancelamento, a interface SHALL informar ao cliente se haverá retenção e de quanto ela será. Um cancelamento que retém valor MUST NOT ser confirmado sem que o cliente tenha visto a retenção.

#### Scenario: Cancelamento antes do aceite
- **WHEN** o cliente dono cancela uma solicitação ainda em `SOLICITADO`
- **THEN** o estado passa a `CANCELADO`

#### Scenario: Cancelamento após o aceite, com pagamento retido
- **WHEN** cliente ou prestador dono cancela uma solicitação em `ACEITO` cujo pagamento está retido
- **THEN** o estado passa a `CANCELADO` e o valor retido é estornado integralmente, sem retenção de taxa

#### Scenario: Cancelamento pelo cliente dentro da carência
- **WHEN** o cliente dono cancela uma solicitação em `EM_ANDAMENTO` antes de decorrida a carência contada do início do atendimento
- **THEN** o estado passa a `CANCELADO`, o valor é estornado integralmente e nada é creditado ao prestador

#### Scenario: Cancelamento pelo cliente após a carência
- **WHEN** o cliente dono cancela uma solicitação em `EM_ANDAMENTO` depois de decorrida a carência
- **THEN** o estado passa a `CANCELADO`, a taxa de cancelamento é creditada ao saldo do prestador, o restante é devolvido ao cliente, e a plataforma não retém nada

#### Scenario: Taxa maior quando o prestador se deslocou mais
- **WHEN** dois cancelamentos após a carência ocorrem sobre serviços de mesmo valor, um com distância acima do limiar configurado e outro abaixo
- **THEN** o de distância maior retém percentual maior que o de distância menor

#### Scenario: Distância não apurável
- **WHEN** o cancelamento após a carência ocorre sobre solicitação cujo endereço de atendimento ou localização do prestador não tem coordenadas cadastradas
- **THEN** o sistema aplica o percentual menor, e o cancelamento é processado normalmente

#### Scenario: Teto da taxa de cancelamento
- **WHEN** o percentual aplicado a um serviço de valor alto resultaria em taxa acima do teto configurado
- **THEN** a taxa retida é o teto, e o restante é devolvido ao cliente

#### Scenario: Cancelamento pelo prestador durante o atendimento
- **WHEN** o prestador dono cancela uma solicitação em `EM_ANDAMENTO`, depois de decorrida a carência
- **THEN** o valor é estornado integralmente ao cliente e nada é creditado ao prestador

#### Scenario: Retenção informada antes da confirmação
- **WHEN** o cliente aciona o cancelamento de uma solicitação em `EM_ANDAMENTO` já fora da carência
- **THEN** a interface informa o valor que será retido e o valor que será devolvido, e o cancelamento só é executado após a confirmação

#### Scenario: Nenhum pagamento permanece retido após cancelamento
- **WHEN** uma solicitação com pagamento retido é cancelada, de qualquer estado que permita cancelamento
- **THEN** nenhum pagamento daquela solicitação permanece em `RETIDO`

#### Scenario: Cancelamento de solicitação já encerrada
- **WHEN** alguém tenta cancelar uma solicitação em `CONCLUIDO` ou `AVALIADO`
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e o estado permanece o mesmo

#### Scenario: Cancelamento por quem não participa
- **WHEN** uma conta que não é o cliente nem o prestador da solicitação tenta cancelá-la
- **THEN** a resposta é 403 com código `ACESSO_NEGADO`

## REMOVED Requirements

### Requirement: Superfícies decorativas nos painéis

**Reason**: O requisito cobria os dois painéis ao mesmo tempo e, para o painel do cliente, *exigia* que o checkout, o chat, o recibo e o simulador de custo não fizessem chamada alguma à API — exatamente o que esta change desfaz. Mantê-lo, ainda que reescrito, deixaria a spec afirmando que aquelas telas são decorativas enquanto elas passam a ser reais. O que continua valendo é apenas a parte do painel do prestador, que é substancialmente mais estreita que o requisito original.

**Migration**: O comportamento que permanece está em `Superfícies decorativas no painel do prestador`, nesta mesma capability, restrito a visitas no perfil e taxa de conversão. As regras do painel do cliente passam a ser as da capability `painel-cliente`, que proíbe dado fabricado e exige efeito observável de todo controle — o oposto do que este requisito autorizava.
