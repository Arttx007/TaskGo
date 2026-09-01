## Purpose

Cobre o que o painel autenticado do cliente pode afirmar a quem já tem conta. Define que saldo, transação, profissional, endereço, código de confirmação e contagem exibidos correspondem a dado real da conta, que todo controle visível tem efeito observável, e como a busca, o simulador de custo e o aviso de atividade do painel se apoiam nos endpoints reais da plataforma.

## Requirements

### Requirement: Nenhum dado fabricado no painel do cliente

O painel do cliente MUST NOT exibir saldo, transação, meio de pagamento, profissional, endereço, código de confirmação, horário, distância, contagem ou identificação de dispositivo que não corresponda a registro real da conta autenticada. Quando não houver dado real para preencher uma área, a área SHALL apresentar estado vazio explicativo ou ser omitida, em vez de ser preenchida com exemplo ilustrativo.

O nome, os dados de contato e a foto exibidos no painel SHALL vir da conta autenticada.

#### Scenario: Conta sem histórico
- **WHEN** um cliente recém-cadastrado abre o painel
- **THEN** as áreas de pedidos, extrato, favoritos e endereços apresentam estado vazio, e nenhum pedido, transação, profissional ou endereço de exemplo aparece em seu lugar

#### Scenario: Identificação do cliente vem da conta
- **WHEN** um cliente autenticado abre qualquer aba do painel
- **THEN** o nome e os dados de contato exibidos são os da conta autenticada, e nenhum nome, e-mail, telefone ou foto de exemplo aparece na tela

#### Scenario: Localidade exibida corresponde ao cliente
- **WHEN** o painel exibe a localidade do cliente
- **THEN** ela vem do endereço padrão cadastrado ou da posição informada pelo navegador, e não de um valor fixo na página

### Requirement: Todo controle do painel tem efeito observável

Todo campo, botão, alternador e ação visível no painel do cliente SHALL produzir efeito verificável: uma consulta à plataforma, uma alteração persistida ou uma mudança de navegação. Um controle que apenas exiba confirmação de sucesso, altere a própria tela ou aguarde um intervalo antes de anunciar conclusão MUST NOT permanecer no painel.

Ação que altere dado da conta SHALL refletir o resultado devolvido pela plataforma, e SHALL informar a falha quando a operação for recusada, em vez de anunciar sucesso.

#### Scenario: Ação anuncia sucesso somente após confirmação da plataforma
- **WHEN** o cliente aciona uma ação que altera dado da conta
- **THEN** a confirmação de sucesso só é apresentada depois de a plataforma confirmar a alteração, e a mesma alteração continua visível ao recarregar a página

#### Scenario: Ação recusada pela plataforma
- **WHEN** uma ação do painel é recusada pela plataforma
- **THEN** o painel informa a recusa ao cliente e nenhuma confirmação de sucesso é apresentada

#### Scenario: Remoção de item é persistida
- **WHEN** o cliente remove um item de uma lista do painel
- **THEN** o item deixa de aparecer também depois de recarregar a página

### Requirement: Busca de serviços no painel usa a busca real da plataforma

A aba de busca do painel SHALL oferecer as categorias que a plataforma reconhece como disponíveis e SHALL executar a busca de serviços da plataforma, apresentando os serviços devolvidos com prestador, categoria, preço, nota e distância quando houver. O painel MUST NOT apresentar lista de profissionais que não tenha vindo de uma busca executada.

A busca SHALL usar a posição informada pelo navegador quando disponível e, na ausência dela, o endereço padrão do cliente. Quando a plataforma devolver resultado vazio com mensagem explicativa, o painel SHALL apresentar essa mensagem.

A contratação a partir de um resultado SHALL abrir uma solicitação real para o serviço escolhido.

#### Scenario: Categorias oferecidas vêm da plataforma
- **WHEN** o cliente abre a aba de busca
- **THEN** as categorias oferecidas são as que a plataforma reconhece como tendo serviço disponível, e nenhuma categoria fixa na página é apresentada

#### Scenario: Resultados vêm de busca executada
- **WHEN** o cliente escolhe uma categoria
- **THEN** os profissionais exibidos são os devolvidos pela busca da plataforma, cada um com o preço e a nota registrados

#### Scenario: Painel aberto sem busca executada
- **WHEN** o cliente abre a aba de busca e ainda não escolheu categoria nem informou localidade
- **THEN** nenhum profissional é exibido e o painel indica o que falta para buscar

#### Scenario: Busca sem resultado
- **WHEN** a busca não encontra serviço algum para a categoria e a localidade informadas
- **THEN** o painel apresenta a mensagem devolvida pela plataforma e nenhum profissional de exemplo aparece

