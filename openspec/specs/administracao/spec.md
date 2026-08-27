## Purpose

Cobre as operações do administrador da plataforma: avaliar a verificação de prestadores e ajustar os parâmetros de negócio sem novo deploy.

## Requirements

### Requirement: Conta administradora existe desde a primeira execução

O sistema SHALL garantir que exista ao menos um administrador ao iniciar, criando-o a partir das credenciais configuradas quando não houver nenhum. A criação SHALL ser idempotente: nenhum administrador adicional é criado em execuções seguintes. Não há autocadastro de administrador.

#### Scenario: Primeira inicialização
- **WHEN** a aplicação inicia e não existe nenhum administrador
- **THEN** um administrador é criado com as credenciais configuradas e passa a poder autenticar

#### Scenario: Inicializações seguintes
- **WHEN** a aplicação inicia e já existe ao menos um administrador
- **THEN** nenhum administrador novo é criado

### Requirement: Fila de verificações pendentes

O administrador autenticado SHALL consultar em `GET /admin/prestadores/pendentes` os prestadores com verificação `PENDENTE`, para avaliá-los.

#### Scenario: Consulta da fila
- **WHEN** o administrador consulta a fila de verificações
- **THEN** recebe apenas os prestadores com verificação pendente, com nome, especialidade, cidade e e-mail

#### Scenario: Consulta por conta não administradora
- **WHEN** uma conta de cliente ou prestador consulta a fila
- **THEN** o acesso é negado e nenhum dado é devolvido

### Requirement: Consulta dos documentos enviados

O administrador SHALL obter cada documento enviado por um prestador via `GET /admin/prestadores/{id}/documentos/{tipo}`, com `tipo` sendo o documento de identidade ou o comprovante Pix, para conferir antes de decidir.

#### Scenario: Documento existente
- **WHEN** o administrador solicita um documento que o prestador já enviou
- **THEN** o arquivo é devolvido com o tipo de conteúdo correspondente

#### Scenario: Documento não enviado ou tipo desconhecido
- **WHEN** o prestador ainda não enviou aquele documento, ou o tipo pedido não é reconhecido
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Aprovação e rejeição da verificação

O administrador SHALL aprovar (`PUT /admin/prestadores/{id}/kyc/aprovar`) ou rejeitar (`PUT /admin/prestadores/{id}/kyc/rejeitar`) a verificação de um prestador. A decisão SHALL ter efeito imediato sobre o que o prestador pode fazer.

#### Scenario: Aprovação
- **WHEN** o administrador aprova a verificação de um prestador
- **THEN** o status de verificação passa a `APROVADO` e o prestador passa a poder publicar serviços e aparecer em buscas

#### Scenario: Rejeição
- **WHEN** o administrador rejeita a verificação de um prestador
- **THEN** o status de verificação passa a `REJEITADO` e o prestador deixa de poder publicar serviços

#### Scenario: Motivo da rejeição não é persistido
- **WHEN** a rejeição é enviada acompanhada de um motivo
- **THEN** a rejeição é aplicada, mas o motivo informado não é armazenado nem devolvido em consultas posteriores

#### Scenario: Prestador inexistente
- **WHEN** a decisão referencia um prestador que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Ajuste de parâmetros de negócio sem deploy

O administrador SHALL listar (`GET /admin/parametros`) e atualizar (`PUT /admin/parametros/{chave}`) os parâmetros de negócio, como os componentes do cálculo da taxa e o raio padrão de busca. Um novo valor SHALL passar a valer nas operações seguintes, sem reinício da aplicação.

#### Scenario: Atualização de um parâmetro
- **WHEN** o administrador altera o valor de um parâmetro existente
- **THEN** o novo valor é persistido e passa a ser aplicado nos cálculos seguintes

#### Scenario: Parâmetro inexistente
- **WHEN** a atualização referencia uma chave que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`, e nenhum parâmetro novo é criado

#### Scenario: Valor ausente
- **WHEN** a atualização não informa valor
- **THEN** a resposta é 400 com código `VALIDACAO`
