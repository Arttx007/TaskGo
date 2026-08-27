## Purpose

Cobre a criação e a consulta de contas de cliente, incluindo a validação dos dados de entrada e o tratamento dado à senha.

## Requirements

### Requirement: Cadastro público de cliente

O sistema SHALL permitir criar uma conta de cliente sem autenticação, via `POST /clientes`, recebendo `nome`, `idade`, `cidade`, `tipoCliente`, `email` e `senha`. `nome`, `email` e `senha` SHALL ser obrigatórios; `email` SHALL ter formato válido e `senha` SHALL ter no mínimo 8 caracteres. Os demais campos são opcionais.

#### Scenario: Cadastro bem-sucedido
- **WHEN** um visitante envia nome, e-mail válido e senha com ao menos 8 caracteres
- **THEN** a conta é criada e a resposta traz os dados do cliente, incluindo o identificador gerado

#### Scenario: Resposta de sucesso usa status 200
- **WHEN** o cadastro de cliente é concluído com sucesso
- **THEN** o status da resposta é 200, diferente do cadastro de prestador, que responde 201

#### Scenario: Campo obrigatório ausente ou inválido
- **WHEN** falta `nome`, `email` ou `senha`, ou o e-mail tem formato inválido, ou a senha tem menos de 8 caracteres
- **THEN** a resposta é 400 com código `VALIDACAO` e `fieldErrors` indicando cada campo recusado

### Requirement: Senha do cliente nunca é devolvida

O sistema SHALL armazenar a senha apenas em forma de hash e MUST NOT incluí-la em nenhuma resposta da API.

#### Scenario: Resposta de cadastro e de listagem
- **WHEN** um cliente é criado ou listado pela API
- **THEN** a representação devolvida contém identificador, nome, idade, cidade, tipo e e-mail, e não contém a senha em nenhuma forma

### Requirement: Listagem de clientes exige autenticação

O sistema SHALL exigir uma requisição autenticada para listar clientes via `GET /clientes`. A listagem não é restrita a administradores: qualquer conta autenticada, de qualquer tipo, recebe a lista completa.

#### Scenario: Listagem sem autenticação
- **WHEN** uma requisição sem token válido chama a listagem de clientes
- **THEN** a requisição é rejeitada e nenhum dado de cliente é devolvido

#### Scenario: Listagem por conta autenticada não administradora
- **WHEN** uma conta autenticada como cliente ou prestador chama a listagem de clientes
- **THEN** a lista completa de clientes é devolvida
