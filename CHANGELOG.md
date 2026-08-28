# Changelog

Todas as mudanças relevantes deste projeto são registradas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e o versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]

## [1.1.0] - 2026-08-28

Primeira versão registrada neste changelog. O MVP (US-01 a US-10) é anterior a ele e está
documentado em `spec.md`, `plan.md` e nas capabilities sob `openspec/specs/`.

Esta versão remove das páginas públicas os elementos que aparentavam funcionalidade sem ter, e
substitui por integração real o que o backend já sabia responder.

### Adicionado

- `GET /servicos-ofertados/categorias` — categorias com serviço disponível ao público e a contagem
  de cada uma, agregadas no banco. Rota pública.
- `GET /servicos-ofertados/estimativa?categoria=X` — faixa de preço realmente praticada na
  categoria (mínimo, mediana, máximo e tamanho da amostra). Amostra menor que três não devolve
  faixa, apenas mensagem, para não revelar o preço de um prestador identificável. Rota pública.
- `GET /avaliacoes/recentes?limite=N` — avaliações reais para a prova social do site, identificando
  quem avaliou apenas pelo primeiro nome. Rota pública.
- `GET /servicos-ofertados/buscar` passa a aceitar `notaMinima`, `precoMin`, `precoMax` e
  `apenasSemAvaliacao`, todos opcionais, aplicados pelo servidor.
- Coordenadas aproximadas (`latitude`/`longitude`, arredondadas para três casas decimais) em cada
  resultado da busca, para que o mapa possa ser real.
- Mapa Leaflet de verdade na lista de profissionais, plotando os resultados da busca.
- Vitrine "Novos na sua região", exibida quando o filtro de nota mínima exclui prestadores ainda não
  avaliados, para que prestador novo não fique invisível.
- Bloco "Outras categorias disponíveis" no catálogo, revelando categorias que existem no backend e
  não estão no grid curado — antes, quem publicava em categoria não prevista era inalcançável.
- `ValidacaoException`, mapeada para 400 `VALIDACAO`, para recusar combinação inconsistente de
  parâmetros de consulta.
- Funções `listarCategorias`, `obterEstimativa` e `listarAvaliacoesRecentes` em
  `assets/js/api.js`, que segue sendo o único arquivo do frontend com `fetch`.

### Alterado

- A "Estimativa IA" da home deixa de calcular preço no navegador e passa a exibir a faixa praticada
  vinda do backend. O formulário volta a submeter para a busca real, que o `preventDefault()` do
  cálculo falso impedia de alcançar.
- O carrossel da home deixa de exibir quatro profissionais inventados e passa a apresentar as
  categorias reais disponíveis. Destacar prestador é US-14, fora do escopo atual.
- Os depoimentos das páginas públicas passam a vir de avaliações reais; sem avaliação com
  comentário, a seção não é exibida.
- Os cards do catálogo passam a exibir a contagem real de profissionais por categoria, e card sem
  oferta deixa de ser link em vez de levar a uma busca vazia.
- O campo de busca do catálogo passa a levar à busca real, mantendo o filtro do grid ao digitar.
- As estrelas de nota mínima e a faixa de preço passam a filtrar de verdade, no servidor; "Aplicar
  Filtros" refaz a busca em vez de recarregar a página e apagar os resultados.
- O contador de profissionais da lista passa a refletir o resultado da busca.
- O botão "Alternar para Lista" passa a alternar de fato entre mapa e lista.
- Toda a copy que atribuía preço a inteligência artificial (cerca de 30 trechos em três páginas) foi
  reescrita para preço praticado. O produto não tem nenhuma dependência de IA.
- O raio escolhido na home passa a ser aplicado na busca; antes era descartado.

### Removido

- Seção "Portfólio em Destaque" da lista de profissionais — portfólio de fotos é US-16, Fase 2, e
  não existe campo, tabela ou rota que o sustente.
- Select "Escala do serviço", que apenas repintava o botão de busca e bloqueava o envio por ser
  obrigatório, sem corresponder a nenhum critério do modelo.
- Indicador "5 min Tempo Médio de Resposta", que era fixo no HTML: a plataforma não registra quando
  o prestador respondeu, e trocar um número inventado por outro não seria correção.
- Sessenta links `href="#"` das três páginas públicas. Os que tinham destino real foram ligados; os
  que anunciavam o que não existe (aplicativo, blog, páginas institucionais e legais) foram
  removidos, junto dos contêineres que ficaram vazios.

### Corrigido

- Parâmetro de consulta obrigatório ausente respondia **500 `ERRO_INTERNO`** em vez de 400: o
  `@ExceptionHandler(Exception.class)` do `GlobalExceptionHandler` engolia a exceção do Spring.
  Efeito no ar: `GET /servicos-ofertados/buscar` sem `categoria` aparecia como falha do servidor
  numa rota pública que qualquer visitante alcança.
- Font Awesome não era carregado na lista de profissionais, apesar de os cards de resultado
  emitirem ícones `fas` — o avatar renderizava em branco.

[1.1.0]: https://github.com/Arttx007/TaskGo/releases/tag/v1.1.0
