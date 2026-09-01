## Purpose

Cobre o catálogo de serviços que o prestador publica na plataforma: criação, edição, ativação e exclusão, e quem pode alterar cada item.

## Requirements

### Requirement: Publicação de serviço no catálogo

Um prestador autenticado e com verificação aprovada SHALL publicar um serviço via `POST /servicos-ofertados`, informando `categoria` (obrigatória), `descricao`, `preco` e, opcionalmente, uma localização existente. O serviço SHALL nascer com status `ATIVO` e SHALL ser associado ao prestador identificado pelo token, nunca a um identificador enviado na requisição.

#### Scenario: Publicação bem-sucedida
- **WHEN** um prestador com verificação aprovada publica um serviço com categoria e preço válidos
- **THEN** a resposta é 201 e o serviço passa a existir com status `ATIVO`, vinculado a esse prestador

#### Scenario: Categoria ausente ou preço não positivo
- **WHEN** a requisição não informa categoria, ou informa preço menor ou igual a zero
- **THEN** a resposta é 400 com código `VALIDACAO` e nenhum serviço é criado

#### Scenario: Localização informada não existe
- **WHEN** a requisição referencia uma localização inexistente
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO` e nenhum serviço é criado

#### Scenario: Prestador sem verificação aprovada
- **WHEN** um prestador com verificação `PENDENTE` ou `REJEITADO` tenta publicar
- **THEN** a resposta é 422 com código `KYC_PENDENTE`

### Requirement: Consulta do próprio catálogo

O sistema SHALL devolver, em `GET /servicos-ofertados/meus`, apenas os serviços do prestador identificado pelo token, independentemente do status de cada um.

#### Scenario: Listagem do próprio catálogo
- **WHEN** um prestador autenticado consulta seus serviços
- **THEN** recebe os seus serviços ativos e inativos, e nenhum serviço de outro prestador

### Requirement: Edição, ativação e exclusão restritas ao dono

O sistema SHALL permitir ao prestador dono atualizar (`PUT /servicos-ofertados/{id}`), alternar entre ativo e inativo (`PUT /servicos-ofertados/{id}/ativo`) e excluir (`DELETE /servicos-ofertados/{id}`) um serviço do seu catálogo. Qualquer dessas operações sobre serviço de outro prestador SHALL ser negada.

#### Scenario: Atualização pelo dono
- **WHEN** o prestador dono atualiza categoria, descrição, preço ou localização do serviço
- **THEN** o serviço passa a refletir os novos dados

#### Scenario: Inativação retira o serviço da busca
- **WHEN** o prestador dono marca um serviço como inativo
- **THEN** o serviço permanece no catálogo dele com status `INATIVO` e deixa de aparecer nas buscas de clientes

#### Scenario: Exclusão pelo dono
- **WHEN** o prestador dono exclui um serviço que nunca foi solicitado
- **THEN** o serviço deixa de existir no catálogo

#### Scenario: Exclusão de serviço já solicitado
- **WHEN** o prestador dono exclui um serviço que já foi objeto de alguma solicitação
- **THEN** a resposta é 500 com código `ERRO_INTERNO`, sem indicar que a causa é o vínculo com solicitações existentes, e o serviço permanece no catálogo

#### Scenario: Operação sobre serviço de outro prestador
- **WHEN** um prestador tenta atualizar, alternar o status ou excluir um serviço que pertence a outro prestador
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e o serviço permanece inalterado

#### Scenario: Serviço inexistente
- **WHEN** a operação referencia um serviço que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Consulta dos serviços ativos de um prestador

O sistema SHALL devolver em `GET /prestadores/{id}/servicos-ofertados` os serviços do prestador indicado, para qualquer conta autenticada. SHALL considerar apenas serviços com status `ATIVO`, e SHALL devolver lista vazia quando o prestador não estiver com verificação `APROVADO`, preservando a regra de que prestador não aprovado não é oferecido para contratação.

Cada serviço SHALL trazer o identificador, a categoria, a descrição e o preço vigente, que é o suficiente para abrir uma solicitação. A resposta MUST NOT expor dado de contato do prestador, porque esta rota existe para escolher o serviço, não para alcançar a pessoa fora da plataforma.

Prestador inexistente SHALL responder 404 com código `RECURSO_NAO_ENCONTRADO`.

#### Scenario: Serviços ativos de um prestador aprovado
- **WHEN** uma conta autenticada consulta os serviços de um prestador aprovado que tem serviços ativos
- **THEN** recebe esses serviços, cada um com categoria, descrição e preço vigente

#### Scenario: Serviços inativos são omitidos
- **WHEN** o prestador consultado tem serviços inativos
- **THEN** esses serviços não aparecem na resposta

#### Scenario: Prestador sem verificação aprovada
- **WHEN** uma conta autenticada consulta os serviços de um prestador que não está com verificação aprovada
- **THEN** a resposta é bem-sucedida com lista vazia, mesmo que ele tenha serviços cadastrados

#### Scenario: Prestador aprovado sem serviço ativo
- **WHEN** o prestador consultado está aprovado mas não tem serviço ativo algum
- **THEN** a resposta é bem-sucedida com lista vazia

#### Scenario: Nenhum dado de contato é exposto
- **WHEN** os serviços de um prestador são consultados
- **THEN** nenhum e-mail, telefone, chave Pix ou documento do prestador aparece na resposta

#### Scenario: Prestador inexistente
- **WHEN** a consulta referencia um prestador que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

#### Scenario: Consulta sem autenticação
- **WHEN** os serviços de um prestador são consultados sem token válido
- **THEN** a requisição é recusada e nenhum serviço é devolvido
