## Context

Ver `proposal.md` (seção Why) para a motivação, e os deltas em `specs/` para os requisitos. O que este documento registra é *por que* cada item foi para integração real, para construção nova ou para remoção — e por que a decisão óbvia é a errada em vários deles.

Sete restrições do código atual moldam o desenho inteiro:

- **`FatoServico` é ao mesmo tempo tabela-fato do star schema e a solicitação de serviço.** Ela participa de `DashboardController` e `RelatorioController`, que contam por status. Acrescentar um estado não é mudança local: `RelatorioController` conta `ACEITO` como "agendados", e uma solicitação em atendimento deixa de ser contada por qualquer bucket.
- **`fato_servicos` não tem um único timestamp.** A única noção temporal é `dim_tempo`, uma dimensão de *dia* (`data`, `dia`, `mes`, `ano`, `dia_semana`), criada sob demanda em `TempoService`. Ela não sabe a hora, então não sustenta timeline alguma.
- **`Service/FatoServicoService.java` é o dono único da máquina de estados**, através de `exigirStatus`, `buscarEValidarDonoPrestador` e `buscarEValidarDonoCliente`. Todo estado novo passa por lá, e é o arquivo de maior risco da change.
- **`ddl-auto=validate`.** Qualquer campo novo de entidade exige migration, e a aplicação não sobe se entidade e schema divergirem. Mas os testes rodam H2 com Flyway **desligado** e `create-drop`: a migration nunca é exercida por `mvn test`.
- **`dim_cliente` é uma dimensão mínima** — `nome`, `idade`, `cidade`, `tipo_cliente`, `email`, `senha` — e **não tem restrição de unicidade em `email`**, ao contrário de `dim_prestador`, que tem `uk_dim_prestador_email`. `ClienteService.buscarPorId` existe e nenhum controller a usa.
- **`dim_localizacao` não é endereço.** Tem `cidade`, `estado`, `bairro`, `latitude`, `longitude` — sem rua, número ou CEP — e não pertence a cliente algum: é dimensão compartilhada, referenciada por `ServicoOfertado` e `FatoServico`.
- **A busca exige categoria e localidade.** `ServicoOfertadoService.buscar` casa `categoria` por igualdade *ignore-case*, sem `LIKE`, e devolve lista vazia se não houver nem coordenadas nem cidade. Não existe "listar tudo" — restrição que a change anterior já documentou e que vale aqui igual.

E duas do frontend: `painel-cliente.html` tem 1866 linhas com **todo o CSS inline** e todo controle mockado como `onclick=` inline, então cada integração toca HTML e JS juntos; e `SecurityConfig` libera GET/POST/PUT/DELETE/OPTIONS no CORS, **sem PATCH**.

## Goals / Non-Goals

**Goals:**

- Deixar cada elemento do painel do cliente verificável: ou vem da conta autenticada, ou não está lá.
- Concentrar a mudança de schema em **uma** migration, `V8`, para que a validação contra Postgres seja um passo único.
- Manter `FatoServicoService` como dono exclusivo da máquina de estados, sem espalhar validação de transição por controllers.
- Registrar por que a carteira do cliente **não** é construída e por que o estado `EM_ANDAMENTO` **é** — as duas decisões mais contraintuitivas do conjunto.
- Preservar RN01, RN03 e RN04 intactas: nenhum cálculo de taxa, fluxo de custódia ou gate de verificação muda.

**Non-Goals:**

- Separar `FatoServico` em fato analítica e entidade de solicitação. É o refactor estruturalmente correto, e está fora: refactor não compartilha commit com feat, e essa separação sozinha é change própria.
- Extrair uma entidade `Avaliacao`. Continua sendo duas colunas em `fato_servicos`. Nada nesta change depende disso.
- Substituir `dim_localizacao` por `endereco_cliente`. As duas coexistem: a dimensão continua sendo a localização do *serviço ofertado*, e a tabela nova é o endereço do *cliente*. Unificá-las mexeria na busca e no star schema.
- Cachear `parametro_negocio`. `ParametroNegocioService` lê sem cache de propósito, para o admin ajustar sem deploy.
- Padronizar modais, toasts e `confirm()` do painel. É refactor.
- Introduzir camada de mapeamento (MapStruct e afins) para os ~15 DTOs novos. O projeto monta DTO à mão, com fábrica estática quando repete (`PrestadorResponse.de`). Manter.

## Decisions

### O estado `EM_ANDAMENTO` entra, e essa é uma mudança de regra de negócio, não um detalhe de tela

