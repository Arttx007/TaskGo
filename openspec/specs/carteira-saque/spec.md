## Purpose

Cobre o saldo do prestador dentro da plataforma e a retirada desse saldo via Pix.

## Requirements

### Requirement: Consulta do próprio saldo

O prestador autenticado SHALL consultar seu saldo disponível via `GET /prestadores/{id}/saldo`. A consulta SHALL ser restrita ao dono do cadastro.

#### Scenario: Consulta pelo dono
- **WHEN** o prestador consulta o próprio saldo
- **THEN** a resposta traz o saldo disponível corrente

#### Scenario: Consulta do saldo de outro prestador
- **WHEN** um prestador informa o identificador de outro prestador
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e nenhum saldo é revelado

#### Scenario: Saldo de prestador recém-cadastrado
- **WHEN** um prestador que ainda não concluiu nenhum atendimento consulta o saldo
- **THEN** o saldo devolvido é zero

### Requirement: Saque limitado ao saldo disponível

O prestador dono SHALL solicitar um saque via `POST /prestadores/{id}/saques`, informando um valor positivo. O sistema SHALL recusar valor acima do saldo disponível e SHALL debitar o saldo no mesmo momento em que registra o saque.

#### Scenario: Saque dentro do saldo
- **WHEN** o prestador solicita um saque de valor menor ou igual ao saldo disponível
- **THEN** a resposta é 201, o saque é registrado como processado e o saldo restante devolvido já reflete o débito

#### Scenario: Saque acima do saldo
- **WHEN** o valor solicitado excede o saldo disponível
- **THEN** a resposta é 422 com código `SALDO_INSUFICIENTE`, nenhum saque é registrado e o saldo permanece inalterado

#### Scenario: Valor de saque não positivo
- **WHEN** o valor solicitado é zero, negativo ou ausente
- **THEN** a resposta é 400 com código `VALIDACAO`

#### Scenario: Saque a partir do cadastro de outro prestador
- **WHEN** um prestador tenta sacar informando o identificador de outro prestador
- **THEN** o saque é recusado e nenhum saldo alheio é movimentado

### Requirement: Repasse Pix é simulado

O saque SHALL registrar como destino a chave Pix cadastrada do prestador e SHALL ser marcado como processado de imediato. Nenhuma transferência Pix real é executada, enquanto a validação de saldo e o débito são reais e persistidos.

#### Scenario: Saque marcado como processado
- **WHEN** um saque válido é solicitado
- **THEN** ele é registrado como processado, com a chave Pix do prestador como destino, sem etapa de confirmação externa
