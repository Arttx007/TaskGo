# TaskGo — PRD e Backlog de User Stories

> Documento gerado pelo processo de Product Ownership do TaskGo. Consolida a demanda inicial em um PRD executável, com decisões de negócio explicitadas, regras centrais e user stories priorizadas por fase (Fase 1 = MVP).

## 1. Visão Geral do Produto

**Problema:** prestadores de serviço autônomos (eletricistas, diaristas, manicures, técnicos, etc.) hoje acumulam, além do próprio ofício, o trabalho de se divulgar, captar clientes e negociar orçamento — trabalho que consome tempo e não é o que geram receita diretamente.

**Solução:** o TaskGo é um marketplace ("Uber de serviços") que assume a divulgação, a captação de clientes por geolocalização e as estratégias de atração. O prestador só precisa se cadastrar, cadastrar os serviços que oferece e o preço que quer cobrar — a plataforma cuida do resto: descoberta, contratação, pagamento e repasse.

**Personas:**
- **Prestador autônomo** — oferece um ou mais serviços, define preço, quer receber rápido e sem burocracia.
- **Cliente contratante** — busca um profissional confiável perto de si, quer contratar e pagar sem sair do app.
- **Administrador TaskGo** — opera a plataforma: aprova cadastro/KYC de prestadores, monitora disputas e a saúde financeira do marketplace.

**Proposta de valor:** o prestador não precisa negociar orçamento nem gerenciar divulgação; o cliente encontra e contrata um profissional avaliado perto de si em minutos; o TaskGo monetiza via taxa de serviço sobre cada atendimento.

## 2. Objetivos e Métricas de Sucesso

| Fase | Objetivo principal | Métricas |
|---|---|---|
| Fase 1 (MVP) | Provar que cliente e prestador completam um ciclo de contratação → pagamento → saque sem intervenção manual | Nº de prestadores verificados ativos; nº de solicitações concluídas; taxa de conversão solicitação → conclusão; tempo médio de aceite |
| Fase 2 | Reter usuários e reduzir atrito operacional | Retenção de prestadores (30/60/90 dias); NPS; nº de avaliações; tempo médio de resposta via chat |
| Fase 3 | Escalar receita e sofisticar monetização | GMV; receita de taxas; nº de disputas por 100 serviços; adoção de plano premium |

## 3. Decisões de Negócio Assumidas

As decisões abaixo foram validadas com o stakeholder do produto para destravar o MVP. Onde marcado como **parâmetro de negócio**, o valor numérico é um ponto de partida e deve poder ser ajustado por um admin sem deploy de código.

1. **Regra de taxa (RN01):** taxa fixa para serviços de baixo valor, percentual acima de um limiar — não "a maior entre os dois". *(parâmetro de negócio: limiar e valores abaixo)*
2. **Fluxo de contratação:** solicitação do cliente com aceite/recusa explícito do prestador antes de confirmar — não é agendamento instantâneo. Reduz risco de conflito de agenda e é mais realista para serviços presenciais.
3. **Verificação do prestador:** KYC documental obrigatório (documento de identidade + comprovante de conta bancária/Pix) validado antes de o prestador poder publicar serviços — prioriza segurança/confiança sobre velocidade de onboarding.
4. **Custódia de pagamento:** o cliente paga dentro do app (cartão/Pix); o valor líquido (menos taxa) é creditado em um saldo interno do prestador, sacável via Pix sob demanda. O TaskGo nunca depende de o pagamento ser "combinado por fora".

## 4. Regras de Negócio Centrais

**RN01 — Cálculo da taxa de serviço** *(parâmetro de negócio, valores de referência)*
- Serviços com valor **< R$ 50,00**: taxa fixa de **R$ 5,00**.
- Serviços com valor **≥ R$ 50,00**: taxa de **10%** sobre o valor do serviço.
- A taxa é sempre descontada do valor recebido pelo prestador; o cliente paga o valor cheio anunciado.