O PIN não é decoração: é o mecanismo pelo qual o cliente confirma que quem chegou é quem foi contratado. Ele só significa algo se existir um momento em que o atendimento **começou** — e é justamente esse momento que a RN02 não tem hoje. Sem `EM_ANDAMENTO`, "iniciar atendimento" seria um campo booleano solto, e a tela de acompanhamento continuaria mostrando uma timeline de três passos onde só dois existem.

A consequência aceita é ampla: `openspec/config.yaml` declara a RN02 na forma antiga e diz que transições fora dela "não existem"; o `CLAUDE.md` da raiz aponta para a mesma regra; `RelatorioController` conta por status; e `FluxoCompletoIntegrationTest` conclui direto de `ACEITO`. Tudo isso é atualizado nesta change, e a atualização das duas fontes de convenção (`config.yaml` e `CLAUDE.md`) é tarefa explícita — não efeito colateral.

*Alternativa considerada e rejeitada:* PIN validado sem estado novo, gravando apenas `iniciado_em`. Custaria uma coluna em vez de um estado, mas deixaria a máquina de estados mentindo: `concluir` continuaria aceitando de `ACEITO`, então um prestador poderia concluir sem nunca ter iniciado, e o PIN seria opcional na prática. Um mecanismo de segurança que se pode contornar não é mecanismo de segurança.

*Alternativa considerada e rejeitada:* remover o PIN junto com o resto do mock. Era a decisão de menor escopo, e foi descartada porque a FAQ da própria página (`painel-cliente.html:1533`) já explica ao cliente como usar o código — a promessa está feita ao usuário, e o mecanismo é barato e genuinamente útil num marketplace de serviço presencial.

### Iniciar exige pagamento retido; pagar continua exigindo `ACEITO`

`concluir` já exigia pagamento em custódia. Movendo a exigência de custódia também para `iniciar`, o prestador não se desloca sem o dinheiro estar retido, e a ordem fica: aceitar → pagar → iniciar com código → concluir. `pagar` permanece exigindo `ACEITO`, então `pagamento-custodia` não muda de requisito — só ganha o extrato.

*Alternativa considerada e rejeitada:* permitir pagar em `EM_ANDAMENTO` também. Daria flexibilidade de "paga quando chegar", mas abriria a porta para o prestador iniciar, o cliente não pagar, e o atendimento travar em `EM_ANDAMENTO` sem caminho de conclusão nem de estorno. A custódia existe para que o dinheiro esteja retido *antes* do serviço.

### O código de confirmação é gerado no aceite e nunca aparece na resposta do prestador

O PIN é gerado por `SecureRandom` em `aceitar` e persistido em `fato_servicos.pin_confirmacao`. O ponto delicado é a serialização: `SolicitacaoResponse` é um `record` único, servindo `GET /servicos/minhas`, `GET /servicos/{id}` e todas as transições, para cliente e prestador. Devolver o PIN nesse record vazaria para o prestador em `/servicos/minhas`.

A decisão é **preencher o campo condicionalmente no service**, a partir do papel de quem consulta: cliente dono recebe o valor, prestador recebe `null`. Fica no service porque é ele que já conhece o papel e faz o ownership check; o controller não decide.

*Alternativa considerada e rejeitada:* dois records, um para cliente e um para prestador. Seria mais difícil de errar — o campo simplesmente não existiria no DTO do prestador — mas duplicaria doze campos e forçaria dois caminhos de montagem em cada uma das oito transições. O risco de vazamento é coberto por teste explícito: um cenário da spec exige que o PIN não apareça na consulta do prestador.

*Alternativa considerada e rejeitada:* PIN derivado (hash do id com segredo) em vez de coluna. Evitaria a migration desse campo, mas tornaria impossível regenerar ou expirar o código depois, e não é economia real — a migration existe de qualquer forma pelos timestamps.

### O cancelamento a partir de `EM_ANDAMENTO` tem de estornar, e hoje não estornaria

Este é o ponto onde o estado novo quase criou um defeito de dinheiro. `FatoServicoService.cancelar` (linhas 174-182) tem esta forma:

```java
if (status == CONCLUIDO || status == AVALIADO) throw new EstadoInvalidoException(...);
if (status == ACEITO) pagamentoService.estornarSeRetido(servico);
servico.setStatus(CANCELADO);
```

A guarda de bloqueio é por lista negra (`CONCLUIDO`, `AVALIADO`), então `EM_ANDAMENTO` passa a ser cancelável **automaticamente**, sem nenhuma alteração. Mas o estorno é por lista branca (`== ACEITO`), então não dispararia. O resultado seria uma solicitação em `CANCELADO` com pagamento eternamente em `RETIDO`: `liberar` exige `RETIDO` vindo de uma conclusão que nunca vem, e `estornarSeRetido` nunca mais é chamado. Dinheiro do cliente preso, sem caminho de saída, e sem erro em lugar algum — o cancelamento responderia 200.

