# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Este repositório contém dois projetos do TaskGo (marketplace de serviços tipo "Uber de serviços", conectando clientes a prestadores):

- `backend-estrela-main/` — Spring Boot 4 (Java 17) REST API, pacote `com.example.Estrela`. Ver `backend-estrela-main/CLAUDE.md`.
- `ProjetoTaskGoFinalizado-main/` — frontend estático HTML/CSS/JS (markup gerado pelo TeleportHQ), sem build tooling. Ver `ProjetoTaskGoFinalizado-main/CLAUDE.md`.

Não há build system compartilhado, nem `package.json` na raiz, nem scripts raiz — cada subprojeto é desenvolvido e executado independentemente. Sempre `cd` para a subpasta relevante antes de rodar comandos (comandos Maven devem rodar de `backend-estrela-main/`).

O backend segue um modelo de dados em estrela (fato `FatoServico` + dimensões `Cliente`/`Prestador`/`Tempo`/`Localizacao`), voltado a analytics (dashboard/relatórios). O frontend ainda não consome essa API — não há integração real entre os dois projetos hoje. Ver detalhes em cada `CLAUDE.md` de subprojeto.

O projeto já está funcional/completo. Mudanças a partir de agora são refactors, correções e novas features pontuais — não recriação.

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

### Diagramas
- Todos os diagramas devem ser escritos em sintaxe Mermaid.
- Diagramas devem ser salvos obrigatoriamente em `docs/diagrams/`.
- Diagramas **descritivos** (documentam o estado atual do código/arquitetura) são gerados via código, a partir da análise do código-fonte existente.
- Diagramas **prescritivos** (definem arquitetura/fluxos a serem implementados) são gerados exclusivamente em Plan Mode, utilizando a metodologia OpenSpec.