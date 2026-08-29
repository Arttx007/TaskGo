## Why

As três páginas públicas do TaskGo são a única coisa que um visitante vê antes de decidir se cria conta — e boa parte do que elas mostram não existe.

O formulário principal da home, o **"Gerar Estimativa IA"**, não consulta nada: `assets/js/index.js:111-118` tem um `switch` com quatro preços fixos, soma `raio × 2,5` e apresenta o resultado como faixa "gerada por IA". O backend não tem endpoint de estimativa, e o `pom.xml` não tem uma única dependência de IA, cliente HTTP ou LLM. A promessa não está isolada nesse formulário: são **~30 trechos em 4 páginas**, incluindo a seção "Previsibilidade Total com IA" (`index.html:419`), o selo de rodapé "IA Verificada" (`:1246`), o rótulo "Faixa de Preço (IA)" e um depoimento inventado que elogia exatamente a precisão da estimativa (`:1001`).

O resto do inventário é da mesma natureza: **4 profissionais que não existem** no carrossel da home, com nomes, fotos de banco de imagens, distâncias e uma contagem de avaliações (`4.9 (124)`) que o backend não sabe calcular — não há tabela de avaliações, só duas colunas em `fato_servicos`. **6 depoimentos falsos**. Um **"Mapa Interativo"** que é uma foto do Pexels com "12 Profissionais Ativos" e "5 min Tempo Médio" escritos no HTML. Um **"Portfólio em Destaque"** inteiro, quando portfólio de fotos é US-16, Fase 2, sem campo, tabela ou rota. E **~60 links `href="#"`**.

Dois desses elementos não apenas mentem — eles destroem funcionalidade que já estava pronta:

- `index.html:318` já é `<form action="pages/profissionais-prximos.html" method="GET">` com `name="service"` e `name="radius"`, exatamente os parâmetros que `assets/js/profissionais.js:35` já lê para disparar busca real. O `e.preventDefault()` de `index.js:102` intercepta esse envio para mostrar a estimativa falsa. **A home tem uma busca funcional e a esconde atrás de um número inventado.**
- O botão "Aplicar Filtros" (`pages/profissionais-prximos.html:813`) está dentro de um `<form>` sem handler: clicar recarrega a página e apaga os resultados reais que o usuário acabou de obter.

Enquanto isso a busca real existe, é pública e funciona: `GET /servicos-ofertados/buscar` com Haversine e ordenação por distância (US-03). O problema não é falta de produto — é que a vitrine não aponta para ele.

## What Changes

Cada item ganha uma decisão explícita: integração real ou remoção.

**Quatro endpoints novos, todos públicos e todos aditivos** (nenhuma entidade JPA muda, portanto **nenhuma migration** e `ddl-auto=validate` segue satisfeito):

- `GET /servicos-ofertados/categorias` — categorias com serviço ativo e a contagem de cada uma.
- `GET /servicos-ofertados/estimativa?categoria=X` — faixa de preço **realmente praticada** (mín/mediana/máx) na categoria. Substitui a "IA" por dado que já está no banco.
- `GET /avaliacoes/recentes?limite=N` — avaliações reais (nota, comentário, primeiro nome do cliente) para alimentar os depoimentos.
- `BuscaServicoResponse` ganha `latitude` e `longitude` **arredondadas**, para que o mapa possa ser real.

E a busca existente ganha **filtro real de nota e de preço**: `GET /servicos-ofertados/buscar` passa a aceitar `notaMinima`, `precoMin` e `precoMax`, todos opcionais. Os controles do painel "Refine sua Busca" deixam de ser inertes filtrando de verdade no servidor, e não escondendo linhas no navegador.

Como filtrar por nota exclui quem ainda não foi avaliado, a busca também passa a aceitar `apenasSemAvaliacao`, e a lista de profissionais ganha uma vitrine **"Novos na sua região"**. Sem isso o prestador recém-aprovado cairia no impasse de não conseguir a primeira avaliação porque não aparece, e não aparecer porque não tem avaliação.

**index.html** — o handler da estimativa falsa sai e o formulário volta a submeter nativamente para a busca real; o botão deixa de se chamar "Gerar Estimativa IA"; a faixa de preço passa a vir de `/estimativa`; o carrossel de 4 pessoas inventadas vira vitrine de **categorias** vinda de `/categorias`; os depoimentos passam a vir de `/avaliacoes/recentes`; a copy de IA é reescrita.

