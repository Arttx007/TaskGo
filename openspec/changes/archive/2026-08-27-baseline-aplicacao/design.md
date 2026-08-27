## Context

Ver `proposal.md` (seção Why) para a motivação. O que importa aqui é a restrição de método: esta change não projeta software novo — ela extrai, do que já existe, uma descrição de comportamento que passará a servir de referência para todas as changes seguintes.

Estado atual relevante:

- `openspec/specs/` está vazio. Não há nenhum baseline anterior, então todas as capabilities entram como `ADDED`, e não há delta a reconciliar.
- Existem três documentos de produto na raiz (`spec.md`, `plan.md`, `tasks.md`) que descrevem o MVP **pretendido**. Eles não são a mesma coisa que o comportamento implementado: `plan.md` é anterior à implementação e `spec.md` é um PRD.
- A cobertura de teste é desigual: o ciclo principal (US-01..US-10) tem teste de integração ponta a ponta, as regras de taxa/custódia/saque têm testes unitários, e boa parte das rotas administrativas e todo o frontend não têm teste automatizado.
- O frontend é estático, sem build nem suíte de teste, e consome a API por uma única camada de acesso.

## Goals / Non-Goals

**Goals:**

- Produzir um baseline **falsificável**: cada cenário deve poder virar um teste ou uma verificação manual, para que uma regressão futura seja detectável.
- Registrar o comportamento como ele é, incluindo o que é simulado e o que é inconsistente.
- Deixar rastreável de onde veio cada afirmação (teste, código, ou observação de interface), para que a confiança em cada parte do baseline seja avaliável.

**Non-Goals:**

- Não normalizar nem "arrumar" o comportamento na escrita da spec. Uma spec que descreve o sistema idealizado não serve de baseline de regressão.
- Não descrever estrutura interna (classes, pacotes, camadas). Isso já vive nos `CLAUDE.md` e em `plan.md`.
- Não produzir código, teste ou migration nesta change.

## Decisions

### O baseline entra como change, não escrevendo direto em `openspec/specs/`

Escrever direto no diretório de specs principais pularia o fluxo do OpenSpec e deixaria o baseline sem proposta, sem revisão e sem histórico. Passando por uma change, o conteúdo é validado (`openspec validate --strict`), revisado e só então promovido por sync ou archive.

*Alternativa considerada:* popular `openspec/specs/` manualmente. Rejeitada: mais rápido, mas o baseline chegaria sem o registro do que foi assumido e sem passar por validação.

### Dez capabilities por domínio, e não uma capability única

Cada capability corresponde a uma área de comportamento que muda por motivos independentes: verificação de prestador muda por regra de compliance, custódia muda por regra financeira, busca muda por produto. Separadas, uma change futura toca só o arquivo afetado e o delta fica legível.

*Alternativa considerada:* um único `taskgo-marketplace/spec.md`. Rejeitada: qualquer alteração futura viraria um delta sobre um arquivo grande, dificultando ver o que mudou de fato.

### Hierarquia de evidência: teste, depois código, depois documento

Onde um teste afirma um comportamento, ele é a fonte — é executável e está verde. Onde não há teste, a fonte é o código. `spec.md` e `plan.md` **não** são fonte para este baseline: descrevem intenção, e divergência entre intenção e implementação é exatamente o que um baseline precisa expor, não esconder.

Consequência prática: onde o código contradiz `spec.md`, o baseline segue o código e a divergência é registrada como comportamento atual.

### Comportamento simulado é comportamento, e entra na spec

Gateway de pagamento e repasse Pix são simulados; parte do painel do cliente é decorativa e não chama a API. Isso é observável por quem usa o sistema, então está nas specs, marcado como simulado. Omitir criaria um baseline que descreve um produto que não existe.

*Trade-off aceito:* uma spec que diz "a cobrança é sempre aprovada" parece endossar o mock. Mitigado deixando explícito, na própria requisição, que a custódia é real e a cobrança não.

### Inconsistências são descritas, não corrigidas