**RN02 — Ciclo de vida da solicitação de serviço**
`SOLICITADO → ACEITO → CONCLUÍDO → AVALIADO`
`SOLICITADO → RECUSADO` (pelo prestador)
`SOLICITADO → CANCELADO` (pelo cliente, antes do aceite)
`ACEITO → CANCELADO` (por qualquer parte, sujeito a política de cancelamento — ver US-10)
Transições fora dessas não são permitidas (ex.: não é possível avaliar um serviço que não foi concluído).

**RN03 — Custódia e saque**
- O pagamento do cliente fica retido pelo TaskGo até a conclusão do serviço.
- Na conclusão, o valor líquido (valor do serviço − taxa) é creditado no saldo interno do prestador.
- O prestador pode solicitar saque via Pix a qualquer momento, limitado ao saldo disponível.

**RN04 — KYC obrigatório**
- Prestador não pode publicar nenhum serviço enquanto o cadastro estiver `PENDENTE` ou `REJEITADO`.
- Apenas prestadores com status `APROVADO` aparecem em buscas de clientes.

## 5. Fora de Escopo (Fase 1 / MVP)

- Chat/mensagens dentro do app entre cliente e prestador.
- Precificação dinâmica ou sugestão automática de preço.
- Split de pagamento entre múltiplos prestadores em um mesmo atendimento.
- Disputas formais com mediação humana (apenas cancelamento simples).
- App mobile nativo (MVP web-first).
- Nota fiscal / relatórios fiscais para o prestador.
- Programa de indicação / cupons.

## 6. Roadmap por Fases

### Fase 1 — MVP (núcleo transacional)
| # | User Story | Prioridade |
|---|---|---|
| US-01 | Cadastro e verificação (KYC) do prestador | P0 |
| US-02 | Cadastro de serviço ofertado (categoria, descrição, preço) | P0 |
| US-03 | Busca de prestadores por geolocalização | P0 |
| US-04 | Solicitação de serviço pelo cliente | P0 |
| US-05 | Aceite/recusa da solicitação pelo prestador | P0 |
| US-06 | Pagamento do serviço com cálculo automático da taxa | P0 |
| US-07 | Conclusão do serviço e liberação de saldo ao prestador | P0 |
| US-08 | Solicitação de saque via Pix pelo prestador | P0 |
| US-09 | Avaliação do cliente pós-conclusão | P1 |
| US-10 | Cancelamento da solicitação | P1 |

### Fase 2 — Crescimento e retenção
| # | User Story | Prioridade |
|---|---|---|
| US-11 | Notificações (push/e-mail) de novo pedido, aceite, pagamento e avaliação | P1 |
| US-12 | Extrato financeiro do prestador (histórico de recebimentos e saques) | P1 |
| US-13 | Chat dentro do app entre cliente e prestador durante um atendimento ativo | P2 |
| US-14 | Destaque de prestadores bem avaliados nos resultados de busca | P2 |
| US-15 | Reagendamento de um serviço já aceito | P2 |
| US-16 | Portfólio de fotos do prestador no perfil de serviço | P2 |

### Fase 3 — Escala e monetização avançada
| # | User Story | Prioridade |
|---|---|---|
| US-17 | Abertura de disputa/reembolso com mediação do TaskGo | P1 |
| US-18 | Plano premium do prestador (destaque pago nos resultados) | P2 |
| US-19 | Split de pagamento para atendimentos com equipe (múltiplos prestadores) | P3 |
| US-20 | Relatórios financeiros/fiscais para o prestador | P3 |
| US-21 | App mobile nativo (iOS/Android) | P3 |

## 7. Especificação Detalhada — User Stories do MVP

### US-01 — Cadastro e verificação (KYC) do prestador
**Prioridade:** P0

**Como** prestador autônomo,
**Desejo** me cadastrar na plataforma e enviar meus documentos de verificação,
**Para que** eu possa ser aprovado e publicar meus serviços com segurança para os clientes.

