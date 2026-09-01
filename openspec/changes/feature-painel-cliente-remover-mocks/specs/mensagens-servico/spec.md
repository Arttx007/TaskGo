## Purpose

Cobre a troca de mensagens de texto entre o cliente e o prestador de uma solicitação de serviço, restrita às duas partes envolvidas, com registro de quem escreveu, quando escreveu e o que já foi lido.

## ADDED Requirements

### Requirement: Conversa restrita às partes da solicitação

Toda mensagem SHALL pertencer a uma solicitação de serviço, e apenas o cliente dono e o prestador dono daquela solicitação SHALL ler ou escrever nela. Qualquer outra conta, inclusive outro cliente ou outro prestador, SHALL receber 403 com código `ACESSO_NEGADO` ao tentar ler ou escrever.

Não existe conversa fora de uma solicitação: o sistema MUST NOT oferecer troca de mensagens com um prestador com quem o cliente não tenha solicitação aberta.

#### Scenario: Cliente dono lê a conversa
- **WHEN** o cliente dono de uma solicitação consulta as mensagens dela
- **THEN** recebe a conversa daquela solicitação

#### Scenario: Prestador dono lê a conversa
- **WHEN** o prestador dono de uma solicitação consulta as mensagens dela
- **THEN** recebe a mesma conversa

#### Scenario: Conta que não participa da solicitação
- **WHEN** uma conta que não é o cliente nem o prestador da solicitação tenta ler ou escrever na conversa
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e nenhuma mensagem é devolvida ou registrada

#### Scenario: Solicitação inexistente
- **WHEN** a conversa de uma solicitação que não existe é consultada
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Consulta da conversa de uma solicitação

O sistema SHALL devolver em `GET /servicos/{id}/mensagens` as mensagens daquela solicitação, da mais antiga para a mais recente. Cada mensagem SHALL trazer o conteúdo, quem a escreveu — identificado pelo papel, cliente ou prestador, e pelo nome — o momento em que foi escrita e se já foi lida pela outra parte.

A consulta SHALL devolver o conjunto completo da conversa, sem paginação.

#### Scenario: Conversa em ordem cronológica
- **WHEN** uma das partes consulta uma conversa com várias mensagens
- **THEN** as mensagens vêm da mais antiga para a mais recente, cada uma indicando quem a escreveu e quando

#### Scenario: Conversa ainda sem mensagem
- **WHEN** uma das partes consulta a conversa de uma solicitação em que ninguém escreveu ainda
- **THEN** a resposta é bem-sucedida com lista vazia, e nenhuma mensagem de exemplo aparece

### Requirement: Envio de mensagem

Uma das partes da solicitação SHALL enviar mensagem via `POST /servicos/{id}/mensagens`, informando o conteúdo em texto. A mensagem SHALL registrar quem a escreveu e o momento do envio, e SHALL nascer não lida.

O conteúdo SHALL ser texto não vazio e SHALL respeitar um limite máximo de tamanho; conteúdo vazio, composto apenas de espaços, ou acima do limite SHALL ser recusado com 400 e código `VALIDACAO`. O sistema MUST NOT aceitar anexo, imagem ou arquivo.

O conteúdo escrito por uma das partes SHALL ser tratado como texto ao ser apresentado à outra: marcação enviada no conteúdo MUST NOT ser interpretada pela interface que a exibe.

#### Scenario: Mensagem enviada
- **WHEN** o cliente dono envia uma mensagem de texto na solicitação
- **THEN** a resposta é 201 e a mensagem passa a aparecer na conversa, atribuída a ele, com o momento do envio e ainda não lida

#### Scenario: Conteúdo vazio
- **WHEN** uma das partes envia mensagem sem conteúdo ou apenas com espaços
- **THEN** a resposta é 400 com código `VALIDACAO` e nenhuma mensagem é registrada

#### Scenario: Conteúdo acima do limite
- **WHEN** uma das partes envia mensagem com conteúdo acima do tamanho máximo aceito
- **THEN** a resposta é 400 com código `VALIDACAO` e nenhuma mensagem é registrada

#### Scenario: Marcação no conteúdo não é interpretada
- **WHEN** uma das partes envia conteúdo que contém marcação de página
- **THEN** a outra parte vê o conteúdo como texto, e a marcação não é interpretada pela interface

### Requirement: Marcação da conversa como lida

Uma das partes SHALL marcar como lidas, via `PUT /servicos/{id}/mensagens/lidas`, as mensagens daquela solicitação escritas pela outra parte. A marcação SHALL registrar o momento da leitura e MUST NOT alcançar as mensagens escritas por quem está marcando.

A quantidade de mensagens não lidas de uma conta SHALL ser o que alimenta o aviso de atividade correspondente.

#### Scenario: Mensagens da outra parte marcadas como lidas
- **WHEN** o cliente dono marca como lida a conversa de uma solicitação em que o prestador escreveu duas mensagens
- **THEN** as duas mensagens do prestador passam a constar como lidas, com o momento da leitura registrado

#### Scenario: Marcação não alcança as próprias mensagens
- **WHEN** uma das partes marca a conversa como lida
- **THEN** as mensagens que ela mesma escreveu permanecem com o estado de leitura que tinham

#### Scenario: Conversa sem mensagem da outra parte
- **WHEN** uma das partes marca como lida uma conversa em que a outra não escreveu nada
- **THEN** a operação é bem-sucedida e nenhuma mensagem é alterada

### Requirement: Conversa acompanha a solicitação

A conversa SHALL permanecer legível pelas duas partes enquanto a solicitação existir, inclusive depois de ela ser concluída, avaliada, recusada ou cancelada, para que o combinado durante o atendimento continue verificável.

Novas mensagens SHALL ser aceitas apenas enquanto a solicitação não estiver encerrada. Solicitação em `RECUSADO`, `CANCELADO` ou `AVALIADO` SHALL recusar envio com 409 e código `ESTADO_INVALIDO`.

#### Scenario: Conversa legível após a conclusão
- **WHEN** uma das partes consulta a conversa de uma solicitação já avaliada
- **THEN** todas as mensagens trocadas continuam sendo devolvidas

#### Scenario: Envio em solicitação encerrada
- **WHEN** uma das partes tenta enviar mensagem em uma solicitação recusada, cancelada ou já avaliada
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e nenhuma mensagem é registrada
