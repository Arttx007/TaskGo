## Purpose

Cobre o pagamento de um atendimento pelo cliente: o cálculo da taxa da plataforma, a retenção do valor em custódia e sua liberação ou estorno conforme o desfecho do atendimento.

## Requirements

### Requirement: Cálculo da taxa de serviço

O sistema SHALL calcular a taxa sobre o valor do serviço aplicando taxa fixa quando o valor for menor que o limiar configurado, e taxa percentual quando for maior ou igual ao limiar. O valor líquido SHALL ser o valor do serviço menos a taxa. Limiar, valor fixo e percentual SHALL vir dos parâmetros de negócio ajustáveis por administrador, e SHALL ser lidos a cada cálculo, sem exigir novo deploy nem reinício.

#### Scenario: Valor abaixo do limiar aplica taxa fixa
- **WHEN** o valor do serviço é menor que o limiar configurado
- **THEN** a taxa cobrada é o valor fixo configurado, e não um percentual

#### Scenario: Valor a partir do limiar aplica percentual
- **WHEN** o valor do serviço é igual ou maior que o limiar configurado
- **THEN** a taxa cobrada é o percentual configurado sobre o valor do serviço

#### Scenario: Cliente paga o valor cheio
- **WHEN** a taxa é calculada para um atendimento
- **THEN** o cliente é cobrado pelo valor cheio anunciado e a taxa é descontada do valor que chega ao prestador

#### Scenario: Parâmetro de taxa ausente na base
- **WHEN** um dos parâmetros de taxa não está cadastrado
- **THEN** o cálculo falha como erro interno, e não é substituído por um valor assumido por padrão

### Requirement: Pagamento retém o valor em custódia

O cliente dono SHALL pagar uma solicitação em `ACEITO` via `POST /servicos/{id}/pagamento`. Um pagamento aprovado SHALL ficar retido em custódia, registrando valor bruto, taxa e valor líquido apurados no momento do pagamento.

#### Scenario: Pagamento aprovado
- **WHEN** o cliente dono paga uma solicitação aceita
- **THEN** o pagamento é registrado como retido, com valor bruto, taxa e valor líquido, e o saldo do prestador ainda não é alterado

#### Scenario: Pagamento recusado pela operadora
- **WHEN** a cobrança é recusada
- **THEN** a resposta é 402 com código `PAGAMENTO_RECUSADO` e nenhum pagamento fica registrado para a solicitação

