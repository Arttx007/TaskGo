## Context

Ver `proposal.md` (seção Why) para a motivação. O que este documento registra é *por que* cada item foi para integração real ou para remoção, porque em vários casos a escolha óbvia é a errada.

Cinco restrições do código atual moldam todo o desenho:

- **`categoria` é texto livre.** Não existe enum, tabela nem endpoint de categorias; é um `VARCHAR(120)` que o prestador digita ao publicar (`V4__servico_ofertado.sql`, índice `idx_servico_ofertado_categoria`). `ServicoOfertadoService.buscar` casa a categoria por igualdade *ignore-case*, sem `LIKE` e sem busca parcial. Consequência: quem digitou "eletricista predial" nunca aparece numa busca por "eletricista".
- **A busca exige categoria e localidade.** `GET /servicos-ofertados/buscar` tem três ramos: com `lat`+`lon` filtra por Haversine; senão com `cidade` casa por igualdade; **senão devolve lista vazia**. Não existe "listar tudo". Isso elimina por construção qualquer carrossel de "profissionais em destaque" na home, onde o visitante ainda não informou nem categoria nem localidade.
- **Avaliação não é entidade.** São duas colunas em `fato_servicos` (`avaliacao`, `comentario_avaliacao`, desde `V5`) mais o agregado `nota_media` em `dim_prestador`. Não há contagem de avaliações por prestador em lugar algum — o `4.9 (124)` do card falso é irreproduzível.
- **`ddl-auto=validate`.** Qualquer alteração de entidade JPA exigiria migration nova, e a aplicação nem sobe se entidade e schema divergirem. Todo o desenho abaixo foi escolhido para **não tocar em entidade nenhuma**.
- **`BuscaServicoResponse` não traz coordenadas.** Traz `distanciaKm`, que serve para ordenar e rotular, mas não posiciona nada num mapa.

## Goals / Non-Goals

**Goals:**

- Deixar cada elemento das três páginas públicas verificável: ou vem da plataforma, ou não está lá.
- Não tocar em entidade, schema ou migration — a change inteira cabe em DTOs, services, controllers e frontend.
- Preservar RN04 em cada endpoint novo, com o mesmo critério que a busca já aplica.
- Registrar por que o grid de `servicos.html` **fica** e por que o carrossel de profissionais **sai**, que é a decisão mais contraintuitiva do conjunto.

**Non-Goals:**

- Reescrever a seleção de candidatos de `ServicoOfertadoService.buscar`. Os três ramos (coordenadas / cidade / vazio) e a ordenação por distância ficam como estão; a busca só ganha três parâmetros opcionais de filtro e dois campos na resposta.
- Introduzir paginação — inclusive agora que a busca filtra por nota e preço. Categorias é lista curta, estimativa é um objeto e avaliações tem teto; a busca continua devolvendo o conjunto inteiro, e paginar exigiria decidir contrato de página, total e ordenação estável.
- Ordenar por nota ou por preço. A ordenação segue sendo por proximidade, como US-03 define.
- Introduzir cache. `ParametroNegocioService` lê sem cache de propósito, e os endpoints novos seguem o mesmo padrão — o volume não justifica, e cache aqui só criaria divergência entre o que o admin vê e o que o visitante vê.
- Padronizar o feedback visual das páginas públicas. Elas usam `alert`/`confirm`, os painéis usam `showToast`. Unificar é refactor.

## Decisions

### O grid de 8 categorias de `servicos.html` fica; o carrossel de 4 profissionais da home sai

Parece incoerente manter um bloco hardcoded e remover outro, mas os dois não são a mesma coisa.

O grid de `servicos.html` **não afirma nada sobre a plataforma**. Cada card já é um `<a>` para `profissionais-prximos.html?service=<slug>`, e `profissionais.js:34-41` lê esse parâmetro e dispara busca real. O grid é *navegação*, não dado — e é a única coisa no produto que dá vocabulário controlado a um campo que no backend é texto livre. Remover o grid para "vir do backend" perderia ícones, descrições e agrupamentos curados, e devolveria uma lista de strings digitadas por prestadores. Pior: sem vocabulário sugerido, a divergência de digitação aumenta.

