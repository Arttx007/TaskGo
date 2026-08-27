# Frontend TaskGo Guidelines (HTML/CSS/JS estático)

## Estrutura
- `assets/css/` — estilos (um arquivo por página, além de `style.css` compartilhado)
- `assets/js/` — scripts (um por página, além de `api.js`/`auth-guard.js`/`navigation.js`/`footer.js`/`validacao.js` compartilhados)
- `components/` — cópias de referência de nav/footer (markup TeleportHQ), **não são incluídas dinamicamente**
- `pages/` — páginas da aplicação
- `index.html` — entrada principal

Não há build tooling, bundler nem framework: cada página carrega `<script src>` na ordem certa e os scripts se comunicam por globais (`TaskGoAPI`, `exigirSessao`).

## Arquitetura

### Toda rede passa por `assets/js/api.js`
`api.js` é a única camada de acesso à API — é o único arquivo do projeto que chama `fetch`, e essa invariante deve ser mantida. Ele expõe o global `TaskGoAPI` (IIFE) e cuida de:
- **Base URL**: `window.TASKGO_API_BASE_URL` ou `http://localhost:8080`.
- **Sessão**: JWT + dados do usuário em `localStorage` sob a chave `taskgo_session`. Use `TaskGoAPI.getSessaoValida()` — diferente de `getSessaoAtual()`, ela decodifica a claim `exp` e descarta (fazendo logout) tokens vencidos, para que uma sessão expirada nunca seja tratada como válida.
- **Erros**: respostas não-2xx viram `ApiError` com `status` e `fieldErrors` (mapa campo → mensagem), que é o formato do `ErrorResponse` do backend. Trate por `status`/`fieldErrors` em vez de comparar strings de mensagem.

### Páginas protegidas
`auth-guard.js` expõe `exigirSessao('CLIENTE'|'PRESTADOR'|'ADMIN')`, que redireciona para `login.html` se não houver sessão válida do tipo esperado. **Ordem de carregamento obrigatória** nos painéis: `api.js` → `auth-guard.js` → script da página. É a proteção de UX apenas — a autorização real é do backend.

Painéis por papel: `painel-cliente.html` (busca/contratação/pagamento/avaliação), `painel-profissional.html` (solicitações, catálogo de serviços, saldo, saque Pix), `painel-administrador.html` (fila de KYC, parâmetros de negócio), `cadastro-kyc.html` (upload de documentos).

### Nav e footer são duplicados inline
`components/navigation.html` e `components/footer.html` não são carregados em runtime — cada página tem o markup de nav/footer **copiado inline**. `navigation.js`/`footer.js` operam por IDs/classes presentes em todas as páginas. Ao alterar navegação ou rodapé, replique a mudança em cada HTML; editar só `components/` não tem efeito nenhum.

### Dependências externas via CDN
`painel-cliente.html` carrega Leaflet (mapa de prestadores próximos) e `painel-profissional.html` carrega Cropper.js (recorte de imagem) por `<script src>` de CDN. Não há `package.json` — não tente resolver isso por npm sem antes decidir introduzir tooling.

## Build & Test Commands
- Não há build tooling nem suíte de testes automatizada.
- Para rodar: `npx serve .` e abra `http://localhost:3000` (ou a porta indicada). **Não** abra os arquivos por `file://` — o `fetch` para a API quebra com origem `null`.
- O backend precisa estar de pé em `localhost:8080` para qualquer fluxo autenticado (ver `../backend-estrela-main/CLAUDE.md`).

## Code Style & Rules
- Nenhum `fetch` fora de `assets/js/api.js`. Ao consumir um endpoint novo, adicione a função lá e chame-a a partir do script da página.
- Lógica em `assets/js/`, não em `<script>` inline nas páginas. As páginas geradas pelo TeleportHQ (`servicos.html`, `profissionais-prximos.html`) ainda têm blocos inline herdados do gerador — não replique esse padrão em código novo.
- Duas paletas de design tokens coexistem: páginas públicas usam classes `.tprt-*`; os painéis usam tema escuro (`--bg-dark`, `--primary-blue`). Siga a paleta da página onde a UI entra, não misture nem invente uma terceira.
- `assets/js/painel.js` não é referenciado por nenhuma página (órfão) — confirme antes de assumir que é o script de algum painel.

## Docstrings (JSDoc)
- Toda função em `assets/js/` deve ter comentário JSDoc com `@param` e `@returns`.