#### Scenario: Contratação a partir de um resultado
- **WHEN** o cliente aciona a contratação de um serviço devolvido pela busca
- **THEN** uma solicitação real é aberta para aquele serviço e passa a aparecer entre os pedidos do cliente

### Requirement: Simulação de custo apresenta preço praticado

O simulador de custo do painel SHALL apresentar a faixa de preço realmente praticada na categoria escolhida, obtida da plataforma, e MUST NOT apresentar valor calculado na própria página. Quando a plataforma indicar amostra insuficiente para apurar a faixa, o simulador SHALL informar isso em vez de apresentar um valor.

A quantidade de profissionais disponíveis, quando exibida, SHALL corresponder ao número de resultados de uma busca executada; na ausência de busca, SHALL ser omitida.

#### Scenario: Faixa de preço com amostra suficiente
- **WHEN** o cliente simula o custo de uma categoria com serviços suficientes cadastrados
- **THEN** a faixa apresentada é a apurada pela plataforma a partir dos preços praticados

#### Scenario: Amostra insuficiente para apurar preço
- **WHEN** o cliente simula o custo de uma categoria sem serviços suficientes cadastrados
- **THEN** o simulador informa que não há dado suficiente e nenhum valor é apresentado

#### Scenario: Disponibilidade exibida corresponde a busca executada
- **WHEN** o simulador exibe quantos profissionais estão disponíveis
- **THEN** o número corresponde à quantidade de resultados devolvidos pela busca, e nenhuma contagem fixa é apresentada

### Requirement: Aviso de atividade derivado do estado da conta

O sistema SHALL expor em `GET /clientes/me/notificacoes` os avisos de atividade do cliente autenticado, apurados do estado corrente da conta e não de registro armazenado de aviso. SHALL considerar solicitação aceita aguardando pagamento, pagamento retido em custódia, serviço concluído aguardando avaliação, solicitação recusada ou cancelada, e mensagens ainda não lidas.

Cada aviso SHALL trazer o tipo, o texto, a solicitação relacionada e o momento do fato que o originou, dos mais recentes para os mais antigos. A quantidade exibida ao cliente SHALL ser a quantidade de avisos devolvida.

Como os avisos são apurados do estado, o sistema MUST NOT oferecer marcação de aviso como lido: um aviso SHALL deixar de existir quando o fato que o originou for resolvido.

#### Scenario: Avisos apurados do estado da conta
- **WHEN** um cliente com uma solicitação aceita e não paga e um serviço concluído sem avaliação consulta seus avisos
- **THEN** recebe um aviso de pagamento pendente e um aviso de avaliação pendente, cada um apontando para a solicitação correspondente

#### Scenario: Aviso desaparece quando o fato é resolvido
- **WHEN** o cliente avalia o serviço que gerava um aviso de avaliação pendente e consulta novamente seus avisos
- **THEN** aquele aviso não aparece mais, sem que nenhuma marcação de leitura tenha sido feita

#### Scenario: Conta sem pendência alguma
- **WHEN** um cliente sem solicitação pendente, pagamento pendente, avaliação pendente ou mensagem não lida consulta seus avisos
- **THEN** a resposta é bem-sucedida com lista vazia e o painel não exibe quantidade alguma

#### Scenario: Avisos são restritos à conta autenticada
- **WHEN** um cliente autenticado consulta seus avisos
- **THEN** nenhum aviso originado de solicitação de outra conta aparece na resposta

#### Scenario: Consulta sem autenticação
- **WHEN** os avisos são consultados sem token válido
- **THEN** a requisição é recusada e nenhum aviso é devolvido

### Requirement: Painel do cliente não oferece recurso que a plataforma não sustenta

O painel do cliente MUST NOT oferecer carteira de saldo do cliente, cadastro de meio de pagamento, segundo fator de autenticação, listagem ou encerramento de sessões ativas, preferência de canal de notificação, nem assistente de conversação automatizada, enquanto a plataforma não sustentar cada um deles.

Conteúdo informativo estático — perguntas frequentes e artigos de ajuda — SHALL ser permitido, por não se apresentar como dado da conta.

#### Scenario: Nenhuma carteira ou cartão no painel
- **WHEN** o cliente abre a aba de pagamentos
- **THEN** nenhum saldo de carteira, meio de pagamento cadastrado ou ação de adicionar cartão ou saldo é apresentado

#### Scenario: Nenhum controle de segurança sem efeito
- **WHEN** o cliente abre as configurações da conta
- **THEN** nenhum controle de segundo fator, nenhuma listagem de sessões ativas e nenhuma preferência de canal de notificação é apresentada

#### Scenario: Nenhuma conversação automatizada
- **WHEN** o cliente abre a central de ajuda
- **THEN** as perguntas frequentes e os artigos são apresentados, e nenhum assistente de conversação automatizada é oferecido