A correção é trocar a lista branca por "estorna sempre que houver retido", que é o que `estornarSeRetido` já faz por si: ele checa o estado do pagamento e não faz nada se não houver retido. A condição de estado é, na verdade, redundante desde o início — ela existia só porque `ACEITO` era o único estado em que podia haver pagamento. Com `EM_ANDAMENTO` isso deixa de ser verdade, e a condição passa de redundante a errada.

O requisito de cancelamento em `solicitacao-servico` já dizia "se houver pagamento retido no momento do cancelamento, ele SHALL ser estornado integralmente" — state-agnostic, e correto. Era o **código** que era mais estreito que a spec. O delta reescreve o requisito para tornar isso explícito e testável, com cenário para o cancelamento durante o atendimento e um cenário que afirma a invariante direta: nenhum pagamento permanece `RETIDO` depois de um cancelamento.

**Consequência para a RN03:** ela é declarada em `config.yaml` como "cancelar a partir de `ACEITO` estorna integral". A regra não muda de intenção, mas muda de redação — o estorno passa a valer para `EM_ANDAMENTO` também. As duas fontes de convenção são atualizadas junto com a RN02.

*Alternativa considerada e rejeitada:* proibir o cancelamento a partir de `EM_ANDAMENTO`, forçando a conclusão. Manteria o código de estorno intocado, mas prenderia o cliente num atendimento que começou e deu errado, e prenderia o prestador num serviço que o cliente não quer — com o dinheiro retido do mesmo jeito. Cancelamento com estorno é a saída correta.

### O estorno não é integral quando o cliente desiste de um atendimento já iniciado

Decisão de produto do dono do projeto: o prestador se deslocou até o local e teve o tempo comprometido, então uma desistência do cliente depois de o atendimento começar não pode devolver tudo. Uma taxa de cancelamento é retida do valor pago e **creditada integralmente ao prestador**.

O ponto importante, e explícito: **a plataforma não fica com nada**. A taxa de serviço da RN01 continua sendo devolvida ao cliente junto com o restante, como já é hoje em qualquer cancelamento. O TaskGo não lucra com desistência; a retenção existe só para compensar quem se deslocou. Isso mantém coerente o incentivo — a plataforma não tem motivo para tornar o cancelamento atraente.

A regra, com os valores escolhidos:

| Chave em `parametro_negocio` | Valor | Papel |
|---|---|---|
| `cancelamento.carencia-minutos` | 2 | janela de arrependimento sem custo, contada de `iniciado_em` |
| `cancelamento.taxa-percentual-perto` | 0,15 | distância dentro do limiar, ou distância desconhecida |
| `cancelamento.taxa-percentual-longe` | 0,20 | distância acima do limiar |
| `cancelamento.limiar-distancia-km` | 10 | separa "perto" de "longe" |
| `cancelamento.taxa-teto` | 50,00 | teto absoluto em reais |

**Por que o percentual varia com a distância.** É a única variável de esforço que a plataforma realmente conhece. Quem dirigiu 30 km perdeu mais tempo e combustível que quem andou 2 km, e `GeoService.distanciaKm` (Haversine, já usado na busca) calcula isso entre as coordenadas do `endereco_cliente` do atendimento e as da `Localizacao` do prestador. Não é preciso nada novo além de chamar o que existe.

**Distância desconhecida aplica o percentual menor.** Endereço sem coordenadas e `dim_localizacao` sem lat/lon são situações reais hoje — as coordenadas do endereço são opcionais, e a solicitação em si pode nascer sem endereço. Numa apuração de dinheiro, a falta de dado tem de favorecer quem está pagando, não quem está recebendo.

**Sobre o teto de R$ 50.** Foi apresentada a consequência de que ele passa a valer já a partir de R$ 250 (a 20%), o que faz a taxa efetiva cair de 20% para 1% num serviço de R$ 5.000, e a recomendação foi R$ 150 — a ordem de grandeza de uma taxa de visita. A decisão do dono do projeto foi R$ 50, favorecendo o cliente, e ela fica registrada como escolha consciente e não como descuido. Como o valor vive em `parametro_negocio`, um administrador o ajusta sem deploy assim que houver dado real de quantos cancelamentos acontecem e de quanto os prestadores reclamam — que é exatamente o caso de uso que a RN01 previu para essa tabela.

