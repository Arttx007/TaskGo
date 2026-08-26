# Frontend TaskGo Guidelines (HTML/CSS/JS estático)

## Estrutura
- `assets/css/` — estilos (um arquivo por página, além de `style.css` compartilhado)
- `assets/js/` — scripts (um arquivo por página, além de `navigation.js`/`footer.js` compartilhados)
- `components/` — cópias de referência de nav/footer (markup TeleportHQ), **não são incluídas dinamicamente**
- `pages/` — páginas da aplicação
- `index.html` — entrada principal

## Arquitetura

- `components/navigation.html` e `components/footer.html` não são carregados via fetch/include em runtime — cada página em `pages/` (e `index.html`) tem o markup de nav/footer **duplicado inline**. `navigation.js` e `footer.js` funcionam por IDs/classes que existem em todas as páginas. Ao editar a navegação ou o rodapé, é preciso replicar a mudança em cada página HTML, não só em `components/`.
- Ainda não há integração real com o backend: não existe `assets/js/api.js`, e nenhum arquivo em `assets/js/` faz `fetch` para a API Spring Boot (ex.: `login.js` apenas redireciona para `painel-profissional.html` sem chamar `/auth/login`). Isso é esperado no estado atual — a integração é trabalho futuro, não um bug a corrigir de surpresa.

## Build & Test Commands
- Não há build tooling. Para visualizar localmente: abra `index.html` ou sirva a pasta com `npx serve .`
- Não há suíte de testes automatizada configurada ainda.

## Code Style & Rules
- Ao adicionar chamadas de API para o backend, centralize em `assets/js/api.js` em vez de espalhar `fetch` pelos arquivos de `pages/` e `components/`.
- Manter separação: lógica em `assets/js/`, nunca `<script>` inline nas páginas de `pages/`.

## Docstrings (JSDoc)
- Toda função em `assets/js/` deve ter comentário JSDoc com `@param` e `@returns`.