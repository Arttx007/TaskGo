## Purpose

Cobre como um cliente guarda prestadores com quem quer voltar a contratar: marcar como favorito, consultar os próprios favoritos, removê-los e chegar do favorito até a contratação de um serviço daquele prestador.

## ADDED Requirements

### Requirement: Consulta dos próprios favoritos

O sistema SHALL devolver em `GET /clientes/me/favoritos` os prestadores marcados como favoritos pelo cliente autenticado, dos mais recentemente marcados para os mais antigos. Cada favorito SHALL trazer o identificador do prestador, o nome, a especialidade, a cidade, a nota média quando houver e a quantidade de serviços ativos que ele oferece.

A consulta SHALL devolver apenas favoritos da conta autenticada. Prestador que deixou de estar com verificação aprovada SHALL continuar aparecendo, sinalizado como indisponível para contratação, para que o cliente entenda por que não consegue contratá-lo em vez de vê-lo desaparecer sem explicação.

#### Scenario: Cliente consulta seus favoritos
- **WHEN** um cliente autenticado que marcou dois prestadores consulta seus favoritos
- **THEN** recebe os dois prestadores, cada um com nome, especialidade e nota média registrada, do mais recente para o mais antigo

#### Scenario: Nenhum favorito marcado
- **WHEN** um cliente que não marcou prestador algum consulta seus favoritos
- **THEN** a resposta é bem-sucedida com lista vazia

#### Scenario: Favoritos de outra conta não aparecem
- **WHEN** um cliente autenticado consulta seus favoritos
- **THEN** nenhum favorito marcado por outra conta aparece na resposta

#### Scenario: Favorito cuja verificação deixou de estar aprovada
- **WHEN** um prestador favoritado passa a não estar com verificação aprovada e o cliente consulta seus favoritos
- **THEN** ele continua na lista, sinalizado como indisponível para contratação

#### Scenario: Consulta sem autenticação
- **WHEN** os favoritos são consultados sem token válido
- **THEN** a requisição é recusada e nenhum favorito é devolvido

### Requirement: Marcação de prestador como favorito

Um cliente autenticado SHALL marcar um prestador como favorito via `POST /clientes/me/favoritos`, informando o prestador. A marcação SHALL registrar o momento em que foi feita.

Um mesmo prestador SHALL poder ser favorito de um cliente uma única vez: marcar de novo um prestador já favoritado SHALL ser recusado, sem criar registro duplicado. Marcar prestador inexistente SHALL ser recusado.

#### Scenario: Prestador marcado como favorito
- **WHEN** um cliente autenticado marca um prestador que ainda não é seu favorito
- **THEN** a resposta é 201, e aquele prestador passa a aparecer entre os favoritos do cliente

#### Scenario: Prestador já favoritado
- **WHEN** o cliente marca como favorito um prestador que já é seu favorito
- **THEN** a resposta é 409 com código `ESTADO_INVALIDO` e nenhum registro novo é criado

#### Scenario: Prestador inexistente
- **WHEN** a marcação referencia um prestador que não existe
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

#### Scenario: Marcação sem autenticação
- **WHEN** a marcação é feita sem token válido
- **THEN** a requisição é recusada e nenhum favorito é registrado

### Requirement: Remoção de favorito

Um cliente autenticado SHALL remover um favorito via `DELETE /clientes/me/favoritos/{prestadorId}`. A remoção SHALL alcançar apenas favoritos da própria conta, e MUST NOT afetar o prestador nem as solicitações já abertas com ele.

Remover um prestador que não está entre os favoritos da conta SHALL ser recusado como recurso não encontrado.

#### Scenario: Favorito removido
- **WHEN** o cliente remove um prestador que está entre seus favoritos
- **THEN** aquele prestador deixa de aparecer entre seus favoritos, também em consulta posterior

#### Scenario: Remoção não afeta o histórico
- **WHEN** o cliente remove um favorito com quem já teve serviço concluído
- **THEN** as solicitações e avaliações daquele serviço permanecem inalteradas

#### Scenario: Prestador que não é favorito da conta
- **WHEN** o cliente tenta remover um prestador que não está entre seus favoritos
- **THEN** a resposta é 404 com código `RECURSO_NAO_ENCONTRADO`

### Requirement: Contratação a partir de um favorito

A partir de um favorito, o cliente SHALL alcançar os serviços ativos daquele prestador e abrir uma solicitação para o serviço escolhido. O favorito por si MUST NOT oferecer contratação direta sem que um serviço tenha sido escolhido, porque uma solicitação é sempre aberta contra um serviço ofertado, não contra um prestador.

#### Scenario: Cliente contrata a partir de um favorito
- **WHEN** o cliente aciona a contratação em um favorito e escolhe um dos serviços ativos daquele prestador
- **THEN** uma solicitação real é aberta para o serviço escolhido

#### Scenario: Favorito sem serviço ativo
- **WHEN** o cliente aciona a contratação em um favorito que não tem serviço ativo algum
- **THEN** o painel informa que aquele prestador não tem serviço disponível e nenhuma solicitação é aberta
