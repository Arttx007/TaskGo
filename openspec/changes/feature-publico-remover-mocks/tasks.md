Caminhos relativos a `backend-estrela-main/` nas fases 2 a 4 e a `ProjetoTaskGoFinalizado-main/` nas fases 5 a 9. **Nenhuma entidade JPA muda e não existe `V8`** — se alguma migration parecer necessária, a premissa da change caiu e a proposta deve ser revista. Comandos Maven rodam de `backend-estrela-main/`.

Convenção de commit: as fases 2 a 8 e a 10 são `feat`; a fase 9 e a **tarefa 2.1** são `fix` e **não podem compartilhar commit** com nenhuma tarefa de `feat`.

## 1. Preparação e linha de base

Exige backend no ar (Postgres + `mvn spring-boot:run`) e o frontend servido por HTTP (`npx serve .`). Abrir por `file://` quebra as chamadas à API.

- [x] 1.1 Registrar o estado atual dos mocks antes de tocar em qualquer arquivo; verificação: com a home aberta, escolher "Eletricista" e raio 20 km produz a faixa "R$ 170 - R$ 230" (150 + 20x2,5 = 200; min=floor(200x0,85), max=ceil(200x1,15)) sem nenhuma requisição de rede no DevTools, confirmando o cálculo local de `assets/js/index.js:111-123`
- [x] 1.2 Confirmar que o formulário da home já aponta para a busca real; verificação: `grep -n 'action="pages/profissionais-prximos.html"' index.html` retorna a linha 319, e `grep -n "urlParams.get('service')" assets/js/profissionais.js` retorna a linha 35
- [x] 1.3 Confirmar que o slider de raio é o único controle funcional do painel de filtros, para não removê-lo por engano; verificação: `grep -c 'type="range"' pages/profissionais-prximos.html` retorna 1, e mover o slider com uma busca ativa dispara nova requisição a `/servicos-ofertados/buscar` no DevTools
- [x] 1.4 Garantir que existe dado real para os endpoints novos exercitarem; verificação: o banco tem ao menos 3 serviços `ATIVO` de prestador `APROVADO` na mesma categoria e ao menos 1 solicitação em `AVALIADO` com `comentario_avaliacao` não vazio

## 2. Backend — base de erro e endpoints públicos de catálogo e preço

Arquivos: `exception/GlobalExceptionHandler.java`, `exception/ValidacaoException.java` (novo), `src/main/java/com/example/Estrela/DTO/CategoriaDisponivelResponse.java` (novo), `DTO/EstimativaPrecoResponse.java` (novo), `repository/ServicoOfertadoRepository.java`, `Service/ServicoOfertadoService.java`, `Controller/ServicoOfertadoController.java`, `security/SecurityConfig.java`.

As tarefas 2.1 e 2.2 foram acrescentadas durante o apply: as specs exigem 400 `VALIDACAO` em dois cenários, e esse status não era alcançável por query param. O `GlobalExceptionHandler` não estende `ResponseEntityExceptionHandler` e tem um `@ExceptionHandler(Exception.class)` catch-all, que hoje converte em **500** o que o Spring devolveria como 400 — verificado: `GET /servicos-ofertados/buscar` sem `categoria` responde `500 ERRO_INTERNO`. A 2.1 é `fix` e vai em commit próprio; a 2.2 é `feat`.

