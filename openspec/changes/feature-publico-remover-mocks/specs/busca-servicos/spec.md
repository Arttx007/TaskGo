## ADDED Requirements

### Requirement: Catálogo público de categorias disponíveis

O sistema SHALL expor em `GET /servicos-ofertados/categorias`, sem exigir autenticação, as categorias que têm ao menos um serviço disponível, cada uma acompanhada da quantidade de serviços disponíveis naquela categoria. SHALL considerar apenas serviços com status `ATIVO` cujo prestador esteja com verificação `APROVADO`, pelo mesmo critério da busca.

Como a categoria é texto livre informado pelo prestador, o catálogo SHALL refletir as categorias efetivamente cadastradas, sem depender de uma lista fixa.

#### Scenario: Consulta sem estar autenticado
- **WHEN** um visitante consulta as categorias disponíveis sem enviar token
- **THEN** a consulta é executada normalmente e devolve as categorias com serviço disponível

#### Scenario: Contagem por categoria
- **WHEN** existem vários serviços disponíveis na mesma categoria
- **THEN** a categoria aparece uma única vez, com a quantidade de serviços disponíveis nela

#### Scenario: Categoria sem serviço disponível é omitida
- **WHEN** todos os serviços de uma categoria estão inativos, ou pertencem a prestadores não aprovados
- **THEN** essa categoria não aparece no catálogo

#### Scenario: Nenhuma categoria disponível
- **WHEN** não existe nenhum serviço disponível na plataforma
- **THEN** a resposta é bem-sucedida com lista vazia

### Requirement: Faixa de preço praticada por categoria

O sistema SHALL expor em `GET /servicos-ofertados/estimativa` uma faixa de preço apurada a partir dos preços realmente publicados, sem exigir autenticação, recebendo `categoria` como parâmetro obrigatório. A faixa SHALL trazer o menor preço, a mediana e o maior preço da categoria, além do tamanho da amostra considerada. SHALL considerar apenas serviços com status `ATIVO` cujo prestador esteja com verificação `APROVADO`.

Quando a amostra tiver menos de três serviços, o sistema MUST NOT devolver a faixa, e SHALL devolver apenas a mensagem explicativa — com um ou dois serviços a faixa revelaria o preço de um prestador identificável.

O sistema SHALL distinguir "sem dado suficiente" de erro: a ausência de amostra SHALL responder com sucesso e mensagem explicativa.

#### Scenario: Faixa apurada de uma categoria
- **WHEN** um visitante consulta a estimativa de uma categoria com três ou mais serviços disponíveis
- **THEN** a resposta traz o menor preço, a mediana, o maior preço e o tamanho da amostra considerada

#### Scenario: Amostra insuficiente para revelar faixa
- **WHEN** a categoria consultada tem apenas um ou dois serviços disponíveis
- **THEN** nenhum valor de faixa é devolvido, e a resposta é bem-sucedida com a mensagem de que ainda não há preços suficientes na categoria

#### Scenario: Categoria sem nenhum serviço
- **WHEN** a categoria consultada não tem serviço disponível algum
- **THEN** a resposta é bem-sucedida, sem valores de faixa, com a mensagem de que ainda não há preços na categoria

#### Scenario: Serviços indisponíveis não entram na apuração
- **WHEN** existem, na categoria consultada, serviços inativos ou de prestadores não aprovados
- **THEN** os preços desses serviços não influenciam o menor preço, a mediana, o maior preço nem o tamanho da amostra

#### Scenario: Categoria ausente
- **WHEN** a requisição não informa a categoria
- **THEN** a resposta é 400 com código `VALIDACAO` e nenhuma faixa é apurada

### Requirement: Filtro por nota mínima e faixa de preço

A busca SHALL aceitar `notaMinima`, `precoMin` e `precoMax`, todos opcionais, e SHALL descartar os serviços que não os satisfaçam antes de devolver os resultados. O filtro SHALL ser aplicado pelo sistema, não pelo consumidor: a resposta MUST NOT conter resultado fora dos limites informados.

`notaMinima` SHALL comparar com a nota média do prestador. Prestador sem nota média — que ainda não recebeu avaliação — SHALL ser descartado quando `notaMinima` for informada, porque não há como afirmar que ele alcança o mínimo pedido.

`precoMin` e `precoMax` SHALL comparar com o preço do serviço, inclusive nos extremos. Cada um SHALL poder ser informado sem o outro, permitindo faixa aberta em qualquer das pontas.

Os filtros SHALL compor com os critérios que já existem — categoria, raio, cidade — e MUST NOT alterar a ordenação por proximidade nem a mensagem de resultado vazio.

#### Scenario: Filtro por nota mínima
- **WHEN** a busca informa nota mínima de 4
- **THEN** apenas serviços de prestadores cuja nota média alcança 4 aparecem nos resultados

#### Scenario: Prestador sem nota média e nota mínima informada
- **WHEN** a busca informa nota mínima e existe, na categoria, serviço de prestador que ainda não recebeu avaliação
- **THEN** esse serviço não aparece nos resultados