```gherkin
Cenário: Caminho feliz - cadastro aprovado
  Dado que sou um novo prestador sem cadastro no TaskGo
  Quando eu preencho meus dados pessoais e envio documento de identidade e comprovante de conta Pix
  Então meu cadastro é criado com status "PENDENTE"
  E recebo uma notificação assim que o status muda para "APROVADO"

Cenário: Caso extremo - documento em formato ou tamanho inválido
  Dado que estou no formulário de envio de documentos
  Quando eu anexo um arquivo que não é imagem/PDF ou que excede o tamanho máximo permitido
  Então o sistema rejeita o upload
  E exibe a mensagem informando o formato e tamanho aceitos
  E meu cadastro permanece sem os documentos pendentes de envio

Cenário: Exceção/Segurança - tentativa de publicar serviço sem aprovação
  Dado que meu cadastro está com status "PENDENTE" ou "REJEITADO"
  Quando eu tento criar ou publicar um serviço
  Então o sistema bloqueia a ação
  E retorna mensagem informando que a verificação de cadastro precisa ser concluída antes de publicar serviços
```

### US-02 — Cadastro de serviço ofertado
**Prioridade:** P0

**Como** prestador aprovado,
**Desejo** cadastrar os serviços que ofereço, com categoria, descrição e o preço que quero cobrar,
**Para que** clientes próximos possam encontrar e contratar meu trabalho sem que eu precise negociar orçamento.

```gherkin
Cenário: Caminho feliz - criação de serviço válido
  Dado que sou um prestador com cadastro "APROVADO"
  Quando eu cadastro um serviço com categoria, descrição e preço de R$ 80,00
  Então o serviço é salvo com status "ATIVO"
  E passa a aparecer nas buscas de clientes na minha área de atuação

Cenário: Caso extremo - preço no limite mínimo permitido
  Dado que sou um prestador com cadastro "APROVADO"
  Quando eu cadastro um serviço com o menor preço permitido pela plataforma (ex.: R$ 1,00)
  Então o serviço é salvo normalmente
  E a régua de cálculo de taxa (RN01) é aplicada corretamente sobre esse valor mínimo

Cenário: Exceção/Segurança - preço inválido ou negativo
  Dado que sou um prestador com cadastro "APROVADO"
  Quando eu tento cadastrar um serviço com preço zero, negativo ou não numérico
  Então o sistema rejeita a criação do serviço
  E exibe mensagem de validação indicando que o preço deve ser um valor positivo
```

### US-03 — Busca de prestadores por geolocalização
**Prioridade:** P0

**Como** cliente,
**Desejo** buscar prestadores de um tipo de serviço próximos à minha localização,
**Para que** eu encontre rapidamente um profissional disponível na minha região.

```gherkin
Cenário: Caminho feliz - resultados dentro do raio de busca
  Dado que existem prestadores "APROVADOS" com serviços ativos a até 10 km da minha localização
  Quando eu busco por uma categoria de serviço
  Então vejo a lista de prestadores ordenada por proximidade
  E cada resultado mostra preço, avaliação média e distância

Cenário: Caso extremo - nenhum prestador na região
  Dado que não existe nenhum prestador aprovado da categoria buscada dentro do raio configurado
  Quando eu realizo a busca
  Então o sistema exibe uma mensagem de "nenhum resultado encontrado nesta região"
  E não retorna erro nem lista vazia sem explicação

Cenário: Exceção/Segurança - localização do cliente indisponível
  Dado que o cliente não concedeu permissão de geolocalização ao navegador/app
  Quando eu tento buscar prestadores por proximidade
  Então o sistema solicita a permissão de localização
  E não expõe nenhum dado de localização de prestadores até que a permissão seja concedida ou uma busca manual por endereço seja informada
```

### US-04 — Solicitação de serviço pelo cliente
**Prioridade:** P0

**Como** cliente,
**Desejo** enviar uma solicitação de contratação para um serviço anunciado por um prestador,
**Para que** o prestador possa confirmar que vai realizar o atendimento no preço anunciado.