- [x] 2.1 Tratar `MissingServletRequestParameterException` no `GlobalExceptionHandler`, devolvendo 400 `VALIDACAO` com o nome do parâmetro ausente em `fieldErrors`; verificação: `GET /servicos-ofertados/buscar` sem `categoria` passa de `500 ERRO_INTERNO` para `400 VALIDACAO`, e o cenário "Categoria ausente" da spec deixa de ser atendido por um 500. Commit `fix`, separado dos de `feat`
- [x] 2.2 Criar `exception/ValidacaoException.java` e mapeá-la para 400 `VALIDACAO` no handler, para que regra de negócio possa recusar combinação inválida de parâmetros sem montar `ResponseEntity` à mão; verificação: `mvn -q compile` passa e a exceção nova aparece no handler ao lado das outras nove
- [x] 2.3 Criar os records `CategoriaDisponivelResponse(String categoria, long totalServicos)` e `EstimativaPrecoResponse(String categoria, BigDecimal minimo, BigDecimal mediana, BigDecimal maximo, int amostra, String mensagem)` em `DTO/`; verificação: `mvn -q compile` passa e os dois arquivos são records, coerentes com o padrão dos DTOs existentes
- [x] 2.4 Adicionar ao `ServicoOfertadoRepository` a agregação de categorias com `@Query` e `GROUP BY`, filtrando `status = ATIVO` e `prestador.statusKyc = APROVADO`; verificação: é o primeiro `@Query` do projeto (`grep -rc "@Query" src/main/java` era 0 antes) e um `@DataJpaTest` novo confirma que categoria de prestador não aprovado não aparece
- [x] 2.5 Implementar em `ServicoOfertadoService` o método de categorias disponíveis, com Javadoc explicando por que a agregação é no repository e não em memória; verificação: teste unitário cobre categoria com vários serviços agregando em uma linha só
- [x] 2.6 Implementar em `ServicoOfertadoService` a apuração de faixa de preço, com mediana calculada em Java e amostra mínima de 3; verificação: testes cobrem amostra de 3+ devolvendo mín/mediana/máx, amostra de 1 e de 2 devolvendo só mensagem, e amostra 0 devolvendo mensagem — todos sem lançar exceção
- [x] 2.7 Expor `GET /servicos-ofertados/categorias` e `GET /servicos-ofertados/estimativa` no controller, com `categoria` obrigatória na estimativa e Javadoc com `@throws` indicando o status HTTP; verificação: chamar a estimativa sem `categoria` responde 400 com código `VALIDACAO` pelo `GlobalExceptionHandler`, sem `ResponseEntity` de erro montado à mão
- [x] 2.8 Liberar as duas rotas em `SecurityConfig`, ao lado do `GET /servicos-ofertados/buscar` já público; verificação: `curl` sem header `Authorization` nas duas rotas responde 200, e uma rota autenticada qualquer continua respondendo 401/403

## 3. Backend — avaliações públicas (`feat`)

Arquivos: `DTO/AvaliacaoPublicaResponse.java` (novo), `Service/AvaliacaoPublicaService.java` (novo), `Controller/AvaliacaoPublicaController.java` (novo), `repository/FatoServicoRepository.java`, `security/SecurityConfig.java`.

- [x] 3.1 Criar o record `AvaliacaoPublicaResponse(int nota, String comentario, String clientePrimeiroNome, String categoria, String cidade, LocalDate data)`; verificação: o record não tem campo de id, e-mail ou nome completo de cliente
- [x] 3.2 Implementar o service que seleciona solicitações em `AVALIADO` com nota e comentário não vazio, de prestador `APROVADO`, da mais recente para a mais antiga, recortando o nome do cliente ao primeiro nome; verificação: testes cobrem avaliação sem comentário omitida, solicitação não avaliada omitida, prestador não aprovado omitido e ordenação decrescente por data
- [x] 3.3 Aplicar padrão e teto de `limite`, truncando em vez de recusar; verificação: `limite` ausente devolve o padrão, `limite` acima do teto devolve no máximo o teto e responde 200 — nunca 400
- [x] 3.4 Criar `AvaliacaoPublicaController` com base `/avaliacoes` e Javadoc, e liberar `GET /avaliacoes/recentes` em `SecurityConfig`; verificação: a rota responde 200 sem token, e nenhuma rota sob `/servicos/**` foi liberada no processo
- [x] 3.5 Confirmar que nenhum dado de contato vaza; verificação: a resposta de `GET /avaliacoes/recentes` não contém `@`, nem campo de id de cliente, nem nome com sobrenome

## 4. Backend — coordenadas e filtros na busca (`feat`)

