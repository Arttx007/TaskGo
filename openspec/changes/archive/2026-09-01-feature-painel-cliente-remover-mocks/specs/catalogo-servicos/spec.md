## ADDED Requirements

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
