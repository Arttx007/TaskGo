## ADDED Requirements

### Requirement: Leitura pública de avaliações recentes

O sistema SHALL expor em `GET /avaliacoes/recentes` as avaliações mais recentes da plataforma, sem exigir autenticação, recebendo opcionalmente `limite`. SHALL considerar apenas solicitações em `AVALIADO` que tenham nota registrada e comentário não vazio, e cujo prestador esteja com verificação `APROVADO`. As avaliações SHALL vir da mais recente para a mais antiga.

Cada avaliação devolvida SHALL trazer a nota, o comentário, a categoria do serviço, a cidade e a data. Para preservar a privacidade de quem avaliou, a resposta SHALL identificar o cliente apenas pelo primeiro nome e MUST NOT expor identificador, e-mail ou nome completo de cliente algum.

Quando `limite` não for informado, o sistema SHALL aplicar um padrão; quando for informado acima do teto aceito, SHALL aplicar o teto em vez de recusar a requisição.

#### Scenario: Consulta sem estar autenticado
- **WHEN** um visitante consulta as avaliações recentes sem enviar token
- **THEN** a consulta é executada normalmente e devolve as avaliações mais recentes

#### Scenario: Dados devolvidos por avaliação
- **WHEN** existe avaliação registrada com comentário
- **THEN** ela é devolvida com nota, comentário, categoria, cidade e data, identificando o cliente apenas pelo primeiro nome

#### Scenario: Avaliação sem comentário é omitida
- **WHEN** uma solicitação avaliada tem nota registrada mas nenhum comentário, ou comentário vazio
- **THEN** ela não aparece entre as avaliações devolvidas

#### Scenario: Solicitação ainda não avaliada é omitida
- **WHEN** uma solicitação está em `SOLICITADO`, `ACEITO`, `CONCLUIDO`, `RECUSADO` ou `CANCELADO`
- **THEN** ela não aparece entre as avaliações devolvidas

#### Scenario: Avaliação de prestador não aprovado é omitida
- **WHEN** existe avaliação de uma solicitação cujo prestador não está com verificação `APROVADO`
- **THEN** ela não aparece entre as avaliações devolvidas

#### Scenario: Ordem das avaliações
- **WHEN** existem várias avaliações com comentário em datas diferentes
- **THEN** elas vêm da mais recente para a mais antiga

#### Scenario: Limite acima do teto aceito
- **WHEN** a consulta informa um limite maior que o teto aceito
- **THEN** a resposta é bem-sucedida e devolve no máximo a quantidade do teto

#### Scenario: Nenhuma avaliação disponível
- **WHEN** não existe avaliação alguma com comentário na plataforma
- **THEN** a resposta é bem-sucedida com lista vazia

#### Scenario: Nenhum dado de contato é exposto
- **WHEN** um visitante não autenticado consulta as avaliações recentes
- **THEN** nenhum identificador, e-mail ou nome completo de cliente aparece na resposta