Arquivos: `DTO/BuscaServicoResponse.java`, `Service/ServicoOfertadoService.java`, `Controller/ServicoOfertadoController.java`, `src/test/java/...`.

As duas alterações são no contrato de `GET /servicos-ofertados/buscar`, que já está público e em uso. Ambas devem ficar aditivas — se alguma exigir mudança em consumidor existente, a premissa caiu.

- [x] 4.1 Acrescentar `latitude` e `longitude` (`Double`, nulos quando não houver coordenadas) a `BuscaServicoResponse`, arredondadas para 3 casas decimais no backend; verificação: teste confirma que a coordenada devolvida tem no máximo 3 casas e que `distanciaKm` continua calculada com a coordenada cheia
- [x] 4.2 Confirmar que o campo é aditivo e que serviço sem coordenadas segue o comportamento atual; verificação: teste confirma que serviço sem lat/lon vem com posição nula, sem distância, e **não** é descartado pelo filtro de raio
- [x] 4.3 Acrescentar `notaMinima`, `precoMin` e `precoMax` como parâmetros **opcionais** ao endpoint de busca e à assinatura de `ServicoOfertadoService.buscar`, com Javadoc; verificação: chamar a busca sem nenhum dos três devolve exatamente o mesmo resultado de antes da mudança
- [x] 4.4 Aplicar os três predicados no mesmo passo em memória que já descarta prestador não aprovado e calcula distância, sem criar query nova nem tocar na seleção de três ramos; verificação: `git diff` em `ServicoOfertadoService` não altera as chamadas ao repository, e a ordenação por distância segue intacta
- [x] 4.5 Descartar prestador sem `notaMedia` quando `notaMinima` for informada, e mantê-lo quando for omitida; verificação: testes cobrem os dois lados — com nota mínima o prestador sem avaliação não aparece, sem nota mínima ele aparece
- [x] 4.6 Aplicar `precoMin`/`precoMax` inclusive nos extremos e permitir faixa aberta em qualquer ponta; verificação: teste confirma que serviço com preço exatamente igual ao limite aparece, e que informar só uma das pontas não limita a outra
- [x] 4.7 Confirmar que os filtros compõem com os critérios existentes e preservam a resposta de resultado vazio; verificação: teste com coordenadas, raio, nota mínima e faixa simultâneos devolve só o que satisfaz tudo, ordenado por distância; e filtro sem correspondência responde 200 com lista vazia e a mensagem, não erro
- [x] 4.8 Acrescentar `apenasSemAvaliacao` (opcional) devolvendo só serviços de prestador sem `notaMedia`, compondo com categoria, raio, cidade e faixa de preço; verificação: teste confirma que nenhum prestador já avaliado aparece, que a ordenação por distância se mantém e que omitir o parâmetro não altera a busca
- [x] 4.9 Recusar a combinação contraditória de `apenasSemAvaliacao` com `notaMinima`; verificação: enviar os dois responde 400 com código `VALIDACAO` pelo `GlobalExceptionHandler`, e nenhuma busca é executada — nunca um dos dois ignorado em silêncio
- [x] 4.10 Rodar a suíte inteira e tratar a regressão do contrato de busca; verificação: `mvn test` fica verde, com atenção a `FluxoCompletoIntegrationTest` — se ele quebrar por outro motivo que não asserção de campos novos, a premissa de mudança aditiva caiu
- [x] 4.11 Confirmar que nada de schema foi tocado; verificação: `ls src/main/resources/db/migration/` continua terminando em `V7__administrador_parametro_negocio.sql` e `git diff --name-only` não inclui nenhum arquivo em `Entity/`

## 5. Frontend — camada de API (`feat`)

Arquivos: `assets/js/api.js`, `index.html`, `pages/servicos.html`.

