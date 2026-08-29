# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Este repositório contém dois projetos do TaskGo (marketplace de serviços tipo "Uber de serviços", conectando clientes a prestadores):

- `backend-estrela-main/` — Spring Boot 4 (Java 17) REST API, pacote `com.example.Estrela`. Ver `backend-estrela-main/CLAUDE.md`.
- `ProjetoTaskGoFinalizado-main/` — frontend estático HTML/CSS/JS (markup gerado pelo TeleportHQ), sem build tooling. Ver `ProjetoTaskGoFinalizado-main/CLAUDE.md`.

Não há build system compartilhado, nem `package.json` na raiz, nem scripts raiz — cada subprojeto é desenvolvido e executado independentemente. Sempre `cd` para a subpasta relevante antes de rodar comandos (comandos Maven devem rodar de `backend-estrela-main/`).

O projeto já está funcional/completo. Mudanças a partir de agora são refactors, correções e novas features pontuais — não recriação.

## Documentos de produto (raiz)

`spec.md` (PRD: user stories US-01..US-21 com critérios de aceite em Gherkin e as regras de negócio RN01-RN04), `plan.md` (plano técnico — registra *por que* cada decisão de arquitetura foi tomada: JWT, Flyway, Haversine em memória, escrow mockado) e `tasks.md` (checklist de execução, com o que já foi feito marcado).

O MVP (US-01..US-10) está implementado ponta a ponta; Fase 2/3 (US-11..US-21) está fora do escopo implementado. Javadoc e comentários no código referenciam essas regras por código (`RN01`, `US-07`) — antes de alterar comportamento de taxa, custódia de pagamento, KYC ou busca por raio, leia a regra correspondente em `spec.md`.

Esses três documentos são o PRD **original** e não são reescritos a cada mudança: eles descrevem o MVP como foi planejado. O que o sistema faz *hoje* está em `openspec/specs/` (ver abaixo) — quando os dois divergirem, `openspec/specs/` é a descrição atual e `spec.md` é a intenção original.

## OpenSpec: como o trabalho anda neste repositório

Mudanças passam pelo OpenSpec (CLI `openspec`, schema `spec-driven`), com os skills `/opsx:propose`, `/opsx:apply`, `/opsx:archive`, `/opsx:sync`, `/opsx:update` e `/opsx:explore`.

- `openspec/specs/<capability>/spec.md` — **as capabilities são a fonte da verdade do comportamento atual**, em requisitos SHALL/MUST com cenários Gherkin (`#### Scenario:` com exatamente 4 hashtags). Hoje: `administracao`, `autenticacao`, `busca-servicos`, `cadastro-cliente`, `cadastro-prestador-kyc`, `carteira-saque`, `catalogo-servicos`, `pagamento-custodia`, `relatorios-analiticos`, `solicitacao-servico`, `vitrine-publica`. Leia a capability afetada antes de mudar comportamento — ela é mais específica que `spec.md`.
- `openspec/changes/<nome>/` — mudança em andamento: `proposal.md`, `specs/` (delta specs com `## ADDED/MODIFIED/REMOVED/RENAMED Requirements`), `design.md`, `tasks.md`.
- `openspec/changes/archive/YYYY-MM-DD-<nome>/` — mudanças concluídas. **Arquivar não aplica os deltas sozinho**: sincronize os delta specs nas capabilities principais antes (`/opsx:sync`, ou a etapa de sync do `/opsx:archive`), senão `openspec/specs/` fica descrevendo um sistema que não existe mais.
- `openspec/config.yaml` — contexto e regras injetados em toda operação OpenSpec (regras de negócio, restrições técnicas, convenções). Mudou uma convenção do projeto? Atualize aqui **e** neste CLAUDE.md, senão as duas fontes divergem.

`openspec validate --all --strict` valida capabilities e changes; rode antes de arquivar.

## Rodando os dois projetos juntos

O frontend consome a API real (não é mais mock). Um fluxo ponta a ponta exige três peças de pé:

1. PostgreSQL local com o banco `estrela` (credenciais em `backend-estrela-main/src/main/resources/application.properties`).
2. Backend: `cd backend-estrela-main && mvn spring-boot:run` — porta 8080.
3. Frontend: `cd ProjetoTaskGoFinalizado-main && npx serve .` — sirva por HTTP; abrir o HTML por `file://` quebra as chamadas à API (origem `null`).

`assets/js/api.js` usa `http://localhost:8080` por padrão, sobrescrevível definindo `window.TASKGO_API_BASE_URL` antes de carregar o script. O CORS do backend libera qualquer origem.

Os testes do backend **não** precisam de Postgres (rodam em H2 em memória) — só `mvn spring-boot:run` precisa.

## Regras de Refactoring deste projeto
- Não altere nenhum comportamento observável.
- Não adicione nenhuma feature nova durante uma tarefa de refactor.
- Todos os testes existentes devem continuar passando sem modificação.
- Liste os arquivos que serão afetados antes de qualquer edição.

## Convenções de Documentação e Diagramação

### Documentação de código
- Backend (Java): usar Javadoc em todas as classes, métodos públicos e endpoints REST. Sempre incluir `@throws` quando o método puder lançar exceção, e `@param`/`@return` completos.
- Frontend (JS): usar JSDoc em todas as funções exportadas/reutilizáveis, com `@param` e `@returns`.

### Mensagens de Commit
- Seguir estritamente o padrão Conventional Commits: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.
- Commits devem ser atômicos (uma mudança lógica por commit) e escritos no imperativo (Ex: "add", "fix", "remove" — não "added", "adding").
- Commits de `refactor` não devem compartilhar escopo com commits de `feat` (não misturar refatoração com nova funcionalidade no mesmo commit).

### Changelog
- Toda nova feature (`feat`) deve gerar uma atualização correspondente em `CHANGELOG.md`.
- Seguir o padrão Keep a Changelog (em pt-BR). A primeira versão registrada é a v1.1.0; o MVP é anterior a ela e está documentado em `spec.md`/`plan.md`.
- Entradas novas entram na seção `[Não publicado]` até virarem versão.

### Diagramas
- Todos os diagramas devem ser escritos em sintaxe Mermaid.
- Diagramas devem ser salvos obrigatoriamente em `docs/diagrams/` (ex.: `fluxo-descoberta-publica.md`).
- Cada arquivo declara no topo se é **descritivo** ou **prescritivo** — mantenha esse cabeçalho.
- Diagramas **descritivos** (documentam o estado atual do código/arquitetura) são gerados via código, a partir da análise do código-fonte existente.
- Diagramas **prescritivos** (definem arquitetura/fluxos a serem implementados) são gerados exclusivamente em Plan Mode, utilizando a metodologia OpenSpec.