```gherkin
Cenário: Caminho feliz - solicitação enviada com sucesso
  Dado que estou vendo a página de um serviço ativo de um prestador aprovado
  Quando eu envio uma solicitação de contratação
  Então a solicitação é criada com status "SOLICITADO"
  E o prestador recebe uma notificação da nova solicitação

Cenário: Caso extremo - cliente já tem uma solicitação pendente com o mesmo prestador
  Dado que já tenho uma solicitação "SOLICITADO" ou "ACEITO" em aberto com este prestador
  Quando eu tento enviar uma nova solicitação para o mesmo serviço
  Então o sistema impede a duplicidade
  E me direciona para a solicitação já existente

Cenário: Exceção/Segurança - solicitação para serviço inativo ou de prestador não aprovado
  Dado que um serviço foi desativado pelo prestador ou o prestador perdeu o status "APROVADO" após a página ter sido carregada
  Quando eu tento enviar a solicitação
  Então o sistema rejeita a solicitação
  E informa que o serviço não está mais disponível
```

### US-05 — Aceite/recusa da solicitação pelo prestador
**Prioridade:** P0

**Como** prestador,
**Desejo** aceitar ou recusar uma solicitação recebida,
**Para que** eu só me comprometa com atendimentos que consigo realizar.

```gherkin
Cenário: Caminho feliz - prestador aceita a solicitação
  Dado que tenho uma solicitação com status "SOLICITADO"
  Quando eu aceito a solicitação
  Então o status muda para "ACEITO"
  E o cliente é notificado da confirmação
  E o pagamento do cliente (retido em custódia, RN03) é vinculado a esta solicitação

Cenário: Caso extremo - solicitação expira sem resposta do prestador
  Dado que uma solicitação está com status "SOLICITADO" há mais tempo que o prazo máximo de resposta definido pela plataforma
  Quando o prazo é atingido sem ação do prestador
  Então a solicitação muda automaticamente para "RECUSADO" por expiração
  E o cliente é notificado para buscar outro prestador

Cenário: Exceção/Segurança - tentativa de aceitar solicitação de outro prestador
  Dado que estou autenticado como um prestador diferente do destinatário da solicitação
  Quando eu tento aceitar ou recusar essa solicitação diretamente pela API
  Então o sistema bloqueia a ação por falta de permissão
  E nenhuma alteração de status é persistida
```

### US-06 — Pagamento do serviço com cálculo automático da taxa
**Prioridade:** P0

**Como** cliente,
**Desejo** pagar pelo serviço dentro do app quando o prestador aceita minha solicitação,
**Para que** o valor fique protegido em custódia até o serviço ser concluído.

```gherkin
Cenário: Caminho feliz - pagamento de serviço acima do limiar percentual
  Dado que tenho uma solicitação "ACEITO" para um serviço de R$ 100,00
  Quando eu efetuo o pagamento pelo app
  Então o sistema cobra R$ 100,00 do cliente
  E calcula a taxa de serviço em 10% (R$ 10,00, conforme RN01)
  E retém o pagamento em custódia até a conclusão do serviço

Cenário: Caso extremo - pagamento de serviço abaixo do limiar (taxa fixa)
  Dado que tenho uma solicitação "ACEITO" para um serviço de R$ 30,00
  Quando eu efetuo o pagamento pelo app
  Então o sistema aplica a taxa fixa de R$ 5,00 (conforme RN01), não o percentual
  E o valor líquido a ser creditado ao prestador na conclusão é R$ 25,00

Cenário: Exceção/Segurança - falha na cobrança do meio de pagamento
  Dado que tenho uma solicitação "ACEITO" aguardando pagamento
  Quando o meio de pagamento é recusado pela operadora/PSP
  Então a solicitação permanece em "ACEITO" sem custódia registrada
  E o cliente é informado da falha e pode tentar novamente ou trocar o meio de pagamento
  E o prestador não é notificado de pagamento confirmado
```