- [x] 5.1 Adicionar a `api.js` as funções `listarCategorias()`, `obterEstimativa(categoria)` e `listarAvaliacoesRecentes(limite)`, todas com `auth: false` e JSDoc com `@param`/`@returns`, e exportá-las no objeto de retorno; verificação: `grep -c "fetch(" assets/js/api.js` continua sendo o único ponto de rede do projeto (`grep -rl "fetch(" assets/js | wc -l` retorna 1)
- [x] 5.2 Carregar `api.js` antes do script da página em `index.html` e `pages/servicos.html`, que hoje não o carregam; verificação: com a home aberta, `TaskGoAPI` deixa de ser `undefined` no console, e o mesmo em `servicos.html`
- [x] 5.3 Confirmar a ordem de execução dos scripts clássicos; verificação: `api.js` aparece antes de `index.js` e de `servicos.js` no HTML, e nenhum `type="module"` foi introduzido
- [x] 5.4 Atualizar apenas o JSDoc de `buscarServicos` para documentar `notaMinima`, `precoMin`, `precoMax` e `apenasSemAvaliacao`; verificação: o corpo da função não muda — ela já monta a query string genericamente a partir das chaves do objeto de filtro (`api.js:240-244`), então os parâmetros novos trafegam sem código novo

## 6. Frontend — home (`feat`)

Arquivos: `index.html`, `assets/js/index.js`.

- [x] 6.1 Remover o handler de submit e a função `showEstimateResult` de `index.js` (hoje linhas 98-166), deixando o formulário submeter nativamente; verificação: `grep -c "showEstimateResult\|preventDefault" assets/js/index.js` retorna 0 na seção da busca, e enviar o formulário navega para `pages/profissionais-prximos.html?service=...&radius=...`
- [x] 6.2 Renomear o rótulo do botão de "Gerar Estimativa IA" para o texto de busca e remover a classe `ai-estimate-card`; verificação: `grep -c "Estimativa IA" index.html` retorna 0
- [x] 6.3 Exibir a faixa de preço real ao escolher a categoria, consumindo `obterEstimativa`, e mostrar a mensagem do backend quando a amostra for insuficiente; verificação: a faixa exibida coincide com o retorno de `GET /servicos-ofertados/estimativa` para a mesma categoria, conferido no DevTools
- [x] 6.4 Substituir os 4 `pro-card` hardcoded por vitrine de categorias vinda de `listarCategorias`, cada card levando a `pages/profissionais-prximos.html?service=<categoria>`; verificação: `grep -c "Ricardo Santos\|Carla Oliveira\|Marcos Lima\|Juliana Costa" index.html` retorna 0, e nenhum botão "Ver Perfil" resta
- [x] 6.5 Garantir que os cards injetados fiquem visíveis, já que o `IntersectionObserver` de `index.js:75-93` roda uma única vez no `DOMContentLoaded` e zera a opacidade dos `.pro-card`; verificação: os cards de categoria aparecem sem precisar de scroll e sem ficar com `opacity: 0`
- [x] 6.6 Substituir os 3 depoimentos hardcoded pelo consumo de `listarAvaliacoesRecentes`, escondendo a seção quando a lista vier vazia; verificação: `grep -c "Ana Silva\|Roberto Mendes\|Lucia Ferreira" index.html` retorna 0, e com a tabela de avaliações sem comentários a seção não é renderizada
- [x] 6.7 Reescrever a copy de IA da home; verificação: `grep -nc "IA" index.html` retorna 0, incluindo a seção "Previsibilidade Total com IA" e o selo de rodapé "IA Verificada"

## 7. Frontend — catálogo de serviços (`feat`)

Arquivos: `pages/servicos.html`, `assets/js/servicos.js`.

