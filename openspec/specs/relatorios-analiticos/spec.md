## Purpose

Cobre os números agregados que a plataforma expõe sobre a própria operação e o cadastro das dimensões de localização e tempo usadas por esses agregados.

## Requirements

### Requirement: Contagens gerais da plataforma

O sistema SHALL expor em `GET /dashboard` as contagens totais de serviços, clientes e prestadores, em formato JSON.

#### Scenario: Consulta das contagens
- **WHEN** uma conta autenticada consulta o painel de contagens
- **THEN** recebe os totais de serviços, clientes e prestadores registrados

#### Scenario: Consulta sem autenticação
- **WHEN** a consulta é feita sem token válido
- **THEN** a requisição é rejeitada e nenhum número é devolvido

#### Scenario: Acesso não é restrito a administradores
- **WHEN** uma conta autenticada como cliente ou prestador consulta o painel de contagens
- **THEN** os totais são devolvidos normalmente

### Requirement: Relatório por situação da solicitação

O sistema SHALL expor em `GET /relatorio` o total de solicitações e as contagens por situação. O campo que representa as solicitações aceitas SHALL continuar sendo publicado com o nome `agendados`, preservando compatibilidade com consumidores anteriores à renomeação do estado.

#### Scenario: Consulta do relatório
- **WHEN** uma conta autenticada consulta o relatório
- **THEN** recebe o total de solicitações e as contagens de aceitas, canceladas e concluídas

#### Scenario: Nome de campo preservado
- **WHEN** o relatório devolve a contagem de solicitações no estado `ACEITO`
- **THEN** ela é publicada sob o nome `agendados`, e não sob o nome do estado atual

### Requirement: Painel administrativo de contagens em texto

O sistema SHALL expor em `GET /admin/dashboard` um resumo de contagens restrito a administradores. A resposta SHALL ser uma linha de texto, e não um objeto JSON.

#### Scenario: Consulta pelo administrador
- **WHEN** o administrador consulta o painel administrativo de contagens
- **THEN** recebe uma linha de texto com as contagens de clientes, prestadores e serviços

#### Scenario: Consulta por conta não administradora
- **WHEN** uma conta de cliente ou prestador consulta esse painel
- **THEN** o acesso é negado

### Requirement: Cadastro das dimensões de localização e tempo

O sistema SHALL permitir criar e listar localizações (`/localizacoes`) e períodos de tempo (`/tempos`) por meio de requisições autenticadas. Uma localização SHALL poder registrar cidade, estado, bairro, latitude e longitude, e é o que habilita o cálculo de distância nas buscas.

#### Scenario: Cadastro de localização com coordenadas
- **WHEN** uma conta autenticada cadastra uma localização com latitude e longitude
- **THEN** a localização passa a existir e pode ser vinculada a serviços ofertados, habilitando a busca por proximidade

#### Scenario: Cadastro sem autenticação
- **WHEN** a criação de localização ou de tempo é tentada sem token válido
- **THEN** a requisição é rejeitada

#### Scenario: Acesso não é restrito a administradores
- **WHEN** uma conta autenticada como cliente ou prestador cria ou lista localizações ou tempos
- **THEN** a operação é executada normalmente

### Requirement: Registro temporal criado sob demanda

O sistema SHALL associar cada solicitação a um registro de data, criando-o automaticamente para a data corrente quando ainda não existir, sem exigir cadastro prévio.

#### Scenario: Primeira solicitação do dia
- **WHEN** a primeira solicitação de uma data é aberta e não há registro para aquele dia
- **THEN** o registro da data é criado automaticamente e associado à solicitação

#### Scenario: Solicitações seguintes no mesmo dia
- **WHEN** outra solicitação é aberta na mesma data
- **THEN** ela reutiliza o registro já existente, sem criar um segundo