**Cancelamento pelo prestador estorna integral.** A taxa compensa quem foi prejudicado pela desistência do outro. Se é o prestador que desiste, ele não é a parte prejudicada, e cobrar do cliente premiaria o abandono.

**A carência de 2 minutos é curta de propósito.** Dois minutos é pouco para um marketplace real, mas mantém o fluxo testável à mão sem esperar meia hora — e, sendo parâmetro, sobe para um número realista sem tocar código.

*Alternativa considerada e rejeitada:* debitar automaticamente aos 2 minutos, com agendador. O resultado observável é idêntico — quem conclui normalmente não paga nada de qualquer forma, e quem cancela depois dos 2 minutos paga nos dois modelos — mas exigiria introduzir `@Scheduled` num projeto que não tem nenhum, mais um job varrendo solicitações em atendimento. A conta feita no momento do cancelamento, comparando `LocalDateTime.now()` com `iniciado_em`, entrega a mesma regra sem infraestrutura nova.

*Alternativa considerada e rejeitada:* taxa fixa em reais, sem percentual. Simples de explicar, mas ou é irrisória num serviço de R$ 500 ou confisca um serviço de R$ 20.

### Estorno parcial é um estado novo de pagamento, e os dois valores ficam persistidos

`StatusPagamento` ganha `ESTORNADO_PARCIAL`, e `Pagamento` ganha `valorEstornado` e `valorTaxaCancelamento`. Guardar os dois é redundante em aritmética — um é `valorBruto` menos o outro — e essa redundância é deliberada: registro financeiro se lê, não se recalcula. O extrato e o comprovante mostram o que aconteceu com o dinheiro sem depender de a regra vigente hoje ser a mesma de quando o cancelamento ocorreu, que é a mesma razão pela qual o extrato devolve `valorTaxa` persistido em vez de recalculado.

`estornarSeRetido` ganha uma variante que recebe a taxa apurada; a versão sem taxa continua existindo para os cancelamentos integrais, e continua idempotente.

**Consequência estrutural:** o crédito no saldo do prestador deixa de ter um único caminho. Hoje só `liberar` credita, na conclusão. Passa a haver um segundo ponto de crédito, no cancelamento parcial. Os dois somam em `prestador.saldoDisponivel`, que tem `@Version` para bloqueio otimista, então concorrência já está coberta — mas é um ponto a mais para auditar quando o saldo divergir.

*Alternativa considerada e rejeitada:* reusar `ESTORNADO` e deduzir a parcialidade de `valorTaxaCancelamento > 0`. Economizaria um valor no enum, mas faria toda leitura de extrato ter de conhecer a regra para saber o que aconteceu, e um `switch` sobre o status deixaria de ser exaustivo em intenção.

### Quatro timestamps em `fato_servicos`, e não uma tabela de eventos

A timeline precisa de hora, e `dim_tempo` é dimensão de dia. As opções eram quatro colunas (`criado_em`, `aceito_em`, `iniciado_em`, `concluido_em`) ou uma tabela `evento_solicitacao` com o histórico de transições.

Quatro colunas, porque os estados são poucos e não se repetem: uma solicitação é aceita uma vez, iniciada uma vez, concluída uma vez. A tabela de eventos seria a modelagem certa se houvesse reabertura, reatribuição ou histórico auditável — nada disso existe, e RN02 é linear. Coluna nula significa "etapa não cumprida", que é exatamente o que a tela precisa saber para não inventar "chegada estimada às 14:35".

*Alternativa considerada e rejeitada:* `evento_solicitacao` como trilha de auditoria. É o desenho que envelhece melhor e vale quando houver disputa ou estorno contestado. Fica registrado como candidato, não antecipado.

### Não se constrói carteira nem cartão do cliente

Este é o item onde construir é pior que remover. Não existe carteira de cliente (`saldoDisponivel` é coluna de `dim_prestador`), não existe entidade de cartão, e o único gateway é `PagamentoGatewayMock`, que aprova tudo a menos que `simularFalha` seja pedido. `Pagamento.metodoPagamento` é `String` livre, e o frontend manda literalmente `'cartao_mock'`.

Criar `Cartao` e `Carteira` produziria uma tela que salva cartão de verdade num banco de verdade e cobra num gateway que não existe — trocaria um mock honesto (que ao menos não persiste nada) por um mock com aparência de sistema financeiro. Saldo de cliente é pior ainda: não há entrada de dinheiro, porque não há PIX de recarga nem PSP; o saldo nasceria sempre zero, e "Adicionar Saldo" continuaria não fazendo nada.