- [x] 7.1 Manter os 8 cards curados e anotar cada um com a contagem real de `listarCategorias`, marcando como indisponível o card sem serviço ativo; verificação: um card cuja categoria não tem serviço ativo deixa de levar a uma busca vazia, e a contagem exibida coincide com o retorno de `/servicos-ofertados/categorias`
- [x] 7.2 Renderizar um bloco "Outras categorias disponíveis" com as categorias que o backend tem e o grid curado não prevê; verificação: publicando um serviço numa categoria inexistente no grid, ela passa a aparecer nesse bloco e leva à busca real
- [x] 7.3 Fazer o campo de busca navegar para a busca real no Enter/submit, mantendo o filtro do grid ao digitar, e reescrever o placeholder que hoje promete "Buscar serviço (ex: Trocar chuveiro)"; verificação: digitar um termo e pressionar Enter abre `profissionais-prximos.html?service=<termo>` com a busca disparada
- [x] 7.4 Reconsultar as `NodeList`s após injetar cards e proteger o acesso a `data-category`; verificação: com o bloco de outras categorias renderizado, os botões de filtro e a busca continuam funcionando sem lançar `TypeError` no console (hoje `servicos.js:37` chama `.getAttribute('data-category').includes(...)` sem guarda)
- [x] 7.5 Reescrever a copy de IA da página; verificação: `grep -nc "IA" pages/servicos.html` retorna 0

## 8. Frontend — profissionais próximos (`feat`)

Arquivos: `pages/profissionais-prximos.html`, `assets/js/profissionais.js`.

- [x] 8.1 Remover o select "Escala do serviço" e o handler cosmético de `profissionais.js:68-83`; verificação: `grep -c 'name="escala"' pages/profissionais-prximos.html` retorna 0, o botão de busca mantém seu `<span>` interno intacto e a busca envia sem exigir o campo
- [x] 8.2 Passar a ler `?radius=` da URL e aplicá-lo como `raioKm` na busca automática; verificação: chegar da home com `?service=eletricista&radius=20` dispara a busca com raio 20, conferido na query string da requisição no DevTools
- [x] 8.3 Substituir a foto do mapa por Leaflet real (CSS+JS por CDN, como `pages/painel-cliente.html:10-11`), plotando os resultados por `latitude`/`longitude`; verificação: os marcadores correspondem em quantidade aos resultados que têm coordenadas, e serviço sem coordenadas não gera marcador
- [x] 8.4 Ligar o contador de profissionais ao tamanho real do resultado e remover "5 min Tempo Médio de Resposta"; verificação: o número exibido é igual a `resultados.length`, zera numa busca sem resultado, e `grep -c "5 min" pages/profissionais-prximos.html` retorna 0
- [x] 8.5 Implementar "Alternar para Lista" como alternância real entre mapa e lista do mesmo conjunto de resultados; verificação: o botão troca a visão nos dois sentidos sem refazer a busca
- [x] 8.6 Ligar as estrelas de nota mínima e o select de faixa de preço aos parâmetros da busca, acrescentando `value` às `option` (hoje as três não têm) e mapeando as faixas para `precoMin`/`precoMax`; verificação: escolher 4,5+ dispara nova requisição a `/servicos-ofertados/buscar` com `notaMinima=4.5` na query string, conferido no DevTools
- [x] 8.7 Interceptar o submit do `form.filter-panel` com `preventDefault()` e refazer a busca com os filtros escolhidos, preservando categoria e localidade já informadas; verificação: clicar "Aplicar Filtros" não recarrega a página, e a requisição enviada mantém `categoria` e as coordenadas/cidade da busca corrente
- [x] 8.8 Tratar o estado de filtro sem correspondência; verificação: uma combinação de filtros sem resultado exibe a mensagem de nada encontrado, e nenhum resultado fora dos filtros permanece na tela
- [x] 8.9 Renderizar a vitrine "Novos na sua região" a partir de uma segunda busca com `apenasSemAvaliacao`, herdando categoria, localidade, raio e faixa de preço da busca corrente e **sem** a nota mínima, reusando `criarProfCard`; verificação: com nota mínima ativa, a segunda requisição aparece no DevTools com `apenasSemAvaliacao=true` e sem `notaMinima`
- [x] 8.10 Exibir a vitrine apenas quando o filtro de nota estiver ativo, e omiti-la quando a segunda busca vier vazia; verificação: buscar sem nota mínima não dispara a segunda requisição e não renderiza a seção — nenhum prestador aparece nas duas listas ao mesmo tempo
- [x] 8.11 Garantir que o card da vitrine não exibe nota, estrela nem contagem, e declara a ausência de avaliação; verificação: inspecionando um card da seção de novos, não há `⭐` nem número de nota, e o rótulo diz que o prestador ainda não tem avaliação
- [x] 8.12 Preservar o slider de raio, que já refaz a busca com debounce; verificação: mover o slider continua disparando requisição a `/servicos-ofertados/buscar`, comportamento inalterado em relação à linha de base 1.3
- [x] 8.13 Remover a seção "Portfólio em Destaque" por inteiro, incluindo o botão morto "Solicitar Orcamento Agora"; verificação: `grep -c "destaque-perfil\|Solicitar Orcamento" pages/profissionais-prximos.html` retorna 0
- [x] 8.14 Substituir os 3 depoimentos hardcoded pelo mesmo consumo de `listarAvaliacoesRecentes` usado na home, escondendo a seção quando vazia; verificação: `grep -c "Juliana Mendes\|Carlos Eduardo\|Fernanda Lima" pages/profissionais-prximos.html` retorna 0
- [x] 8.15 Remover a copy falsa do painel de filtros: "mais de 50 categorias", "precisão da nossa IA" e o "(IA)" do rótulo de preço; verificação: `grep -nc "IA\|50 categorias" pages/profissionais-prximos.html` retorna 0