`POST /clientes` responde 200 e `POST /prestadores` responde 201; o motivo de rejeição de KYC é recebido e descartado; `GET /admin/dashboard` devolve texto e não JSON; `/dashboard`, `/relatorio`, `/clientes`, `/localizacoes` e `/tempos` são acessíveis a qualquer conta autenticada. Tudo isso entra como comportamento atual.

O pedido desta etapa foi explícito em não sugerir melhorias, e há uma razão além disso: se o baseline já descrevesse o comportamento corrigido, a correção futura passaria despercebida na validação — o delta apareceria como "sem mudança".

### Formato `WHEN/THEN` com conteúdo em português

A convenção do projeto pede critérios de aceite no estilo Gherkin de `spec.md` (Dado/Quando/Então), mas o validador do OpenSpec exige os marcadores `**WHEN**`/`**THEN**` em cenários com exatamente quatro cerquilhas. A restrição da ferramenta prevalece na estrutura; a convenção do projeto é honrada no idioma e na granularidade dos cenários.

### O frontend entra dentro das capabilities de domínio

Comportamento de interface foi distribuído nas capabilities a que pertence (guarda de sessão em `autenticacao`, aviso de KYC em `cadastro-prestador-kyc`), em vez de virar uma capability `frontend` separada. Uma capability descreve um comportamento do produto, não uma camada técnica — e a mesma regra costuma ter lado servidor e lado interface.

## Cobertura de teste do baseline

Levantado na aplicação desta change, com a suíte verde (`mvn test`: 32 testes, 0 falhas). Esta seção fica na change e **não** é promovida junto com as specs: ela envelhece a cada teste novo, enquanto a spec descreve comportamento.

**Confirmado por teste automatizado**

- `solicitacao-servico`: abertura com estado `SOLICITADO` e valor herdado do serviço; recusa de duplicidade com o mesmo prestador; recusa de serviço inativo; aceite pelo dono; aceite por não dono (403 `ACESSO_NEGADO`); conclusão exigindo pagamento retido; conclusão tentada pelo cliente; avaliação de concluída com recálculo de nota média; avaliação repetida e fora de hora; cancelamento de encerrada; cancelamento por quem não participa; cancelamento a partir de `ACEITO` disparando estorno; transição inválida a partir de estado que não a permite.
- `pagamento-custodia`: as duas faixas de RN01, incluindo o **valor exatamente igual ao limiar**, que o teste distingue de propósito usando taxa fixa diferente do percentual no limiar; retenção em custódia com taxa e líquido; recusa sem persistir pagamento; liberação creditando o líquido; liberação recusada sem pagamento retido; estorno sem debitar o prestador.
- `carteira-saque`: saque do valor exato do saldo; saque parcial abatendo o saldo; recusa acima do saldo sem debitar nem registrar; consulta do próprio saldo após conclusão.
- `autenticacao`: emissão de token para os três tipos de conta; autorização cruzada entre prestadores devolvendo 403 com o código estável no campo `error`.

**Confirmado por verificação manual contra a API em execução** (grupo 2 de `tasks.md`)

- `autenticacao`: rota protegida sem token, com token inválido e com papel insuficiente, todas respondendo 403 com corpo vazio; credenciais inválidas (401 `CREDENCIAIS_INVALIDAS`); tipo de usuário ausente (400); recusa por posse de recurso trazendo o corpo padrão.
- `cadastro-cliente`: `POST /clientes` respondendo 200, contra 201 de `POST /prestadores`.
- `cadastro-prestador-kyc`: recusa de formato não aceito (400 `ARQUIVO_INVALIDO`); recusa por tamanho acima do limite (500 `ERRO_INTERNO`); bloqueio de publicação sem verificação aprovada (422 `KYC_PENDENTE`); reenvio após rejeição voltando a `PENDENTE`.
- `administracao`: fila de pendentes; consulta de documento (200 `image/png`) e tipo desconhecido (404); aprovação e rejeição; motivo da rejeição não persistido; prestador inexistente (404); parâmetro alterado passando a valer sem reinício; parâmetro inexistente (404) e valor ausente (400).
- `relatorios-analiticos`: acesso de conta não administradora a `/dashboard`, `/relatorio`, `/clientes`, `/localizacoes` e `/tempos` (200) contra `/admin/**` (403); resposta em texto de `/admin/dashboard`; campo `agendados` presente em `/relatorio`.

