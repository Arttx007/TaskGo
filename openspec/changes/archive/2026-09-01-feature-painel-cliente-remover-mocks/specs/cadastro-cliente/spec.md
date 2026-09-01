## ADDED Requirements

### Requirement: Consulta do próprio perfil pelo cliente

O sistema SHALL devolver em `GET /clientes/me` o perfil da conta de cliente autenticada, trazendo nome, e-mail, telefone, idade, cidade, tipo de cliente e a referência da foto de perfil quando houver. A resposta MUST NOT conter a senha, em nenhuma forma.

A rota SHALL identificar o cliente pelo token, e MUST NOT aceitar identificador informado por quem chama, para que uma conta não possa ler o perfil de outra.

#### Scenario: Cliente consulta o próprio perfil
- **WHEN** um cliente autenticado consulta o próprio perfil
- **THEN** recebe os dados cadastrais da própria conta

#### Scenario: Senha nunca é devolvida
- **WHEN** o perfil de um cliente é consultado
- **THEN** nenhum campo de senha, em texto claro ou codificado, aparece na resposta

#### Scenario: Consulta sem autenticação
- **WHEN** o perfil é consultado sem token válido
- **THEN** a requisição é recusada e nenhum dado de perfil é devolvido

### Requirement: Atualização do próprio perfil pelo cliente

Um cliente autenticado SHALL atualizar o próprio perfil via `PUT /clientes/me`, informando nome, e-mail, telefone, idade e cidade. O nome e o e-mail SHALL ser obrigatórios, e o e-mail SHALL ter formato válido; dado inválido SHALL ser recusado com 400 e código `VALIDACAO`, indicando os campos com problema.

O e-mail SHALL ser único entre as contas de cliente. Tentativa de adotar e-mail já usado por outra conta de cliente SHALL ser recusada com 409 e código `ESTADO_INVALIDO`, e nenhum dado SHALL ser alterado.

A atualização MUST NOT alterar a senha nem o saldo, o histórico ou as solicitações da conta. Alteração de senha não é oferecida por esta rota.

#### Scenario: Perfil atualizado
- **WHEN** um cliente autenticado envia nome, e-mail e telefone válidos
- **THEN** os dados passam a ser os informados, e a consulta seguinte ao perfil devolve os valores novos

#### Scenario: Dado obrigatório ausente ou inválido
- **WHEN** a atualização é enviada sem nome, ou com e-mail em formato inválido
- **THEN** a resposta é 400 com código `VALIDACAO`, indicando os campos com problema, e nenhum dado é alterado

#### Scenario: E-mail já usado por outra conta
- **WHEN** o cliente tenta adotar um e-mail que já pertence a outra conta de cliente
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e nenhum dado é alterado

#### Scenario: Atualização não alcança outra conta
- **WHEN** um cliente autenticado atualiza o perfil
- **THEN** somente a própria conta é alterada, qualquer que seja o identificador presente no corpo da requisição

### Requirement: Foto de perfil do cliente

Um cliente autenticado SHALL enviar a própria foto de perfil via `PUT /clientes/me/foto`. O sistema SHALL aceitar apenas imagem em formato de uso corrente e SHALL recusar arquivo de outro tipo ou acima do tamanho máximo aceito com 400 e código `ARQUIVO_INVALIDO`.

A foto SHALL ficar associada à conta que a enviou, SHALL substituir a anterior quando houver, e SHALL ser legível apenas pela própria conta. Cliente sem foto enviada SHALL ter o perfil devolvido sem referência de foto, e a interface SHALL apresentar identificação sem foto em vez de uma imagem de exemplo.

#### Scenario: Foto enviada
- **WHEN** o cliente envia uma imagem em formato aceito
- **THEN** a foto passa a estar associada à conta e a referência dela é devolvida no perfil

#### Scenario: Arquivo de tipo não aceito
- **WHEN** o cliente envia arquivo que não é imagem em formato aceito, ou acima do tamanho máximo
- **THEN** a resposta é 400 com código `ARQUIVO_INVALIDO` e nenhuma foto é registrada

#### Scenario: Foto substituída
- **WHEN** o cliente que já tem foto envia outra
- **THEN** a nova passa a ser a foto da conta