O carrossel da home **afirma** que Ricardo Santos, Carla Oliveira, Marcos Lima e Juliana Costa existem, estão a 1,2 km e têm 124 avaliações. Nada disso é reproduzível, e "prestadores em destaque" é US-14 (Fase 2). Como a busca exige categoria e localidade, não há nem como fazer esse carrossel de verdade hoje. Por isso ele vira vitrine de categorias: mantém a função de entrada no funil, sem inventar gente.

*Alternativa considerada e rejeitada:* montar o carrossel com N chamadas a `buscarServicos`, uma por categoria conhecida. Além do custo, ainda precisaria da localização do visitante antes de ele pedir qualquer coisa, e continuaria devolvendo prestadores *arbitrários* apresentados como destaque — o que é justamente US-14.

*Alternativa considerada e rejeitada:* substituir o grid de `servicos.html` inteiramente pelo `/categorias`. Rejeitada pelo motivo acima; o desenho adotado é híbrido — grid curado como taxonomia, `/categorias` como fonte de disponibilidade e de categorias fora do grid.

### O grid curado passa a ser reconciliado com o backend, não só decorado

Só anotar contagem resolveria metade do problema. A outra metade é o inverso: categoria que **existe no backend e não está no grid** é hoje inalcançável pela navegação. Então o grid recebe duas informações de `/categorias`: a contagem por card (card com zero fica marcado como indisponível, em vez de levar a uma busca vazia) e um bloco "Outras categorias disponíveis" com o que o backend tem e o grid não previu.

É também o único ponto da change que mede o custo real de `categoria` ser texto livre. Se esse bloco vier cheio de variações grafadas de um mesmo ofício, a conclusão não é mexer no frontend — é que o backend precisa de vocabulário controlado, e isso é outra change.

### A "Estimativa IA" é substituída por preço praticado, e a copy de IA cai junto

Decisão de produto tomada pelo usuário. O ponto técnico é que o dado já existe: `ServicoOfertado.preco` de todos os serviços `ATIVO` de prestador `APROVADO`. Mín/mediana/máx disso é *o preço praticado*, e é uma afirmação verdadeira e útil — diferente da faixa atual, que é `switch` com quatro valores mais `raio × 2,5`.

Duas escolhas dentro dela:

- **Mediana calculada em Java, não em SQL.** Mesmo motivo do Haversine em `GeoService`: `PERCENTILE_CONT` não é portável entre Postgres (dev) e H2 (testes), e o projeto decidiu pagar em memória para manter a query portável. Amostras aqui são pequenas.
- **Amostra mínima de três.** Com um ou dois serviços, "faixa de preço da categoria" é o preço de um prestador identificável — a categoria mais a cidade já quase o identificam. Abaixo de três o endpoint devolve só mensagem. É o mesmo espírito da convenção de resposta vazia que `busca-servicos` já tem: sucesso com mensagem, não erro.

A copy de IA sai porque ela não estava só no formulário: são ~30 trechos em 4 páginas. Deixar "Previsibilidade Total com IA" e o selo "IA Verificada" no ar depois de remover o cálculo transformaria uma mentira concreta numa mentira vaga.

*Alternativa considerada e rejeitada:* manter o rótulo "IA" sobre a faixa real, argumentando que estatística é "inteligência". Rejeitada: é exatamente o eufemismo que a change existe para eliminar.

### Depoimentos vêm de avaliação real, com privacidade decidida no backend

O endpoint devolve nota, comentário, categoria, cidade, data e **só o primeiro nome** do cliente. A alternativa — devolver `clienteNome` inteiro — expõe nome completo de pessoa física numa rota sem autenticação, para colher um depoimento. O recorte é feito no backend, não no frontend, porque privacidade que depende do consumidor não é garantia: uma rota pública que devolve nome completo já vazou, independentemente do que a página renderize.

Só entram avaliações **com comentário não vazio** — nota sem texto não é depoimento, e renderizaria um card vazio. Lista vazia faz o frontend **esconder a seção**, em vez de mostrar moldura sem conteúdo.

Rota nova em controller novo (`/avaliacoes`), não em `FatoServicoController` (base `/servicos`), porque `/servicos/**` é território autenticado e por dono; pendurar uma rota pública ali convida a erro de configuração no `SecurityConfig`. Prefixo próprio deixa o `permitAll` explícito.

### Contagem de categorias por `@Query`, não carregando listas

