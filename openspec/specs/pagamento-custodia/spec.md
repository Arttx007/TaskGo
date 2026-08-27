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

O cancelamento de uma solicitação com pagamento retido SHALL estornar o valor integralmente ao cliente, sem retenção de taxa. O estorno MUST NOT debitar o prestador, já que o crédito só ocorre na liberação.

#### Scenario: Estorno integral
- **WHEN** uma solicitação com pagamento retido é cancelada
- **THEN** o pagamento passa a estornado, pelo valor integral, e nenhuma taxa é retida

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