**Derivado apenas de leitura de código — sem teste nem verificação manual**

- `solicitacao-servico`: recusa pelo prestador (`RECUSADO`); consulta das próprias solicitações; cancelamento a partir de `SOLICITADO` no caminho de sucesso; serviço ofertado inexistente; indisponibilidade pelo ramo "prestador não aprovado" (só o ramo "serviço inativo" tem teste); nota fora da faixa de 1 a 5.
- `pagamento-custodia`: pagamento repetido; pagamento de solicitação não aceita; pagamento por quem não é o cliente; parâmetro de taxa ausente; cancelamento sem pagamento registrado.
- `carteira-saque`: valor de saque não positivo; saque e consulta sobre cadastro alheio; saldo zero de prestador recém-cadastrado; marcação do saque como processado.
- `catalogo-servicos`: sem teste de comportamento próprio nem verificação manual das operações de edição, ativação e exclusão.

**Confirmado por verificação manual em navegador** (grupo 3 de `tasks.md`)

- `autenticacao`: sessão expirada descartada do armazenamento e redirecionada ao login; usuário já autenticado desviado do login para o painel do seu tipo; papel divergente terminando no painel próprio, não no login.
- `cadastro-prestador-kyc`: aviso distinto para cadastro em análise e cadastro rejeitado, ambos bloqueando a publicação de especialidade.
- `busca-servicos` e `solicitacao-servico`: busca por categoria e cidade devolvendo o serviço na interface; solicitação criada em `SOLICITADO`; pagamento pela interface deixando a custódia `RETIDO`; avaliação registrando nota e comentário e recalculando a nota média; inversão do seletor de estrelas; uso de diálogos nativos na confirmação da solicitação.
- `pagamento-custodia`: cliente cobrado pelo valor cheio com a taxa descontada do prestador, e crédito do líquido no saldo na conclusão, medido com a taxa vigente.
- Áreas decorativas: checkout de demonstração, chat, recibo e simulador acionados sem produzir nenhuma chamada de rede.

## Risks / Trade-offs

- **Um bug vira requisito.** Descrever comportamento atual como `SHALL` pode ser lido como decisão de mantê-lo. → Mitigação: as inconsistências estão nomeadas como tal no `proposal.md` e no cenário correspondente, e cada correção futura será uma change com delta `MODIFIED` explícito.
- **Parte do baseline não é verificada por teste.** Rotas administrativas, dimensões e todo o frontend foram derivados de leitura de código. → Mitigação: `tasks.md` separa o que foi confirmado por teste do que precisa de conferência manual, e prioriza a conferência do segundo grupo.
- **O baseline envelhece em silêncio.** Nada impede que o código mude sem a spec correspondente ser atualizada. → Mitigação: depois do archive, `openspec/specs/` passa a ser alvo de delta; mudanças de comportamento sem delta ficam visíveis na revisão.
- **Granularidade pode não sobreviver ao uso.** Dez capabilities podem se mostrar demais ou de menos quando as primeiras changes reais chegarem. → Mitigação: renomear ou fundir capability é barato enquanto não há histórico de deltas acumulado.

## Migration Plan

Não há deploy, schema nem rollback de código envolvidos.

1. Revisar os artefatos desta change.
2. Executar as tarefas de verificação de `tasks.md`, corrigindo qualquer cenário que a conferência mostrar impreciso.
3. Promover o baseline para `openspec/specs/` com `/opsx:sync` ou `/opsx:archive`.

Rollback: descartar o diretório da change antes da promoção. Depois da promoção, qualquer ajuste vira uma change nova com delta `MODIFIED`.