`GET /servicos-ofertados/categorias` agrega com `GROUP BY` no repository. Será o **primeiro `@Query` do projeto** — hoje só há derived queries. A alternativa seria carregar os serviços ativos e agrupar em Java, no mesmo estilo do Haversine, mas `backend-estrela-main/CLAUDE.md` é explícito na direção oposta para rotas de contagem: agregue via repository (`count`, `countByX`) em vez de carregar listas em memória. Agrupar por categoria é contagem, não geometria; o argumento de portabilidade que justifica o Haversine em memória não se aplica a um `GROUP BY`, que é ANSI e roda igual em Postgres e H2.

### Coordenadas arredondadas na busca, para o mapa poder ser real

`BuscaServicoResponse` ganha `latitude`/`longitude`. É campo aditivo em endpoint público que já existe: nenhum consumidor quebra, e **nenhuma migration é necessária** porque `dim_localizacao` já tem as colunas desde `V3__localizacao_geo.sql`. Nulos quando o serviço não tem coordenadas — caso que a busca já trata (esses serviços passam pelo filtro de raio sem serem descartados).

**Arredondamento para 3 casas decimais (~110 m)** é a decisão que importa. O mapa precisa mostrar *onde mais ou menos*; devolver a coordenada cheia de um autônomo numa rota sem autenticação publica, na prática, o endereço residencial de quem trabalha em casa. 110 m situa no bairro e na quadra sem apontar a porta. O arredondamento é feito no backend, pelo mesmo motivo do primeiro nome nos depoimentos.

Como `distanciaKm` continua sendo calculada com a coordenada cheia, a ordenação por proximidade não perde precisão — só a posição exibida é aproximada.

⚠️ `FluxoCompletoIntegrationTest` cobre a busca de US-03 e é o primeiro teste a quebrar quando um contrato de endpoint muda. `mvn test` faz parte da tarefa, não da verificação final.

### Nota e preço passam a ser filtro de servidor, não refino de tela

Decisão tomada pelo usuário, contra a alternativa mais barata. `GET /servicos-ofertados/buscar` ganha `notaMinima`, `precoMin` e `precoMax`, todos opcionais.

O que decidiu: filtro que só esconde linhas no navegador está certo por acidente. Enquanto o resultado cabe na tela, os dois comportamentos são indistinguíveis; no momento em que a base cresce e a busca devolve muito mais do que o visitante examina, "nota mínima 4,5" passa a significar "os 4,5+ entre os que por acaso vieram", e a interface continua parecendo correta. Como o critério vive no servidor, ele vale sobre o conjunto buscado e não sobre o recorte acidental — e é isso que a spec passa a exigir.

Três escolhas dentro dela:

- **Parâmetros opcionais, não obrigatórios.** Busca que não os informa comporta-se exatamente como hoje. É o que mantém a mudança aditiva e o que a spec fixa num cenário próprio.
- **Filtragem no mesmo passo em Java que já filtra KYC e distância**, não numa query nova. `ServicoOfertadoService.buscar` já materializa a lista de candidatos para calcular Haversine e já descarta prestador não aprovado em memória; acrescentar dois predicados nesse mesmo passo não adiciona carga alguma. A alternativa — um `@Query` com parâmetros anuláveis (`:precoMin is null or s.preco >= :precoMin`) — teria de reproduzir a seleção de três ramos (coordenadas / cidade / vazio) dentro de JPQL, duplicando em SQL uma lógica que hoje é legível em Java. Trocaria clareza por uma eficiência que, sem paginação, não se realiza.
- **Prestador sem nota média é descartado quando `notaMinima` vem.** `nota_media` é nula até a primeira avaliação, e nulo não satisfaz "pelo menos 4". Incluí-lo seria afirmar que ele alcança um mínimo que ninguém mediu; excluí-lo penaliza prestador novo. A segunda é a leitura honesta do filtro, e a spec registra as duas faces em cenários separados — com o filtro omitido, o prestador sem nota volta a aparecer.

O slider de raio já se comportava assim: refaz a busca porque `raioKm` é parâmetro da API (`profissionais.js:46-63`, debounce de 400 ms). Com esta decisão os três controles do painel passam a ser da mesma natureza, o que **elimina** a incoerência de o painel misturar um controle de servidor com dois de tela — e torna desnecessário separá-los visualmente.