**pages/servicos.html** — o grid de 8 cards **é mantido**: ele não é mock de resultados, cada card já linka `profissionais-prximos.html?service=<slug>` e dispara busca real. Sendo `categoria` um texto livre no backend, esse grid é o único vocabulário controlado que o produto tem. Ele passa a exibir a contagem real por categoria e a revelar categorias que existem no backend e não estão no grid curado. O campo de busca, que hoje promete "Buscar serviço (ex: Trocar chuveiro)" e só filtra 8 títulos no DOM, passa a levar à busca real.

**pages/profissionais-prximos.html** — o select "Escala do serviço" sai (não existe o conceito no backend, e seu handler só repinta o botão); o mapa passa a ser Leaflet real plotando os resultados, com contagem real e sem o "5 min" que nenhum dado sustenta; as estrelas de nota mínima e o select de faixa de preço passam a **filtrar de verdade**, enviando `notaMinima`/`precoMin`/`precoMax` para a busca; "Aplicar Filtros" passa a ser interceptado e a refazer a busca com os filtros escolhidos; a vitrine "Novos na sua região" aparece quando o filtro de nota excluiu prestadores ainda não avaliados; o "Portfólio em Destaque" é removido por inteiro; os depoimentos passam a ser reais; o `?radius=` vindo da home deixa de ser descartado.

**Links mortos** — os que têm destino real são ligados (Serviços Populares → busca por categoria, Como Funciona → `pages/como-funciona.html`, Seja um Profissional → `pages/cadastro.html`). Os que prometem o que não existe são removidos: "Baixar App" (US-21, Fase 3), Blog, Sobre Nós, Contato, Termos, Privacidade, Cookies e os ícones sociais.

**Não há BREAKING.** Os três endpoints são novos, e as duas alterações no contrato de `GET /servicos-ofertados/buscar` são aditivas: dois campos novos na resposta e três parâmetros de consulta **opcionais**. Uma busca que não os informa comporta-se exatamente como hoje, então nenhum consumidor existente quebra. Não há mudança de banco, de schema, de migration nem de dependência do backend.

**US e RN tocadas:** US-03 (busca de prestadores por geolocalização) e US-09 (avaliação do cliente pós-conclusão), ambas já implementadas; RN04 é preservada, porque todo endpoint novo filtra por prestador `APROVADO`. **RN01 (taxa), RN02 (ciclo de vida) e RN03 (custódia e saque) não são tocadas** — nenhum fluxo de dinheiro ou de estado muda.

## Não-objetivos

- **Implementar IA de verdade.** A decisão de produto foi trocar a promessa pelo dado real que já existe: preço praticado. Integrar um LLM adicionaria dependência, chave de API, custo por chamada e latência, e é escopo próprio — não uma correção de mock.
- **Criar as páginas institucionais e legais** (Sobre Nós, Contato, Blog, Termos de Uso, Privacidade, Cookies). Os links são removidos porque não têm destino; escrever conteúdo jurídico e institucional é outra change, com outro tipo de revisão.
- **Perfil público de prestador.** É o que o botão "Ver Perfil" sugere, mas `GET /prestadores/{id}` exige JWT hoje, e abrir perfil ao público envolve decidir o que se expõe de um autônomo. O carrossel passa a levar à busca por categoria em vez de fingir um perfil.
- **Qualquer coisa de Fase 2/3:** portfólio de fotos (US-16), destaque de prestadores bem avaliados (US-14), app nativo (US-21). O `config.yaml` coloca Fase 2/3 fora de escopo, e o "Portfólio em Destaque" é removido justamente por ser US-16 — não antecipado.
- **Paginação na busca.** Os filtros de nota e preço entram no servidor, mas a busca continua devolvendo o conjunto inteiro. Paginar exige decidir contrato de página, total e ordenação estável — escopo próprio.
- **Trocar a nota média por média ponderada (prior bayesiano).** Seria a solução estruturalmente correta tanto para o prestador sem nota quanto para um defeito que já está no ar: `FatoServicoService.recalcularNotaMedia` faz média aritmética simples, então uma única nota 5,0 ordena acima de cinquenta notas 4,8. Mas `nota_media` deixaria de ser a média que o requisito atual de `solicitacao-servico` descreve, e mudaria números já exibidos a prestadores — precisa de change própria, com decisão de produto sobre o número que cada um vê.
- **Devolver a contagem de avaliações por prestador.** `totalAvaliacoes` em `BuscaServicoResponse` deixaria a interface distinguir "5,0 com uma avaliação" de "4,8 com cinquenta" — é aditivo e barato, mas é melhoria de confiança na lista principal, não o que resolve a visibilidade do prestador novo. Fica disponível para uma próxima change.
- **Ordenação alternativa dos resultados** (por preço, por nota). A ordenação segue sendo por distância, como US-03 define. Filtrar e ordenar são decisões separadas.
- **Unificar as três implementações divergentes de menu mobile** e corrigir o duplo-bind em `profissionais-prximos.html` (bloco inline `:493-548` mais `navigation.js`). É refactor, e a convenção do projeto proíbe misturar refactor com feat.
- **Limpeza geral de mojibake** (`4~.+`, `~.~.~.~.~.`, `Avaliacao 5.0`) além dos trechos que já serão reescritos.
- **O `<link rel="canonical">` apontando para `jumpy-celebrated-ferret-u21wka.teleporthq.app`** (`index.html:1508`, `pages/profissionais-prximos.html:1352`). É defeito real de SEO — um canonical entregando a indexação a um domínio de terceiro — mas é `fix` de outra natureza e merece a própria change.
- **`pages/como-funciona.html`, `cadastro.html`, `login.html`, `404.html`.** Fora de escopo, conforme o pedido. Vale registrar que `como-funciona.html` é a única página pública com **zero** `href="#"`, e que tem 4 menções a IA que sobreviverão a esta change.