#### Scenario: Cliente sem foto
- **WHEN** o perfil de um cliente que nunca enviou foto é consultado
- **THEN** a resposta vem sem referência de foto, e nenhuma imagem de exemplo é apresentada em seu lugar

### Requirement: Endereços do cliente

O sistema SHALL manter os endereços de atendimento de cada cliente, acessíveis apenas pela própria conta, em `GET /clientes/me/enderecos`, `POST /clientes/me/enderecos`, `PUT /clientes/me/enderecos/{id}` e `DELETE /clientes/me/enderecos/{id}`.

Cada endereço SHALL trazer um apelido, CEP, rua, número, complemento opcional, bairro, cidade, estado e, quando conhecidas, as coordenadas. Apelido, CEP, rua, número, bairro, cidade e estado SHALL ser obrigatórios; ausência ou formato inválido SHALL ser recusado com 400 e código `VALIDACAO`.

Um endereço de cada cliente SHALL ser o endereço padrão. O primeiro endereço cadastrado SHALL nascer padrão; ao marcar outro como padrão, o anterior SHALL deixar de ser. O endereço padrão SHALL ser o que a busca do painel usa quando não há posição informada pelo navegador.

Endereço de outra conta SHALL responder 403 com código `ACESSO_NEGADO` a leitura, alteração ou remoção. Endereço já referenciado por uma solicitação SHALL continuar legível pela solicitação depois de removido da lista do cliente, para que o histórico não perca o local do atendimento.

#### Scenario: Primeiro endereço nasce padrão
- **WHEN** um cliente sem endereço algum cadastra o primeiro
- **THEN** ele é criado e passa a ser o endereço padrão da conta

#### Scenario: Troca do endereço padrão
- **WHEN** o cliente marca como padrão um endereço que não era
- **THEN** ele passa a ser o padrão e o anterior deixa de ser, restando um único padrão na conta

#### Scenario: Endereço com dado obrigatório ausente
- **WHEN** o cliente cadastra endereço sem CEP, rua, número, bairro, cidade ou estado
- **THEN** a resposta é 400 com código `VALIDACAO` e nenhum endereço é criado

#### Scenario: Endereço de outra conta
- **WHEN** um cliente tenta ler, alterar ou remover endereço pertencente a outra conta
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e nada é alterado

#### Scenario: Cliente sem endereço cadastrado
- **WHEN** um cliente que não cadastrou endereço algum consulta seus endereços
- **THEN** a resposta é bem-sucedida com lista vazia, e nenhum endereço de exemplo aparece

#### Scenario: Endereço removido permanece no histórico
- **WHEN** o cliente remove um endereço que já havia sido informado em uma solicitação
- **THEN** ele deixa de aparecer entre os endereços da conta, e a solicitação continua indicando o local em que o atendimento foi combinado

### Requirement: Desativação da própria conta pelo cliente

Um cliente autenticado SHALL desativar a própria conta via `DELETE /clientes/me`. A desativação SHALL impedir novo login com aquela conta e MUST NOT apagar as solicitações, os pagamentos, as avaliações ou o saldo de prestador que dependam dela.

Conta desativada com solicitação em andamento SHALL ter a desativação recusada com 409 e código `ESTADO_INVALIDO`, porque há atendimento e dinheiro em custódia pendentes de conclusão.

As avaliações escritas pela conta desativada SHALL continuar contando para a nota média do prestador avaliado, e SHALL continuar aparecendo na leitura pública identificadas apenas pelo primeiro nome, como já ocorre.

#### Scenario: Conta desativada
- **WHEN** um cliente sem solicitação em andamento desativa a própria conta
- **THEN** a operação é bem-sucedida e uma tentativa de login com aquela conta é recusada

#### Scenario: Desativação com solicitação em andamento
- **WHEN** o cliente tenta desativar a conta tendo solicitação em `SOLICITADO`, `ACEITO` ou `EM_ANDAMENTO`
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e a conta permanece ativa

#### Scenario: Histórico preservado
- **WHEN** uma conta de cliente é desativada
- **THEN** as solicitações, os pagamentos e as avaliações que ela originou permanecem registrados, e a nota média dos prestadores avaliados não muda

#### Scenario: Desativação não alcança outra conta
- **WHEN** um cliente autenticado desativa a conta
- **THEN** somente a própria conta é desativada