*Alternativa considerada e rejeitada:* filtrar no navegador os resultados já devolvidos, rotulando que o refino se aplica aos resultados atuais. Mais barata e sem mexer em contrato público, mas depende de um rótulo para não enganar, e o rótulo é justamente o tipo de ressalva que ninguém lê.

*Alternativa considerada e rejeitada:* ordenar também por nota ou preço no servidor. Fora de escopo — a ordenação segue por distância, como US-03 define. Filtrar e ordenar são decisões separadas, e misturá-las alargaria a change.

### O prestador sem avaliação ganha vitrine própria, não exceção dentro do filtro

Filtrar por nota no servidor cria o impasse de partida do marketplace: o prestador recém-aprovado não consegue a primeira avaliação porque não aparece, e não aparece porque não tem avaliação. Como o filtro agora vale sobre o universo buscado e não sobre o recorte que veio para a tela, ignorar isso o tornaria invisível de verdade.

A solução adotada é uma área separada — "Novos na sua região" — alimentada por uma segunda busca com `apenasSemAvaliacao` verdadeiro, herdando categoria, localidade, raio e faixa de preço da busca corrente. O filtro de nota continua significando exatamente o que diz, e o prestador novo aparece em um lugar onde a ausência de nota é a premissa, não um defeito.

Três escolhas dentro dela:

- **A vitrine só aparece quando o filtro de nota excluiu alguém.** Sem filtro de nota ativo, os prestadores sem avaliação já estão na lista principal, e exibir a vitrine os mostraria duas vezes. A regra evita a duplicação sem precisar comparar conjuntos no cliente: se não há filtro de nota, não há segunda busca.
- **`apenasSemAvaliacao` junto de `notaMinima` é 400, não precedência.** Os dois critérios se contradizem. Definir que um vence significaria descartar em silêncio um filtro que o cliente enviou — exatamente o comportamento que esta change existe para eliminar. Recusar é mais barato de entender e impossível de confundir.
- **Nenhuma nota é exibida na vitrine.** Prestador sem avaliação não recebe estrela, nota nem contagem. É a mesma regra que fez os 4 cards falsos da home saírem, aplicada a um caso em que a tentação de preencher é maior.

*Alternativa considerada e rejeitada:* incluir o prestador sem nota no próprio resultado filtrado, com um marcador "novo" e ordenado ao fim. Mais simples, mas devolve ao cliente que pediu 4,5+ alguém sem nota alguma — quebra o filtro para salvar o prestador, e ainda contraria a ordenação por proximidade que US-03 define.

*Alternativa considerada e rejeitada:* nota média provisória com prior bayesiano, em que o prestador sem avaliação herda a média da plataforma e converge para a própria. É a solução estruturalmente correta, e resolveria de graça um defeito que já está no ar — `FatoServicoService.recalcularNotaMedia` faz média aritmética simples, então uma única nota 5,0 ordena acima de cinquenta notas 4,8. Rejeitada aqui porque `nota_media` deixaria de ser a média que o requisito atual de `solicitacao-servico` descreve, e mudaria o número que prestadores já veem hoje: é decisão de produto sobre reputação, não detalhe de implementação de uma change de mocks.

*Alternativa considerada e rejeitada:* apenas informar quantos foram ocultados, com um botão para incluí-los na lista principal. Barato e honesto, mas dá ao prestador novo visibilidade condicionada a um clique que quase ninguém dá — a vitrine própria é destaque, o contador é rodapé.

**Custo assumido:** uma segunda chamada à busca sempre que o filtro de nota estiver ativo. É aceitável porque acontece só nesse caso, e porque a busca não pagina — a segunda chamada percorre a mesma lista de candidatos que a primeira já percorreu.

### "Escala do serviço" e "Portfólio em Destaque" saem, por motivos diferentes

"Escala do serviço" sai porque **não há o que ligar**: nenhum campo do modelo distingue reparo rápido de obra, e não é US-19 (que é split de pagamento para equipe, Fase 3). Seu handler atual só repinta o botão e reescreve o rótulo via `innerText`, destruindo o `<span>` interno. Bônus de remover: o select é `required` e hoje bloqueia o envio da busca sem oferecer nada em troca.