#### Scenario: Prestador sem nota média e nota mínima omitida
- **WHEN** a busca não informa nota mínima e existe serviço de prestador que ainda não recebeu avaliação
- **THEN** esse serviço aparece normalmente nos resultados

#### Scenario: Faixa de preço fechada
- **WHEN** a busca informa preço mínimo e preço máximo
- **THEN** apenas serviços com preço entre os dois valores, incluindo os extremos, aparecem nos resultados

#### Scenario: Faixa de preço aberta em uma das pontas
- **WHEN** a busca informa apenas preço máximo, ou apenas preço mínimo
- **THEN** o filtro é aplicado somente naquela ponta, sem limitar a outra

#### Scenario: Filtros compõem com raio e ordenação
- **WHEN** a busca informa coordenadas, raio, nota mínima e faixa de preço ao mesmo tempo
- **THEN** os resultados satisfazem todos os critérios simultaneamente e continuam ordenados do mais próximo ao mais distante

#### Scenario: Filtro sem correspondência
- **WHEN** nenhum serviço da categoria satisfaz os filtros informados
- **THEN** a resposta é bem-sucedida, com lista de resultados vazia e a mensagem explicativa de que nada foi encontrado

#### Scenario: Filtros omitidos preservam o comportamento anterior
- **WHEN** a busca não informa nota mínima nem faixa de preço
- **THEN** nenhum serviço é descartado por esses critérios, e o resultado é o mesmo de antes de os filtros existirem

### Requirement: Busca dos prestadores ainda não avaliados

A busca SHALL aceitar `apenasSemAvaliacao`, opcional, e quando verdadeiro SHALL devolver apenas serviços de prestadores que ainda não têm nota média. Isso existe para que um prestador recém-aprovado tenha caminho de visibilidade mesmo quando um filtro de nota o exclui da busca comum.

O critério SHALL compor com categoria, raio, cidade e faixa de preço, e SHALL preservar a ordenação por proximidade.

Como `apenasSemAvaliacao` e `notaMinima` são critérios contraditórios — um pede quem não tem nota, o outro pede quem alcança um mínimo — informar os dois na mesma busca SHALL ser recusado com 400 e código `VALIDACAO`. O sistema MUST NOT ignorar em silêncio um dos dois.

#### Scenario: Apenas prestadores sem avaliação
- **WHEN** a busca informa `apenasSemAvaliacao` verdadeiro
- **THEN** todos os resultados são de prestadores sem nota média, e nenhum prestador já avaliado aparece

#### Scenario: Critério omitido não altera a busca
- **WHEN** a busca não informa `apenasSemAvaliacao`
- **THEN** prestadores avaliados e não avaliados concorrem normalmente, conforme os demais critérios

#### Scenario: Composição com localidade e preço
- **WHEN** a busca informa `apenasSemAvaliacao` junto de coordenadas, raio e faixa de preço
- **THEN** os resultados satisfazem todos esses critérios e vêm ordenados do mais próximo ao mais distante

#### Scenario: Combinação contraditória com nota mínima
- **WHEN** a busca informa `apenasSemAvaliacao` verdadeiro e também nota mínima
- **THEN** a resposta é 400 com código `VALIDACAO` e nenhuma busca é executada

#### Scenario: Nenhum prestador novo na região
- **WHEN** a busca por prestadores sem avaliação não encontra ninguém
- **THEN** a resposta é bem-sucedida, com lista vazia e a mensagem explicativa

## MODIFIED Requirements

### Requirement: Busca pública por categoria

O sistema SHALL expor a busca de serviços em `GET /servicos-ofertados/buscar` sem exigir autenticação, recebendo `categoria` (obrigatória) e, opcionalmente, `lat`, `lon`, `raioKm`, `cidade`, `notaMinima`, `precoMin`, `precoMax` e `apenasSemAvaliacao`. A busca SHALL considerar apenas serviços com status `ATIVO` cujo prestador esteja com verificação `APROVADO`.

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

Cada resultado SHALL trazer também a posição aproximada do serviço, para que possa ser situado em um mapa. A posição SHALL ser arredondada antes de sair da plataforma, com precisão suficiente para orientar o cliente e insuficiente para identificar o endereço exato do prestador. Resultado cuja localização não tenha coordenadas cadastradas SHALL vir sem posição.

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
- **THEN** ele é devolvido sem distância calculada, sem posição aproximada, e não é descartado pelo filtro de raio

#### Scenario: Posição aproximada acompanha cada resultado
- **WHEN** a busca devolve um serviço cuja localização tem coordenadas cadastradas
- **THEN** o resultado traz a posição aproximada do serviço, arredondada, permitindo situá-lo em um mapa

#### Scenario: Posição devolvida não revela o endereço exato
- **WHEN** dois serviços têm coordenadas cadastradas muito próximas entre si
- **THEN** as posições devolvidas são arredondadas na mesma precisão, de modo que a resposta não permite distinguir o endereço exato de cada prestador
