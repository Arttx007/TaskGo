## Purpose

Cobre como um cliente encontra serviços disponíveis por categoria, com prioridade para os mais próximos quando a localização é conhecida.

## Requirements

### Requirement: Busca pública por categoria

O sistema SHALL expor a busca de serviços em `GET /servicos-ofertados/buscar` sem exigir autenticação, recebendo `categoria` (obrigatória) e, opcionalmente, `lat`, `lon`, `raioKm` e `cidade`. A busca SHALL considerar apenas serviços com status `ATIVO` cujo prestador esteja com verificação `APROVADO`.

#### Scenario: Busca sem estar autenticado
- **WHEN** um visitante busca por uma categoria sem enviar token
- **THEN** a busca é executada normalmente e devolve os serviços disponíveis

#### Scenario: Serviços indisponíveis são omitidos
- **WHEN** existem, na categoria buscada, serviços inativos ou de prestadores não aprovados
- **THEN** esses serviços não aparecem entre os resultados

#### Scenario: Categoria ausente
- **WHEN** a requisição não informa a categoria
- **THEN** a busca é recusada e nenhum resultado é devolvido

### Requirement: Ordenação por proximidade quando há coordenadas

Quando `lat` e `lon` são informados, o sistema SHALL calcular a distância entre o ponto informado e a localização de cada serviço, SHALL descartar os que estiverem além do raio aplicável e SHALL ordenar os resultados do mais próximo ao mais distante. Cada resultado SHALL trazer a distância calculada.

#### Scenario: Resultados ordenados por distância
- **WHEN** o cliente busca informando suas coordenadas
- **THEN** os resultados vêm ordenados do mais próximo ao mais distante, cada um com sua distância em quilômetros

#### Scenario: Raio informado pelo cliente
- **WHEN** a busca informa um raio explícito
- **THEN** serviços mais distantes que esse raio não aparecem nos resultados

#### Scenario: Raio omitido usa o padrão configurável
- **WHEN** a busca com coordenadas não informa raio
- **THEN** o sistema aplica o raio padrão vigente nos parâmetros de negócio, ajustável por administrador sem novo deploy

#### Scenario: Serviço sem coordenadas cadastradas
- **WHEN** um serviço da categoria não tem coordenadas em sua localização
- **THEN** ele é devolvido sem distância calculada e não é descartado pelo filtro de raio

### Requirement: Busca alternativa por cidade

Quando `lat` e `lon` não são informados, o sistema SHALL buscar por nome de cidade, sem calcular distância. Se nem coordenadas nem cidade forem informadas, a busca SHALL devolver resultado vazio.

#### Scenario: Busca por cidade
- **WHEN** o cliente busca informando categoria e cidade, sem coordenadas
- **THEN** são devolvidos os serviços disponíveis daquela cidade, sem distância calculada

#### Scenario: Nem coordenadas nem cidade
- **WHEN** a busca informa apenas a categoria
- **THEN** o resultado vem vazio, com a mensagem de que nada foi encontrado na região

### Requirement: Resposta explícita para busca sem resultado

O sistema SHALL distinguir "nenhum resultado" de erro: uma busca sem correspondência SHALL responder com sucesso, lista vazia e uma mensagem explicativa.

#### Scenario: Nenhum serviço corresponde à busca
- **WHEN** nenhum serviço atende à categoria, ao raio ou à cidade informados
- **THEN** a resposta é bem-sucedida, com lista de resultados vazia e a mensagem "Nenhum resultado encontrado nesta região"