## 9. Correções de navegação e ícones (`fix` — commits separados dos de `feat`)

Arquivos: `index.html`, `pages/servicos.html`, `pages/profissionais-prximos.html`.

- [x] 9.1 Ligar os links que têm destino real nas três páginas: Serviços Populares para a busca por categoria, Como Funciona para `pages/como-funciona.html`, Seja um Profissional e Criar Conta Profissional para `pages/cadastro.html`, chamadas principais para `pages/servicos.html`; verificação: cada link ligado abre a página esperada, sem 404
- [x] 9.2 Remover os itens sem destino nas três páginas: "Baixar App" (×2), Blog, Sobre Nós, Contato, Termos de Uso, Privacidade, Cookies e os 4 ícones sociais; verificação: `grep -c 'href="#"' index.html pages/servicos.html pages/profissionais-prximos.html` retorna 0 nas três (hoje 22, 19 e 19)
- [x] 9.3 Carregar Font Awesome em `pages/profissionais-prximos.html`, que hoje não o carrega apesar de `profissionais.js:201` emitir `<i class="fas fa-user">`; verificação: o ícone de avatar dos cards de resultado passa a renderizar em vez de ficar em branco. **Desvio de convenção registrado:** esta tag entrou no commit `feat` da fase 8 junto do CSS/JS do Leaflet, e não num commit `fix` separado
- [x] 9.4 Confirmar que este commit não carrega nada de `feat`; verificação: `git show --stat` do commit `fix` mostra apenas alterações de `href`, remoção de itens de navegação e a tag do Font Awesome

## 10. Documentação (`feat`)

Arquivos: `CHANGELOG.md` (novo, raiz), `docs/diagrams/fluxo-descoberta-publica.md` (novo).

- [ ] 10.1 Criar `CHANGELOG.md` na raiz seguindo Keep a Changelog, a partir da **v1.1.0**, com as entradas de `Added`, `Changed` e `Removed` desta change; verificação: o arquivo não existia antes (`git log --diff-filter=A -- CHANGELOG.md` mostra este commit) e é a primeira `feat` do repositório, à qual o `config.yaml` atribui essa responsabilidade
- [ ] 10.2 Criar `docs/diagrams/fluxo-descoberta-publica.md` com diagrama Mermaid do fluxo home → catálogo → profissionais próximos → endpoints consumidos; verificação: o diretório `docs/diagrams/` passa a existir e o diagrama está em sintaxe Mermaid, conforme a convenção do projeto

## 11. Verificação de comportamento ponta a ponta

Exige backend no ar e o frontend servido por HTTP. Não há suíte automatizada no frontend — cada item confere contra o que a **API devolve**, não contra a aparência.