"Portfólio em Destaque" sai porque é **US-16, Fase 2** — não há campo, tabela nem rota de foto de trabalho em nenhum lugar do backend; o único upload existente é KYC, e é visível apenas a administradores. Implementá-lo seria antecipar Fase 2 dentro de uma change de correção de mocks.

### Mapa real com Leaflet, e o botão de alternância passa a significar algo

Leaflet por CDN já é dependência de fato do projeto (`pages/painel-cliente.html:10-11`), então não é dependência nova — é o mesmo padrão numa página a mais. Com coordenadas reais nos resultados, o mapa deixa de ser foto e "Alternar para Lista" deixa de ser decorativo: passa a alternar entre duas visões do **mesmo** conjunto de resultados.

"12 Profissionais Ativos" passa a ser a contagem real dos resultados. **"5 min Tempo Médio de Resposta" é removido**, não corrigido: a plataforma não registra quando o prestador respondeu. Existe `solicitacao.prazo-resposta-horas` em `parametro_negocio`, mas é um *prazo* configurado, não uma média medida — usá-lo como "tempo médio" trocaria um número inventado por outro.

### Links mortos: ligar, remover, e não inventar página

Três destinos: os que existem são ligados (Serviços Populares → busca por categoria, Como Funciona → `pages/como-funciona.html`, Seja um Profissional / Criar Conta Profissional → `pages/cadastro.html`, chamadas principais → `pages/servicos.html`); os que anunciam produto inexistente são removidos ("Baixar App" é US-21/Fase 3, Blog nunca existiu); e os institucionais/legais (Sobre Nós, Contato, Termos, Privacidade, Cookies) também são removidos.

Sobre os legais especificamente: a tentação é criar páginas com texto de preenchimento, e isso é pior que não tê-las — documento jurídico placeholder é passivo, não recurso. Remover o link é a ação honesta; escrever o documento é outra change, com outro tipo de revisão.

Os 4 ícones sociais viram remoção por não haver perfil conhecido para apontar.

### `api.js` continua sendo o único lugar com `fetch`

Três funções novas (`listarCategorias`, `obterEstimativa`, `listarAvaliacoesRecentes`), todas com `auth: false`, como `buscarServicos` já faz. É invariante do projeto e a change não a arranha.

Consequência de ordem de carregamento: `index.html` e `pages/servicos.html` **não carregam `api.js` hoje**, então precisam da tag antes do script da página. Todos os scripts do projeto são clássicos, e `defer` executa na ordem do documento — então `api.js` com `defer` antes do script da página funciona. O que **não** pode é `api.js` com `defer` junto de um eventual `window.TASKGO_API_BASE_URL` inline, porque `api.js` captura a base URL num `const` no momento em que o IIFE roda.

## Risks / Trade-offs

