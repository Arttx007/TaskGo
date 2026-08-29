## Purpose

Cobre o que as superfícies públicas do TaskGo — a home, o catálogo de serviços e a lista de profissionais próximos — podem afirmar a um visitante que ainda não tem conta. Define que pessoa, avaliação, preço e métrica exibidos correspondem a dado real da plataforma, que todo controle visível tem efeito observável e que todo link leva a um destino existente.

## ADDED Requirements

### Requirement: Nenhuma pessoa ou avaliação fabricada nas páginas públicas

As páginas públicas MUST NOT exibir prestador, cliente, depoimento, nota ou contagem de avaliações que não corresponda a registro real da plataforma. Quando não houver dado real suficiente para preencher uma área de prova social, a área SHALL ser omitida em vez de preenchida com exemplo ilustrativo.

Prova social exibida ao visitante SHALL vir da leitura pública de avaliações e SHALL identificar quem avaliou apenas pelo primeiro nome.

#### Scenario: Depoimentos vindos de avaliações reais
- **WHEN** um visitante abre uma página pública que exibe depoimentos e existem avaliações registradas com comentário
- **THEN** são exibidas avaliações reais, cada uma com a nota registrada, o comentário escrito pelo cliente e apenas o primeiro nome de quem avaliou

#### Scenario: Sem avaliações para exibir
- **WHEN** nenhuma avaliação com comentário existe na plataforma
- **THEN** a área de depoimentos não é exibida, e nenhum depoimento de exemplo aparece em seu lugar

#### Scenario: Vitrine da home não apresenta prestadores nomeados
- **WHEN** um visitante abre a home
- **THEN** a vitrine de destaque apresenta categorias de serviço disponíveis, e nenhum nome, foto, distância, nota ou contagem de avaliações de prestador individual é exibido

### Requirement: Métricas exibidas ao visitante correspondem a dado apurado

Toda métrica numérica exibida em página pública SHALL ser derivada de dado devolvido pela plataforma no momento da visita. Métrica que a plataforma não apura MUST NOT ser exibida com valor fixo no markup.

#### Scenario: Contagem de profissionais reflete a busca
- **WHEN** o visitante realiza uma busca e a plataforma devolve resultados
- **THEN** o contador de profissionais exibido é a quantidade de resultados efetivamente devolvidos

#### Scenario: Busca sem resultado zera o contador
- **WHEN** a busca não devolve nenhum resultado
- **THEN** o contador exibido é zero, e não permanece com o valor de uma busca anterior

#### Scenario: Métrica sem dado de origem é omitida
- **WHEN** uma métrica exibida não tem correspondente apurável na plataforma, como tempo médio de resposta
- **THEN** a métrica não é exibida

### Requirement: Todo controle visível na página pública tem efeito observável

Campo, botão, seletor ou filtro exibido em página pública SHALL produzir efeito observável ao ser acionado. Controle sem efeito MUST NOT permanecer visível.

Filtro exibido ao visitante SHALL ser aplicado pela plataforma sobre o conjunto buscado, e MUST NOT limitar-se a esconder resultados já exibidos na tela.

#### Scenario: Filtro de nota mínima filtra a busca
- **WHEN** o visitante escolhe uma nota mínima
- **THEN** a busca é refeita com esse critério e os resultados exibidos são os que a plataforma devolveu já filtrados

#### Scenario: Filtro de faixa de preço filtra a busca
- **WHEN** o visitante escolhe uma faixa de preço
- **THEN** a busca é refeita com essa faixa e apenas resultados dentro dela são exibidos

#### Scenario: Aplicar filtros não descarta a busca corrente
- **WHEN** o visitante confirma a aplicação dos filtros
- **THEN** a busca é refeita com os filtros escolhidos, sem recarregar a página e sem perder a categoria e a localidade já informadas

#### Scenario: Filtro sem correspondência
- **WHEN** os filtros escolhidos não deixam nenhum resultado
- **THEN** a página informa que nada foi encontrado, e não exibe resultados que desrespeitem os filtros

### Requirement: Prestador ainda não avaliado tem caminho de visibilidade

Quando um filtro de nota mínima excluir prestadores que ainda não foram avaliados, a lista de profissionais SHALL oferecer uma área própria apresentando esses prestadores, identificada como profissionais novos na região. A área SHALL respeitar a categoria, a localidade, o raio e a faixa de preço da busca corrente, e SHALL indicar que aqueles prestadores ainda não têm avaliação.