- [ ] 11.1 Percorrer o funil completo a partir da home; verificação: escolher categoria e raio, enviar, e chegar à lista de profissionais com a busca já disparada com os dois parâmetros
- [ ] 11.2 Conferir a faixa de preço exibida; verificação: o valor na home é idêntico ao retorno de `GET /servicos-ofertados/estimativa` para a mesma categoria
- [ ] 11.3 Conferir o comportamento de amostra insuficiente; verificação: numa categoria com 1 ou 2 serviços ativos, nenhuma faixa é exibida e aparece a mensagem do backend
- [ ] 11.4 Conferir os depoimentos contra a fonte; verificação: cada depoimento exibido corresponde a uma linha em `AVALIADO` com `comentario_avaliacao` não vazio, e só o primeiro nome do cliente aparece na tela e na resposta da API
- [ ] 11.5 Conferir o estado vazio das áreas integradas; verificação: sem avaliações com comentário, a seção de depoimentos não é renderizada em nenhuma das duas páginas — nem como moldura vazia
- [ ] 11.6 Conferir mapa e contador; verificação: quantidade de marcadores e valor do contador coincidem com o tamanho de `resultados`, e busca sem resultado zera o contador
- [ ] 11.7 Conferir que os filtros filtram no servidor, e não na tela; verificação: escolher nota mínima e faixa de preço produz requisição com `notaMinima`/`precoMin`/`precoMax` na query string, e a resposta da API já vem sem os resultados excluídos — nenhum resultado é apenas ocultado no DOM
- [ ] 11.8 Conferir que a busca sem filtros preserva o comportamento anterior; verificação: a mesma busca de antes da change, sem nota mínima nem faixa de preço, devolve o mesmo conjunto de resultados na mesma ordem
- [ ] 11.9 Conferir o efeito do filtro sobre prestador sem avaliação; verificação: com nota mínima informada, prestador sem `notaMedia` não aparece; removendo o filtro, ele volta a aparecer
- [ ] 11.10 Conferir a vitrine de profissionais novos; verificação: com nota mínima ativa, os prestadores da seção de novos satisfazem categoria, localidade, raio e faixa de preço da busca, e nenhum deles tem nota exibida
- [ ] 11.11 Conferir que não há prestador duplicado entre as duas listas; verificação: nenhum prestador aparece simultaneamente na lista principal e na vitrine de novos, em nenhuma combinação de filtros
- [ ] 11.12 Conferir a recusa da combinação contraditória; verificação: uma requisição manual com `apenasSemAvaliacao=true&notaMinima=4` responde 400 com código `VALIDACAO`
- [ ] 11.13 Conferir que RN04 é preservada nos endpoints novos; verificação: um prestador com KYC `PENDENTE` não influencia categorias, contagem, faixa de preço, depoimentos nem resultados filtrados
- [ ] 11.14 Conferir que nenhum valor de preço é calculado no navegador; verificação: com a aba Network filtrando XHR, toda faixa exibida tem uma requisição correspondente, e `grep -rc "basePrice\|distanceTax" assets/js/` retorna 0
- [ ] 11.15 Conferir a navegação; verificação: nenhum link das três páginas leva a `#` ou a 404, e os itens removidos não deixaram lista vazia nem coluna de rodapé órfã

## 12. Fechamento

- [ ] 12.1 Confirmar que nenhuma migration nem entidade foi tocada; verificação: `git diff --name-only main` não inclui nada em `db/migration/` nem em `Entity/`
- [ ] 12.2 Confirmar que a suíte de backend segue verde; verificação: `mvn test` passa a partir de `backend-estrela-main/`
- [ ] 12.3 Confirmar a separação de commits exigida pela convenção; verificação: `git log --oneline` mostra commits `feat` e `fix` distintos, nenhum commit misturando os dois escopos, todos no imperativo
- [ ] 12.4 Confirmar que `CHANGELOG.md` cobre toda `feat` entregue; verificação: cada endpoint novo e cada substituição de mock aparece em `Added`, `Changed` ou `Removed` da v1.1.0