O que sobra de valor real é o **extrato**, e ele já está no banco: `Pagamento` tem `valorBruto`, `valorTaxa`, `valorLiquido`, `status` e `criadoEm`, ligado 1:1 a `FatoServico` por FK única. `GET /clientes/me/pagamentos` é uma leitura por join, sem entidade nova e sem linha de migration. Ele alimenta a tabela de transações e o comprovante — que hoje gera o número com `Math.random()` e **subtrai R$ 4,50 para fabricar o subtotal** (`painel-cliente.js:403-405`), inventando um valor de taxa que o backend calcula de verdade em `TaxaService`.

### O extrato lê `Pagamento`, e a taxa exibida é a que foi apurada

Um detalhe que muda o que a tela diz: RN01 é taxa fixa abaixo do limiar e percentual a partir dele, com valores em `parametro_negocio` ajustáveis sem deploy. A taxa de um pagamento antigo é a vigente **naquele momento**, já persistida em `Pagamento.valorTaxa`. O extrato devolve o valor persistido, nunca recalculado — recalcular mostraria a taxa de hoje num pagamento de março.

### `GET /prestadores/{id}/servicos-ofertados` é autenticado, não público

Um favorito é um prestador, mas uma solicitação é sempre aberta contra um **serviço ofertado**. Sem uma rota que liste os serviços de um prestador, o botão "Contratar" de um favorito não tem `servicoOfertadoId` para chamar — o catálogo hoje só é legível pelo dono (`/servicos-ofertados/meus`) ou pela busca por categoria.

A rota é autenticada, e não `permitAll` como as três públicas que a change anterior criou. Público, ela viraria perfil de prestador por outro nome — enumerável por `id` sequencial, expondo o catálogo de cada autônomo a qualquer visitante. Perfil público é não-objetivo declarado, aqui e na change anterior. Prestador não aprovado devolve **lista vazia, não 403**, mantendo o mesmo comportamento das consultas públicas: RN04 remove da oferta sem revelar o estado de verificação de ninguém.

### As notificações são apuradas do estado, sem tabela

O feed responde a "o que exige minha atenção agora": solicitação aceita e não paga, pagamento retido, serviço concluído sem avaliação, solicitação recusada ou cancelada, mensagem não lida. Tudo isso é consulta sobre `FatoServico`, `Pagamento` e `Mensagem` — dado que já existe.

Uma tabela `notificacao` exigiria produzir a notificação no momento do fato, o que significa eventos de domínio disparados por `FatoServicoService` e `PagamentoService`, mais estado de leitura, mais decidir o que acontece com notificação de solicitação cancelada. É a infraestrutura da US-11, que está fora de escopo.

O custo aceito é que **não há marcação de lido**, e "Marcar todas como lidas" sai da tela. Não é perda: o aviso conta ação pendente, então ele desaparece quando o cliente age — que é o comportamento que se quer de um badge. Uma notificação puramente informativa ("seu pagamento foi confirmado") não caberia nesse modelo, e por isso o feed é definido em termos de pendência, não de histórico.

*Alternativa considerada e rejeitada:* tabela com `lida_em`, populada por evento. Melhor produto, escopo próprio, e traria a decisão de retenção e de canal (US-11) junto.

### "Profissionais em Destaque" sai como conceito, repetindo a decisão da home

A busca exige categoria **e** localidade, senão devolve vazio. Não existe listagem geral, então um bloco de "destaque" só poderia ser preenchido de duas formas: N buscas, uma por categoria conhecida, apresentando resultados arbitrários como destaque — que é US-14, Fase 2 — ou dado inventado, que é o que está lá. A change anterior removeu o carrossel da home por esse motivo exato; a mesma restrição vale dentro do painel, com o agravante de que ali o cliente já confia na plataforma.

A seção passa a ser o grid de resultados da busca. Antes de haver busca, estado vazio dizendo o que falta.

### O campo de busca livre reconcilia contra `/categorias`, sem parâmetro novo no backend

`categoria` é texto livre digitado pelo prestador, casado por igualdade *ignore-case*. Texto digitado pelo cliente quase nunca casa: quem publicou "eletricista predial" não aparece em busca por "eletricista".

A correção certa é busca parcial no servidor, e ela é change própria: exige decidir casamento, ranking, sinônimos e o que fazer com resultado por relevância numa busca hoje ordenada por distância — e afeta as páginas públicas também. Aqui o campo consulta `/categorias` e oferece as categorias que casam com o texto, deixando a escolha explícita. É a mesma reconciliação que a change anterior aplicou ao grid de `servicos.html`.

### Endereço do cliente é entidade própria, e a solicitação aponta para ele opcionalmente