### US-07 — Conclusão do serviço e liberação de saldo ao prestador
**Prioridade:** P0

**Como** prestador,
**Desejo** marcar um atendimento como concluído,
**Para que** o valor líquido do serviço seja liberado no meu saldo dentro da plataforma.

```gherkin
Cenário: Caminho feliz - conclusão libera saldo
  Dado que tenho uma solicitação "ACEITO" com pagamento em custódia confirmado
  Quando eu marco o atendimento como concluído
  Então o status da solicitação muda para "CONCLUÍDO"
  E o valor líquido (valor do serviço − taxa, RN01) é creditado no meu saldo interno

Cenário: Caso extremo - tentativa de concluir sem pagamento confirmado
  Dado que tenho uma solicitação "ACEITO" mas o pagamento do cliente ainda não foi confirmado em custódia
  Quando eu tento marcar o atendimento como concluído
  Então o sistema bloqueia a conclusão
  E informa que a conclusão depende da confirmação do pagamento

Cenário: Exceção/Segurança - cliente tenta marcar o próprio serviço como concluído
  Dado que estou autenticado como o cliente de uma solicitação "ACEITO"
  Quando eu tento acionar a conclusão do serviço (ação reservada ao prestador)
  Então o sistema bloqueia a ação por falta de permissão
  E a solicitação permanece com status "ACEITO"
```

### US-08 — Solicitação de saque via Pix pelo prestador
**Prioridade:** P0

**Como** prestador,
**Desejo** solicitar a transferência do meu saldo disponível via Pix quando eu quiser,
**Para que** eu tenha acesso rápido ao dinheiro que ganhei, sem depender de um ciclo fixo de pagamento.

```gherkin
Cenário: Caminho feliz - saque dentro do saldo disponível
  Dado que tenho saldo disponível de R$ 150,00
  Quando eu solicito o saque de R$ 100,00 via Pix
  Então o saque é processado para a chave Pix cadastrada e validada no meu KYC
  E meu saldo disponível passa a ser R$ 50,00

Cenário: Caso extremo - saque do valor total do saldo
  Dado que tenho saldo disponível de R$ 50,00
  Quando eu solicito o saque de exatamente R$ 50,00
  Então o saque é processado normalmente
  E meu saldo disponível passa a ser R$ 0,00

Cenário: Exceção/Segurança - saque acima do saldo disponível
  Dado que tenho saldo disponível de R$ 50,00
  Quando eu tento solicitar um saque de R$ 80,00
  Então o sistema rejeita a solicitação de saque
  E informa que o valor solicitado excede o saldo disponível
  E nenhum débito é realizado no meu saldo
```

### US-09 — Avaliação do cliente pós-conclusão
**Prioridade:** P1

**Como** cliente,
**Desejo** avaliar o prestador depois que o serviço for concluído,
**Para que** eu ajude outros clientes a escolherem prestadores confiáveis e o prestador construa reputação.

```gherkin
Cenário: Caminho feliz - avaliação registrada
  Dado que tenho uma solicitação com status "CONCLUÍDO" ainda não avaliada
  Quando eu envio uma nota de 1 a 5 e um comentário opcional
  Então a avaliação é registrada vinculada a essa solicitação
  E a nota média do prestador é recalculada considerando a nova avaliação

Cenário: Caso extremo - avaliação sem comentário
  Dado que tenho uma solicitação "CONCLUÍDO" ainda não avaliada
  Quando eu envio apenas a nota, sem preencher o comentário
  Então a avaliação é registrada normalmente com o campo de comentário vazio

Cenário: Exceção/Segurança - tentativa de avaliar duas vezes ou serviço não concluído
  Dado que já avaliei esta solicitação anteriormente, ou a solicitação ainda não está "CONCLUÍDO"
  Quando eu tento enviar uma avaliação para essa mesma solicitação
  Então o sistema rejeita a nova avaliação
  E informa que a solicitação já foi avaliada ou ainda não pode ser avaliada
```