## Capabilities

### New Capabilities

- `vitrine-publica`: o que as superfícies públicas (home, catálogo de serviços, lista de profissionais próximos) podem afirmar ao visitante — proíbe pessoa, depoimento e métrica fabricados, exige que todo controle visível tenha efeito, que todo link leve a algum lugar e que prestador ainda não avaliado tenha caminho de visibilidade quando um filtro o exclui. Segue o precedente já existente em `solicitacao-servico` → "Superfícies decorativas nos painéis", que é a convenção deste projeto para documentar o que é demonstração sem fingir que é dado.

### Modified Capabilities

- `busca-servicos`: ganha o catálogo público de categorias, a faixa de preço praticada por categoria, as coordenadas nos resultados e o filtro por nota mínima e faixa de preço; o requisito da busca pública passa a listar os parâmetros novos e o de ordenação por proximidade passa a declarar que cada resultado traz posição aproximada.
- `solicitacao-servico`: ganha a leitura pública de avaliações recentes. A capability já é a dona da avaliação (US-09), então o requisito novo entra nela em vez de criar capability nova.

## Impact

**Backend** (`backend-estrela-main/`) — 4 DTOs novos em `DTO/`, 1 controller novo (`Controller/AvaliacaoPublicaController.java`), 2 endpoints acrescentados a `Controller/ServicoOfertadoController.java`, lógica nova em `Service/ServicoOfertadoService.java` e um service novo para avaliação pública, o **primeiro `@Query` do projeto** em `repository/ServicoOfertadoRepository.java`, e 3 `permitAll` novos em `security/SecurityConfig.java`. `DTO/BuscaServicoResponse.java` ganha dois campos, e `ServicoOfertadoService.buscar` mais o endpoint de busca ganham três parâmetros opcionais de filtro.

**Sem impacto** em banco de dados, migrations Flyway (não há `V8`), entidades JPA, `pom.xml` ou build. `ddl-auto=validate` continua válido porque nenhuma entidade muda.

**Frontend** (`ProjetoTaskGoFinalizado-main/`) — `assets/js/api.js` ganha 3 funções com `auth: false`, mantendo a invariante de que nenhum outro arquivo chama `fetch`. Alterados: `index.html`, `assets/js/index.js`, `pages/servicos.html`, `assets/js/servicos.js`, `pages/profissionais-prximos.html`, `assets/js/profissionais.js`. **`index.html` e `pages/servicos.html` não carregam `api.js` hoje** — precisam da tag antes do script da página.

**Verificação** — `mvn test` a partir de `backend-estrela-main/`, com atenção a `FluxoCompletoIntegrationTest`, que é o primeiro a quebrar quando um contrato de endpoint muda e cobre a busca de US-03. No frontend não há suíte automatizada: a checagem é manual no navegador, com backend no ar e as páginas servidas por HTTP.

**Documentação** — esta é a primeira `feat` do repositório, então cria `CHANGELOG.md` (Keep a Changelog, a partir da v1.1.0) e `docs/diagrams/`, ambos exigidos pelo `config.yaml` e ainda inexistentes.

**Efeito colateral desejado** — o visitante deixa de ver preço inventado e passa a ver o preço que os prestadores realmente cobram. Onde não há dado suficiente, a interface diz que não há, em vez de preencher com um número plausível.