`endereco_cliente` nasce com CEP, rua, número, complemento, bairro, cidade, estado, coordenadas opcionais e o marcador de padrão. Ela não substitui `dim_localizacao`, que continua sendo a localização do serviço ofertado no star schema.

`fato_servicos` ganha FK **anulável** para o endereço, e `SolicitacaoRequest` um campo opcional. Aditivo por decisão: obrigar o endereço quebraria `POST /servicos` para todo consumidor atual, incluindo a página pública de busca, que não tem sessão de cliente com endereços carregados no momento da solicitação.

O ponto delicado é a remoção: um endereço já usado por uma solicitação não pode desaparecer do histórico. `DELETE` é **exclusão lógica** (o endereço sai da lista do cliente, a FK da solicitação continua resolvendo), pela mesma razão que a conta é desativada em vez de apagada.

O marcador de padrão fica **na tabela de endereço**, com a invariante de um único padrão por cliente garantida no service ao gravar. Um campo `endereco_padrao_id` em `dim_cliente` seria mais fácil de manter único, mas criaria dependência circular entre as duas tabelas e complicaria a exclusão lógica.

### A conta é desativada, não apagada, e a desativação é bloqueada com atendimento em curso

`DELETE /clientes/me` marca `ativo = false`, e `AuthService` passa a recusar login de conta inativa. Exclusão física quebraria as FKs de `fato_servicos` e `pagamento` e destruiria histórico financeiro do prestador — as avaliações escritas pela conta, inclusive, continuam contando para a nota média e aparecendo na leitura pública pelo primeiro nome.

Desativar com solicitação em `SOLICITADO`, `ACEITO` ou `EM_ANDAMENTO` responde 409 `ESTADO_INVALIDO`: há atendimento combinado e possivelmente dinheiro em custódia. Deixar sair nesse ponto exigiria decidir se cancela e estorna automaticamente, o que é decisão de produto sobre dinheiro de terceiro.

Anonimização (apagar nome e e-mail mantendo os registros) é o que a LGPD acabará exigindo, e é decisão jurídica antes de técnica. Fica de fora, declarado.

### Unicidade de `email` em `dim_cliente` entra agora, e o banco de desenvolvimento já está sujo

`PUT /clientes/me` permite trocar o e-mail, e sem unicidade duas contas podem terminar com o mesmo. `dim_prestador` já tem `uk_dim_prestador_email` desde `V2`; `dim_cliente` nunca teve.

**A falta dessa restrição já é um defeito no ar, não um risco futuro.** `ClienteRepository.findByEmail` devolve `Optional<Cliente>`, e o Spring Data lança `IncorrectResultSizeDataAccessException` quando a consulta casa mais de uma linha. Como o `GlobalExceptionHandler` mapeia `Exception` genérica para 500 `ERRO_INTERNO`, um e-mail duplicado **torna aquela conta impossível de logar, com erro de servidor** — não com credencial inválida. O login já está quebrado para quem duplicou.

O Postgres de desenvolvimento confirma: 11 clientes, 8 e-mails distintos, e `carlos.browser1@teste.com` em **4 linhas** (ids 1, 4, 5, 6), nenhuma delas com solicitação associada. São contas de teste descartáveis, então a deduplicação é segura — mas ela é intervenção manual e vem **antes** de `V8`, porque escolher qual linha mantém o e-mail não é decisão de migration.

`V8` cria a restrição sem tentar deduplicar sozinha. Uma migration que apaga linha de cliente por conta própria é pior que uma migration que falha: falhar é reversível e visível, apagar não.

*Alternativa considerada e rejeitada:* deixar a unicidade fora do schema e validar só no service. Evitaria o passo manual, mas manteria a porta aberta — corrida entre duas requisições concorrentes ainda duplicaria, e o defeito de login continuaria alcançável. O banco é o único lugar onde unicidade é garantia.

### Uma migration, `V8`, e a validação dela é manual por construção

Tudo entra em `V8__painel_cliente.sql`: as colunas de `dim_cliente`, as três tabelas novas, e as colunas de `fato_servicos`. Uma migration em vez de cinco porque o custo real é a validação contra Postgres, e ela é manual — os testes rodam H2 com Flyway desligado e `create-drop`, então `mvn test` **nunca executa a migration**. Cinco migrations seriam cinco oportunidades de divergir de entidade sem que nenhum teste percebesse.

Padrão do arquivo, seguindo `V1` a `V7`: `BIGINT GENERATED BY DEFAULT AS IDENTITY`, `NUMERIC(19,2)` para dinheiro, índice explícito em cada FK consultada.