### US-10 — Cancelamento da solicitação
**Prioridade:** P1

**Como** cliente ou prestador,
**Desejo** cancelar uma solicitação de serviço,
**Para que** eu não fique preso a um atendimento que não vai mais acontecer.

```gherkin
Cenário: Caminho feliz - cliente cancela antes do aceite
  Dado que enviei uma solicitação com status "SOLICITADO" ainda não respondida
  Quando eu cancelo a solicitação
  Então o status muda para "CANCELADO"
  E nenhuma cobrança é realizada, pois o pagamento ainda não ocorreu nesta etapa

Cenário: Caso extremo - cancelamento após aceite com pagamento já em custódia
  Dado que tenho uma solicitação "ACEITO" com pagamento confirmado em custódia
  Quando o cliente ou o prestador cancela a solicitação
  Então o status muda para "CANCELADO"
  E o valor em custódia é estornado integralmente ao cliente, sem cobrança da taxa de serviço

Cenário: Exceção/Segurança - tentativa de cancelar solicitação já concluída
  Dado que uma solicitação está com status "CONCLUÍDO"
  Quando o cliente ou o prestador tenta cancelá-la
  Então o sistema bloqueia o cancelamento
  E informa que solicitações concluídas não podem ser canceladas
```

## 8. Backlog Resumido — Fase 2 e Fase 3

| # | Como | Desejo | Para que |
|---|---|---|---|
| US-11 | prestador/cliente | receber notificações push/e-mail de novo pedido, aceite, pagamento e avaliação | eu não precise ficar checando o app manualmente |
| US-12 | prestador | ver um extrato financeiro com histórico de recebimentos e saques | eu tenha controle sobre meus ganhos na plataforma |
| US-13 | cliente/prestador | trocar mensagens dentro do app durante um atendimento ativo | possamos alinhar detalhes sem expor contato pessoal antes da confirmação |
| US-14 | cliente | ver prestadores bem avaliados em destaque nos resultados de busca | eu escolha com mais confiança |
| US-15 | prestador/cliente | reagendar um serviço já aceito | imprevistos não obriguem a cancelar e recomeçar o processo |
| US-16 | prestador | adicionar fotos de trabalhos anteriores ao meu perfil de serviço | eu demonstre qualidade do meu trabalho a novos clientes |
| US-17 | cliente/prestador | abrir uma disputa sobre um atendimento concluído ou cancelado | o TaskGo possa mediar reembolsos ou penalidades quando algo dá errado |
| US-18 | prestador | assinar um plano premium com destaque pago nos resultados | eu aumente minha visibilidade e capte mais clientes |
| US-19 | cliente | contratar um atendimento realizado por uma equipe de prestadores | eu resolva serviços que exigem mais de um profissional |
| US-20 | prestador | gerar relatórios financeiros/fiscais dos meus atendimentos | eu use essas informações para declarar meus ganhos |
| US-21 | cliente/prestador | usar um app mobile nativo | eu tenha uma experiência mais fluida que a versão web |

## 9. Riscos e Questões em Aberto

- **Compliance de KYC/LGPD:** armazenamento de documentos de identidade exige política de retenção e segurança de dados a ser validada com jurídico antes do lançamento.
- **Gateway de pagamento/Pix:** a escolha do provedor de pagamento (PSP) que suporte custódia + repasse via Pix é uma decisão técnica que impacta prazo e custo do MVP; não coberta por este PRD.
- **Definição final dos parâmetros de RN01** (limiar de R$ 50 e valores de R$ 5 / 10%) deve ser validada com a área financeira antes do lançamento — os valores aqui são referência para viabilizar o desenvolvimento do MVP.
- **Prazo máximo de resposta do prestador** (US-05, cenário de expiração) precisa de definição de negócio (ex.: 30 min, 2h, 24h) — não especificado no pedido original.
- **Área de cobertura da busca geográfica** (US-03, raio de 10 km usado como exemplo) precisa de validação por categoria de serviço e densidade urbana.