A área MUST NOT repetir prestador já presente na lista principal: ela SHALL ser exibida apenas quando o filtro de nota efetivamente excluiu alguém. Quando não houver filtro de nota ativo, os prestadores sem avaliação já concorrem na lista principal e a área SHALL ser omitida.

Prestador exibido nessa área MUST NOT receber nota, estrela ou contagem de avaliações — ele não tem nenhuma.

#### Scenario: Filtro de nota revela a área de profissionais novos
- **WHEN** o visitante filtra por nota mínima e existem, na mesma busca, prestadores ainda não avaliados
- **THEN** a lista principal traz apenas quem alcança a nota pedida, e uma área separada apresenta os prestadores novos da região

#### Scenario: Sem filtro de nota não há área separada
- **WHEN** o visitante busca sem informar nota mínima
- **THEN** prestadores sem avaliação aparecem na lista principal e a área de profissionais novos não é exibida, evitando que o mesmo prestador apareça duas vezes

#### Scenario: Nenhum prestador novo na região
- **WHEN** o visitante filtra por nota mínima e não existe prestador sem avaliação naquela busca
- **THEN** a área de profissionais novos não é exibida

#### Scenario: Área de novos respeita os demais critérios
- **WHEN** a busca corrente tem categoria, localidade, raio e faixa de preço definidos
- **THEN** os prestadores apresentados na área de novos satisfazem esses mesmos critérios

#### Scenario: Prestador novo não recebe nota inventada
- **WHEN** um prestador é apresentado na área de profissionais novos
- **THEN** nenhuma nota, estrela ou contagem de avaliações é exibida para ele, e a ausência de avaliação é declarada

#### Scenario: Controle sem contrapartida na plataforma é removido
- **WHEN** um seletor exibido não corresponde a nenhum critério que a plataforma saiba aplicar, como escala do serviço
- **THEN** o seletor não é exibido, e sua ausência não impede o envio da busca

### Requirement: Navegação pública sem destino morto

Link exibido em página pública SHALL apontar para um destino existente. Link cujo destino não existe MUST NOT ser exibido como link.

#### Scenario: Link de categoria leva à busca real
- **WHEN** o visitante aciona um link de categoria no rodapé ou no catálogo
- **THEN** ele chega à lista de profissionais com a busca daquela categoria já disparada

#### Scenario: Chamada principal leva ao catálogo
- **WHEN** o visitante aciona a chamada principal para começar uma busca
- **THEN** ele chega ao catálogo de serviços ou à lista de profissionais, e não permanece na mesma posição da página

#### Scenario: Promessa sem página correspondente não é exibida
- **WHEN** um item de navegação anuncia algo que a plataforma não oferece, como aplicativo para download, blog ou página institucional inexistente
- **THEN** o item não é exibido

### Requirement: Busca da home alcança a busca real da plataforma

O formulário de busca da home SHALL encaminhar a categoria e o raio escolhidos para a lista de profissionais, e a busca SHALL ser disparada com esses valores sem exigir que o visitante os informe de novo.

#### Scenario: Categoria e raio chegam à lista de profissionais
- **WHEN** o visitante escolhe uma categoria e um raio na home e envia o formulário
- **THEN** a lista de profissionais abre já buscando aquela categoria, com o raio escolhido aplicado

#### Scenario: Categoria não escolhida
- **WHEN** o visitante envia o formulário sem escolher categoria
- **THEN** a busca não é disparada e o visitante é informado de que precisa escolher um serviço

### Requirement: Estimativa de preço apresentada como preço praticado

Onde a interface pública apresentar faixa de preço, ela SHALL declarar que se trata do preço praticado pelos prestadores da categoria, apurado a partir dos serviços publicados. A interface pública MUST NOT atribuir a faixa a inteligência artificial, nem apresentar valor calculado no próprio navegador como estimativa da plataforma.

#### Scenario: Faixa de preço de uma categoria
- **WHEN** o visitante escolhe uma categoria que tem preços publicados em quantidade suficiente
- **THEN** é exibida a faixa praticada naquela categoria, identificada como preço praticado pelos prestadores

#### Scenario: Categoria sem preços suficientes
- **WHEN** a categoria escolhida não tem preços publicados em quantidade suficiente
- **THEN** nenhuma faixa é exibida, e a interface informa que ainda não há preços suficientes na categoria

#### Scenario: Nenhum preço é calculado no navegador
- **WHEN** o visitante interage com qualquer superfície pública de preço
- **THEN** todo valor exibido provém de consulta à plataforma, e nenhum valor é derivado de tabela fixa embutida na página