### `api.js` continua sendo o único lugar com `fetch`

As ~19 funções novas entram em `api.js` com JSDoc, seguindo o padrão existente: `request(path, {method, body})` com `auth` implícito, e `requestMultipart` para a foto — que já existe, criada para os documentos de KYC. Nenhuma função nova é `auth: false`: todas as rotas desta change são autenticadas.

Duas consequências de arquitetura no frontend, ambas obrigatórias: `painel-cliente.html` passa a carregar `assets/js/validacao.js`, que hoje não inclui e que é o que dá erro por campo (`aplicarErrosDoServidor` casa com os `fieldErrors` que o `GlobalExceptionHandler` já devolve nos 400 `VALIDACAO`); e os controles integrados deixam de ser `onclick=` inline e passam a ter listener registrado no JS, como `criarProfCard` faz em `profissionais.js:321-352` — necessário porque um card de resultado precisa do objeto inteiro em closure, não de strings interpoladas no HTML.

### Consulta periódica no chat, não conexão persistente

O chat busca `GET /servicos/{id}/mensagens` em intervalo enquanto o modal está aberto, e para ao fechar. WebSocket ou SSE mudaria o modelo de deploy do backend e introduziria estado de conexão num serviço declaradamente `STATELESS`. Para uma conversa entre duas pessoas durante um atendimento, latência de alguns segundos é aceitável.

## Risks / Trade-offs

- **`FatoServicoService` concentra o risco da change.** Ele ganha um estado, geração e validação de código, quatro timestamps e o endereço opcional — e é o dono da máquina de estados de todo o produto. → Mitigação: `FatoServicoServiceTest` já existe com Mockito e cobre as transições; cada transição nova e cada guarda ganham cenário. `FluxoCompletoIntegrationTest` é o sinal mais confiável, e a fase de backend não fecha com ele vermelho.
- **`FluxoCompletoIntegrationTest` quebra por desenho.** Ele conclui direto de `ACEITO`, transição que deixa de existir. → Mitigação: é o resultado esperado, não regressão. O teste ganha o passo de iniciar com código, e a tarefa que altera o estado é a mesma que o corrige — não ficam separadas em commits diferentes.
- **A restrição de unicidade de `email` faz `V8` falhar no banco de desenvolvimento como ele está hoje.** Não é hipótese: `carlos.browser1@teste.com` existe em 4 linhas de `dim_cliente` (ids 1, 4, 5, 6). → Mitigação: a deduplicação é tarefa própria, anterior à migration, e as quatro linhas não têm solicitação associada — descartar três é seguro. A mesma consulta precisa ser repetida em qualquer outra base antes de aplicar `V8`.
- **O estado novo quase criou um defeito de dinheiro no cancelamento.** A guarda de bloqueio de `cancelar` é lista negra e a de estorno é lista branca, então `EM_ANDAMENTO` seria cancelável **sem estornar**, deixando o pagamento preso em `RETIDO` para sempre e respondendo 200. → Mitigação: o estorno passa a ser incondicional (`estornarSeRetido` já é idempotente), com cenário de spec afirmando a invariante e teste dedicado. Este é o achado que justifica a fase 8 ir sozinha.
- **A migration não é exercida por nenhum teste.** H2 com Flyway desligado. Uma divergência entre entidade e schema só aparece no boot contra Postgres. → Mitigação: tarefa de verificação que sobe a aplicação contra Postgres limpo e contra Postgres com dado, separada das tarefas de código.
- **`RelatorioController` passa a ver um status que não conhecia.** Hoje conta `agendados` como `ACEITO`; uma solicitação em atendimento não é contada por bucket algum, e o total deixa de fechar com a soma. → Mitigação: o relatório ganha a contagem do estado novo na mesma tarefa que altera o enum. É a única superfície onde o estado novo passa despercebido, porque não há teste sobre esse controller.
- **O PIN vive num record compartilhado por cliente e prestador.** Um caminho de montagem esquecido vaza o código para o prestador, e o mecanismo perde o sentido inteiro. → Mitigação: cenário de spec dedicado, teste que consulta como prestador e afirma campo nulo, e preenchimento condicional em um único ponto do service.
- **O painel encurta visivelmente.** Saem carteira, cartões, 2FA, sessões, assistente, rastreamento ao vivo e quatro cards de profissionais. → Mitigação: os blocos maiores são *substituídos*, não apagados — extrato real no lugar das transações, resultados de busca no lugar dos destaques, endereços reais no lugar dos dois fixos. A perda líquida concentra-se na carteira e nos controles de segurança, que não existiam.
- **`painel-cliente.html` tem 1866 linhas com CSS inline e `onclick=` por toda parte.** Edição extensa em arquivo grande, sem teste automatizado no frontend. → Mitigação: uma aba por commit, em ordem de risco crescente, cada uma independente das outras; reverter é reverter um commit.
- **O cliente pode não ter endereço quando abre a solicitação pela página pública.** Ali não há endereços carregados, então a solicitação nasce sem local. → Mitigação: o campo é opcional por decisão, e o acompanhamento apresenta-se sem local em vez de presumir um. A tela de busca do painel, que tem sessão, oferece a escolha.
- **A consulta periódica do chat gera requisição por intervalo, por conversa aberta.** → Mitigação: só enquanto o modal está aberto, com o timer limpo ao fechar; escala sublinear no número de clientes, porque conversa aberta é evento raro.
- **Dezenove endpoints numa change só.** Superfície grande para revisar. → Mitigação: agrupados por área e por fase, cada fase entregável e verificável sozinha; o frontend só depende do backend da própria área.

