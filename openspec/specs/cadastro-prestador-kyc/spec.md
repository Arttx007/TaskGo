## Purpose

Cobre a criação da conta do prestador, o envio dos documentos de verificação (KYC) e o efeito do status de verificação sobre o que ele pode fazer na plataforma.

## Requirements

### Requirement: Cadastro público de prestador

O sistema SHALL permitir criar uma conta de prestador sem autenticação, via `POST /prestadores`, recebendo `nome`, `especialidade`, `cidade`, `email` e `senha`. `nome`, `email` e `senha` SHALL ser obrigatórios, com e-mail em formato válido e senha de no mínimo 8 caracteres. A conta recém-criada SHALL nascer com status de verificação `PENDENTE` e saldo disponível zero.

#### Scenario: Cadastro bem-sucedido
- **WHEN** um visitante envia nome, e-mail válido e senha com ao menos 8 caracteres
- **THEN** a resposta é 201, o prestador é criado com `statusKyc` igual a `PENDENTE` e saldo disponível zero

#### Scenario: Dados de entrada inválidos
- **WHEN** falta um campo obrigatório, o e-mail é inválido ou a senha tem menos de 8 caracteres
- **THEN** a resposta é 400 com código `VALIDACAO` e os campos recusados em `fieldErrors`

### Requirement: Envio dos documentos de verificação

O prestador autenticado SHALL enviar dois documentos — documento de identidade e comprovante de chave Pix — via `POST /prestadores/{id}/documentos`, em uma requisição multipart. O sistema SHALL aceitar apenas arquivos PNG, JPEG ou PDF, e SHALL recusar arquivos acima do limite configurado de tamanho.

#### Scenario: Envio válido dos dois documentos
- **WHEN** o prestador autenticado envia os dois arquivos em formato aceito
- **THEN** os documentos são armazenados, ficam associados ao seu cadastro e o `statusKyc` fica `PENDENTE` de avaliação

#### Scenario: Formato de arquivo não aceito
- **WHEN** algum dos arquivos enviados não é PNG, JPEG nem PDF
- **THEN** a resposta é 400 com código `ARQUIVO_INVALIDO` e nenhum documento é associado ao cadastro

#### Scenario: Arquivo ausente ou vazio
- **WHEN** um dos dois arquivos exigidos não é enviado ou está vazio
- **THEN** a resposta é 400 com código `ARQUIVO_INVALIDO`

#### Scenario: Arquivo acima do limite de tamanho
- **WHEN** um dos arquivos enviados excede o limite configurado de 5 MB
- **THEN** a resposta é 500 com código `ERRO_INTERNO` e a mensagem genérica, sem indicar que a causa foi o tamanho do arquivo, e nenhum documento é armazenado

#### Scenario: Envio para o cadastro de outro prestador
- **WHEN** um prestador autenticado tenta enviar documentos informando o identificador de outro prestador
- **THEN** a resposta é 403 `ACESSO_NEGADO` e nenhum arquivo é armazenado

#### Scenario: Reenvio após rejeição
- **WHEN** um prestador com verificação `REJEITADO` envia novos documentos
- **THEN** os documentos anteriores são substituídos e o status volta para `PENDENTE`, devolvendo o cadastro à fila de avaliação

#### Scenario: Recusa de arquivo só é sinalizada pelo servidor
- **WHEN** o usuário escolhe na interface de envio um arquivo de formato ou tamanho não aceito
- **THEN** a interface permite tentar o envio e só exibe a recusa quando o servidor responde com erro

### Requirement: Consulta do próprio status de verificação

O sistema SHALL permitir consultar um prestador por identificador via `GET /prestadores/{id}`, devolvendo entre outros o `statusKyc` corrente, para que o painel do prestador saiba em que etapa da verificação ele está.

#### Scenario: Painel consulta o status após o cadastro
- **WHEN** o prestador autenticado consulta o próprio cadastro
- **THEN** a resposta traz nome, especialidade, cidade, e-mail, nota média e o `statusKyc` atual

#### Scenario: Painel do prestador com verificação não aprovada
- **WHEN** o painel do prestador carrega e o `statusKyc` não é `APROVADO`
- **THEN** a interface exibe o aviso correspondente ao status, diferenciando cadastro em análise de cadastro rejeitado

### Requirement: Verificação aprovada como pré-requisito de operação

O sistema SHALL impedir que um prestador sem `statusKyc` igual a `APROVADO` publique serviços, e SHALL mantê-lo fora dos resultados de busca de clientes.

#### Scenario: Publicação de serviço sem verificação aprovada
- **WHEN** um prestador com verificação `PENDENTE` ou `REJEITADO` tenta publicar um serviço
- **THEN** a resposta é 422 com código `KYC_PENDENTE` e nenhum serviço é criado

#### Scenario: Prestador não aprovado não aparece em busca
- **WHEN** um cliente busca serviços de uma categoria em que existe serviço ativo de prestador não aprovado
- **THEN** esse serviço não aparece entre os resultados