- **A remoção de seções encurta visivelmente as páginas públicas.** Saem o portfólio, os 4 cards de profissionais e vários itens de rodapé. → Mitigação: os dois blocos maiores são *substituídos*, não apagados — categorias no lugar do carrossel, avaliações reais no lugar dos depoimentos. A perda líquida concentra-se no portfólio, que é Fase 2.
- **As páginas ficam dependentes do backend para renderizar áreas que hoje sempre aparecem.** Backend fora do ar deixa a home sem vitrine e sem depoimentos. → Mitigação: cada área tem estado vazio definido em spec (esconder a seção, não mostrar moldura vazia), e o grid curado de `servicos.html` continua renderizando sem rede — a navegação por categoria sobrevive à queda da API.
- **O contrato de um endpoint público em uso muda duas vezes na mesma change** — dois campos na resposta e três parâmetros na entrada. → Mitigação: as duas alterações são aditivas e há cenário de spec fixando que busca sem os parâmetros novos produz o resultado de antes. `FluxoCompletoIntegrationTest` cobre US-03 e é o canário; ele roda na mesma tarefa que altera o contrato, não depois.
- **Filtrar em memória não escala.** Sem paginação, a busca materializa todos os candidatos da categoria e só então descarta por nota e preço — trabalho jogado fora que cresce com a base. → Mitigação: é a carga que `buscar` já paga hoje para calcular Haversine, então a change não a aumenta. Quando paginação entrar, filtro e paginação devem descer juntos para a query; até lá, mover só o filtro para SQL duplicaria a seleção de três ramos em JPQL sem ganho realizável.
- **Prestador novo desaparece da lista principal em qualquer busca com nota mínima**, por não ter `nota_media`. → Mitigação: é a leitura correta do filtro, e a vitrine "Novos na sua região" existe exatamente para compensá-la — aparece quando o filtro exclui alguém, herdando os demais critérios da busca.
- **A vitrine de novos é uma superfície nova numa página que a change já altera muito.** Mais markup, mais um estado vazio, mais uma chamada de rede. → Mitigação: ela reusa o mesmo renderizador de card dos resultados e o mesmo endpoint de busca, sem componente nem rota nova; e a change está *removendo* duas seções dessa mesma página (portfólio e depoimentos falsos), então o saldo de markup é negativo.
- **A regra "vitrine só com filtro de nota ativo" é uma condição fácil de implementar errado**, e implementada errado produz prestador duplicado na tela. → Mitigação: há cenário de spec para os dois lados, e a verificação manual confere explicitamente que nenhum prestador aparece nas duas listas.
- **Coordenada arredondada ainda é informação de localização de pessoa física.** 110 m não anonimiza; aproxima. → Mitigação: é o mínimo necessário para a função, e a alternativa de não ter mapa foi oferecida ao usuário e recusada. Se o produto passar a ter prestador atendendo em residência própria como caso dominante, o raio de arredondamento deve ser revisto — decisão de negócio, não de implementação.
- **A amostra mínima de três esconde faixa em categoria nova.** Em base pequena, quase toda categoria cai nesse caso e a home mostra "ainda não há preços suficientes". → Mitigação: é o comportamento correto, e é falsificável — a mensagem diz a verdade sobre o estado da plataforma. O `switch` atual escondia esse mesmo vazio com números plausíveis.
- **O primeiro `@Query` do projeto abre precedente.** → Mitigação: está confinado a uma agregação de contagem, que é exatamente onde o `CLAUDE.md` do backend manda agregar via repository. Não vira licença para mover regra de negócio para JPQL.
- **`FluxoCompletoIntegrationTest` pode quebrar pela mudança em `BuscaServicoResponse`.** → Mitigação: `mvn test` está dentro da tarefa que altera o DTO, não numa fase de verificação posterior. Se quebrar por asserção de campos, o ajuste é no teste; se quebrar por outro motivo, a premissa de que a mudança é aditiva caiu.
- **Não há teste automatizado no frontend.** A maior parte desta change é frontend. → Mitigação: o roteiro de verificação confere contra o que a API devolve (comparando a faixa exibida com o retorno de `/estimativa`, a contagem com o tamanho de `resultados`), não contra a aparência.

## Migration Plan

Não há migração de dados nem de schema — nenhuma entidade JPA muda e não existe `V8`.

1. **Backend primeiro, e sozinho.** Os quatro endpoints são aditivos: implantados antes do frontend, não alteram nada do que está no ar. `BuscaServicoResponse` com dois campos a mais é ignorado por um frontend que não os lê.
2. **`api.js` e as tags de script.** Sem efeito visível; só disponibiliza as funções.
3. **Uma página por vez**, em ordem de risco crescente: `index.html`, `pages/servicos.html`, `pages/profissionais-prximos.html`. Cada uma é independente das outras.
4. **Links mortos e Font Awesome** em commits `fix` separados dos de `feat`, por convenção do projeto.

**Rollback:** reverter o commit da página afetada. Como o frontend é estático e sem build, reverter o HTML/JS restaura o comportamento anterior imediatamente; os endpoints novos podem ficar no ar sem consumidor, já que nenhum deles altera estado. O único passo com efeito sobre um contrato existente é o item 1 (`BuscaServicoResponse`), e ele é aditivo — não há o que reverter no cliente.

## Open Questions

- **Qual o teto e o padrão de `limite` em `/avaliacoes/recentes`.** A spec exige que exista padrão e teto, e que limite acima do teto seja truncado em vez de recusado; os números concretos (a proposta parte de 6 e 20) podem ser ajustados na implementação sem mexer em spec, approach ou tarefas.
- **Se as categorias fora do grid curado devem aparecer sempre ou só acima de um mínimo de serviços.** Depende do que a base real mostrar quando o bloco for exibido pela primeira vez. Não altera contrato de endpoint nem spec — o `/categorias` devolve a contagem, e a decisão é de apresentação.