#### Scenario: Pagamento de solicitação que não está aceita
- **WHEN** o cliente tenta pagar uma solicitação que ainda não foi aceita, ou que já foi encerrada
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO`

#### Scenario: Pagamento repetido
- **WHEN** o cliente tenta pagar novamente uma solicitação cujo pagamento já está retido ou liberado
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e nenhum segundo pagamento é criado

#### Scenario: Pagamento por quem não é o cliente da solicitação
- **WHEN** outra conta tenta pagar a solicitação
- **THEN** a resposta é 403 com código `ACESSO_NEGADO`

### Requirement: Liberação do valor na conclusão

A conclusão do atendimento SHALL mudar o pagamento de retido para liberado e SHALL creditar o valor líquido no saldo disponível do prestador. A liberação SHALL exigir que o pagamento esteja retido.

#### Scenario: Liberação bem-sucedida
- **WHEN** o atendimento é concluído e havia pagamento retido
- **THEN** o pagamento passa a liberado e o valor líquido é somado ao saldo disponível do prestador

#### Scenario: Liberação sem pagamento retido
- **WHEN** a conclusão é tentada sem pagamento em custódia
- **THEN** a operação é recusada com código `ESTADO_INVALIDO` e nenhum saldo é creditado

### Requirement: Estorno no cancelamento

O cancelamento de uma solicitação com pagamento retido SHALL estornar o valor ao cliente, sem que a plataforma retenha taxa de serviço alguma.

O estorno SHALL ser integral, exceto quando o cliente cancelar um atendimento já iniciado depois de decorrida a carência. Nesse caso o estorno SHALL ser parcial: a taxa de cancelamento apurada SHALL ser creditada no saldo disponível do prestador, e o restante devolvido ao cliente. O registro do pagamento SHALL guardar quanto foi devolvido e quanto foi retido, para que o extrato e o comprovante possam apresentar os dois valores sem recalcular nada.

Fora desse caso, o estorno MUST NOT debitar nem creditar o prestador. Cancelamento pelo prestador SHALL estornar integralmente, ainda que o atendimento já tenha começado.

#### Scenario: Estorno integral
- **WHEN** uma solicitação com pagamento retido é cancelada antes do início do atendimento
- **THEN** o pagamento passa a estornado, pelo valor integral, e nenhuma taxa é retida

#### Scenario: Estorno parcial com crédito ao prestador
- **WHEN** o cliente cancela um atendimento iniciado, fora da carência, cujo pagamento está retido
- **THEN** o pagamento passa a estornado parcialmente, a taxa de cancelamento é somada ao saldo disponível do prestador, e o registro guarda o valor devolvido e o valor retido

#### Scenario: Estorno integral no cancelamento pelo prestador
- **WHEN** o prestador cancela um atendimento já iniciado, fora da carência
- **THEN** o pagamento passa a estornado pelo valor integral e nenhum crédito é feito ao prestador

#### Scenario: Plataforma não retém taxa de serviço no cancelamento
- **WHEN** qualquer cancelamento com pagamento retido é processado
- **THEN** a taxa de serviço apurada no pagamento é devolvida ao cliente, e a plataforma não retém parte alguma

#### Scenario: Cancelamento sem pagamento
- **WHEN** uma solicitação sem pagamento registrado é cancelada
- **THEN** o cancelamento é concluído normalmente, sem nenhum estorno a processar

### Requirement: Cobrança externa é simulada

A cobrança SHALL ser processada por um gateway simulado, que aprova toda cobrança exceto quando a requisição pede explicitamente a simulação de falha. Nenhum provedor de pagamento real é acionado, enquanto o registro de custódia é real e persistido.

#### Scenario: Simulação de falha a pedido
- **WHEN** a requisição de pagamento pede explicitamente para simular falha
- **THEN** a cobrança é recusada e o cliente da API recebe 402 `PAGAMENTO_RECUSADO`

#### Scenario: Cobrança sem pedido de falha
- **WHEN** a requisição de pagamento não pede simulação de falha
- **THEN** a cobrança é aprovada, independentemente do método de pagamento informado

### Requirement: Taxa de cancelamento após o início do atendimento

O sistema SHALL apurar uma taxa de cancelamento quando o cliente cancelar um atendimento já iniciado, depois de decorrida a carência contada do início. A taxa SHALL ser calculada sobre o valor do serviço, aplicando o percentual menor quando a distância entre o local do atendimento e a localização do prestador estiver dentro do limiar configurado, e o percentual maior quando ultrapassá-lo. Quando a distância não puder ser apurada por falta de coordenadas, o sistema SHALL aplicar o percentual menor.

A taxa apurada SHALL respeitar um teto em reais e MUST NOT exceder o valor pago. Carência em minutos, os dois percentuais, o limiar de distância e o teto SHALL vir dos parâmetros de negócio, SHALL ser lidos a cada apuração e ajustáveis por administrador sem novo deploy nem reinício, como já ocorre com a taxa de serviço.

Esta taxa é distinta da taxa de serviço da RN01 e MUST NOT alterá-la: a taxa de serviço remunera a plataforma num atendimento que se completa, e a taxa de cancelamento compensa o prestador por um atendimento que não se completou. As duas nunca são cobradas no mesmo desfecho.

#### Scenario: Percentual menor dentro do limiar de distância
- **WHEN** a taxa é apurada para um atendimento cuja distância está dentro do limiar configurado
- **THEN** o percentual menor é aplicado sobre o valor do serviço

#### Scenario: Percentual maior além do limiar de distância
- **WHEN** a taxa é apurada para um atendimento cuja distância ultrapassa o limiar configurado
- **THEN** o percentual maior é aplicado sobre o valor do serviço

#### Scenario: Coordenadas ausentes
- **WHEN** a taxa é apurada e o endereço do atendimento ou a localização do prestador não tem coordenadas cadastradas
- **THEN** o percentual menor é aplicado, sem recusar a operação

#### Scenario: Teto aplicado
- **WHEN** o percentual resultaria em taxa acima do teto configurado
- **THEN** a taxa apurada é o teto

#### Scenario: Taxa limitada ao valor pago
- **WHEN** o teto configurado é maior que o valor pago pelo serviço
- **THEN** a taxa apurada é no máximo o valor pago, e o cliente nunca fica devendo

#### Scenario: Parâmetros ajustados sem deploy
- **WHEN** um administrador altera a carência, um percentual, o limiar ou o teto
- **THEN** a apuração seguinte já usa os valores novos, sem reinício da aplicação

### Requirement: Extrato de pagamentos do cliente

O sistema SHALL devolver em `GET /clientes/me/pagamentos` os pagamentos originados pelo cliente autenticado, do mais recente para o mais antigo. Cada item SHALL trazer a solicitação a que se refere, a categoria do serviço, o nome do prestador, o valor bruto, o valor da taxa, a situação do pagamento, o meio de pagamento registrado e o momento em que o pagamento foi feito.

A situação SHALL ser a que a custódia registra — pagamento retido, valor liberado ao prestador, valor estornado integralmente ou valor estornado em parte — para que o cliente veja onde o dinheiro está, e não apenas que pagou. Valor estornado SHALL ser apresentado como estorno, distinguível de um pagamento efetivado.

Quando o estorno tiver sido parcial, o item SHALL trazer o valor devolvido e o valor retido como taxa de cancelamento, e a interface SHALL apresentar os dois separadamente. Um estorno parcial exibido como se fosse integral esconderia do cliente a retenção que ele sofreu.

O extrato SHALL conter apenas pagamentos da conta autenticada, e SHALL devolver o conjunto completo, sem paginação. Cliente que nunca pagou nada SHALL receber lista vazia, e a interface SHALL apresentar estado vazio em vez de transações de exemplo.

O comprovante apresentado ao cliente SHALL ser montado a partir dos valores devolvidos por este extrato. A interface MUST NOT calcular, arredondar ou inferir subtotal, taxa ou identificador de comprovante que não tenha vindo da plataforma.

#### Scenario: Cliente consulta o próprio extrato
- **WHEN** um cliente que fez dois pagamentos consulta o extrato
- **THEN** recebe os dois, do mais recente para o mais antigo, cada um com valor bruto, taxa, situação e o serviço a que se refere

#### Scenario: Pagamento em custódia aparece como retido
- **WHEN** o cliente pagou um serviço que ainda não foi concluído
- **THEN** aquele pagamento aparece no extrato com a situação de valor retido em custódia

#### Scenario: Estorno aparece como estorno
- **WHEN** uma solicitação paga foi cancelada e o valor estornado
- **THEN** aquele lançamento aparece no extrato identificado como estorno, distinguível de um pagamento efetivado

#### Scenario: Estorno parcial mostra os dois valores
- **WHEN** o cliente consulta o extrato de um atendimento que ele cancelou fora da carência
- **THEN** o lançamento traz o valor devolvido e o valor retido como taxa de cancelamento, apresentados separadamente

#### Scenario: Extrato de conta sem pagamento
- **WHEN** um cliente que nunca pagou nada consulta o extrato
- **THEN** a resposta é bem-sucedida com lista vazia, e nenhuma transação de exemplo é apresentada

#### Scenario: Extrato não alcança outra conta
- **WHEN** um cliente autenticado consulta o extrato
- **THEN** nenhum pagamento originado por outra conta aparece na resposta

#### Scenario: Comprovante montado a partir do extrato
- **WHEN** o cliente abre o comprovante de um pagamento do extrato
- **THEN** os valores apresentados são os devolvidos pela plataforma para aquele pagamento, e nenhum valor é calculado ou gerado na própria página

#### Scenario: Consulta sem autenticação
- **WHEN** o extrato é consultado sem token válido
- **THEN** a requisição é recusada e nenhum pagamento é devolvido
