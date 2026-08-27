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
- Seguir o padrão Keep a Changelog, iniciando a partir da versão v1.1.0.
- `CHANGELOG.md` ainda não existe no repositório — a próxima `feat` deve criá-lo.

### Diagramas
- Todos os diagramas devem ser escritos em sintaxe Mermaid.
- Diagramas devem ser salvos obrigatoriamente em `docs/diagrams/` (diretório ainda não criado).
- Diagramas **descritivos** (documentam o estado atual do código/arquitetura) são gerados via código, a partir da análise do código-fonte existente.
- Diagramas **prescritivos** (definem arquitetura/fluxos a serem implementados) são gerados exclusivamente em Plan Mode, utilizando a metodologia OpenSpec.