## Migration Plan

1. **`V8` e as entidades, juntas e primeiro.** Migration e alteração de entidade são a mesma tarefa: com `ddl-auto=validate`, uma sem a outra impede o boot. Antes de aplicar, rodar a consulta de duplicados de `email` em `dim_cliente`. Nada de comportamento muda neste passo — colunas novas nascem nulas, tabelas novas nascem vazias.
2. **Backend por área, em ordem de risco crescente:** extrato (leitura pura, nenhuma escrita), conta e endereços do cliente, catálogo de um prestador, favoritos, notificações, mensagens, e por último a máquina de estados — código de confirmação, `EM_ANDAMENTO` e timestamps. Todas as áreas antes da última são **aditivas**: implantadas isoladamente, não alteram nada do que está no ar.
3. **A máquina de estados sozinha, e com o teste de integração no mesmo commit.** É o único passo que altera comportamento existente: o prestador passa a precisar iniciar antes de concluir. Implantá-la exige o painel do prestador atualizado junto, senão um atendimento aceito e pago fica sem caminho até a conclusão.
4. **`api.js` e as tags de script.** Sem efeito visível; só disponibiliza as funções e o `validacao.js`.
5. **Uma aba do painel por vez**, cada uma independente: busca e simulador (só endpoints que já existiam), pedidos e acompanhamento, pagamentos, favoritos, configurações, chat, notificações.
6. **Remoções e correções `fix`** — colisão de `id` do cartão, `#notifCountBadge` morto, escape de conteúdo nos chats — em commits separados dos de `feat`, por convenção do projeto.
7. **RN02 nas duas fontes de convenção** (`openspec/config.yaml` e `CLAUDE.md` da raiz), mais `CHANGELOG.md` e o diagrama Mermaid.

**Rollback:** os passos 1 e 2 são aditivos e não precisam de rollback — colunas nulas e tabelas vazias são inertes. O passo 3 é o único com rollback de verdade: reverter o commit da máquina de estados devolve `concluir` a partir de `ACEITO`, e solicitações que estiverem em `EM_ANDAMENTO` ficam num estado que o código revertido não conhece — então o rollback exige, além do revert, levar essas solicitações de volta a `ACEITO` por `UPDATE`. Os passos 4 a 6 revertem por commit de página. `V8` não é revertida: as colunas ficam, ociosas.

## Open Questions

- **O tamanho máximo de uma mensagem de chat.** A spec exige que exista limite e que conteúdo acima dele responda 400 `VALIDACAO`; o número concreto (a proposta parte de 2000 caracteres) pode ser ajustado na implementação sem mexer em spec, approach ou tarefas.
- **O intervalo da consulta periódica do chat.** Alguns segundos; o valor exato é ajuste de frontend, sem efeito de contrato.
- **Se o código de confirmação deve expirar.** Hoje ele vale enquanto a solicitação estiver em `ACEITO`. Expirar exigiria decidir o prazo e o que acontece depois — e `parametro_negocio` tem `solicitacao.prazo-resposta-horas` semeado desde `V7` e **nunca lido por código algum**, então há um parâmetro esperando um dono. Não bloqueia: o comportamento sem expiração é o que as specs descrevem.
- **Se o extrato deve incluir pagamento recusado** (`RECUSADO`, que `PagamentoGatewayMock` só produz sob `simularFalha`). As specs descrevem retido, liberado e estornado. Incluir recusado é aditivo e não muda approach nem tarefas.
- **Se a foto de perfil precisa de miniatura.** `FileStorageService` guarda o arquivo como veio. Redimensionar é otimização, decidível depois sem tocar contrato.
