# Changelog

Todas as mudanças relevantes deste projeto são registradas neste arquivo.

O formato segue [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/),
e o versionamento segue [Semantic Versioning](https://semver.org/lang/pt-BR/).

## [Não publicado]

Esta entrada cobre o painel do cliente, que era a maior superfície decorativa que restava: carteira
com saldo, cartão de crédito, checkout que aprovava por temporizador, chat com resposta enlatada,
assistente virtual, recibo com identificador sorteado e quatro profissionais inventados. O que o
backend não sabia responder foi construído; o que não deveria existir foi removido.

### Adicionado

- Estado `EM_ANDAMENTO` na solicitação, entre `ACEITO` e `CONCLUIDO`. Antes, "aceito" e "o
  profissional está aqui trabalhando" eram o mesmo estado, e nenhuma das duas telas conseguia
  distingui-los. `RelatorioController` passa a contá-lo, para que uma solicitação em atendimento não
  fique fora de todos os buckets.
- Código de confirmação de quatro dígitos, gerado com `SecureRandom` no aceite e devolvido **apenas
  ao cliente**. `PUT /servicos/{id}/iniciar` exige o código correto, o estado `ACEITO` e o pagamento
  já retido em custódia — é a prova de que o profissional chegou ao local.
- Quatro momentos em `fato_servicos` (`criadoEm`, `aceitoEm`, `iniciadoEm`, `concluidoEm`). A única
  noção temporal do modelo era `dim_tempo`, uma dimensão de *dia*, que não sustenta timeline alguma.
- `GET /clientes/me` e `PUT /clientes/me` — leitura e atualização do próprio perfil, com `telefone` e
  `fotoUrl` novos em `dim_cliente`. `PUT /clientes/me/foto` reusa a validação de tipo e tamanho do
  KYC.
- `DELETE /clientes/me` — desativação da própria conta, recusada com 409 enquanto houver solicitação
  em `SOLICITADO`, `ACEITO` ou `EM_ANDAMENTO`. `AuthService` passa a recusar login de conta inativa.
- Entidade `EnderecoCliente` e o CRUD `/clientes/me/enderecos`, com a invariante de **um único
  endereço padrão por cliente** garantida ao gravar. `dim_localizacao` não servia: não tem rua,
  número ou CEP, e não pertence a cliente algum. A solicitação passa a apontar para o endereço
  opcionalmente, e a remoção é lógica — solicitação que já referencia o endereço continua válida.
- `GET /clientes/me/pagamentos` — extrato do cliente, devolvendo **a taxa persistida no momento do
  pagamento**, nunca recalculada: alterar `taxa.fixa` não reescreve lançamento antigo.
- `/clientes/me/favoritos` (listar, marcar, remover). A listagem traz a contagem de serviços ativos e
  sinaliza indisponibilidade quando o prestador não está `APROVADO`, sem tirá-lo da lista.
- `GET /prestadores/{id}/servicos-ofertados` — catálogo de um prestador. Rota **autenticada**, e
  devolve lista vazia para prestador não aprovado, preservando RN04 sem revelar o estado de
  verificação.
- `GET /clientes/me/notificacoes` — avisos apurados do estado, sem tabela e sem marcação de leitura:
  o aviso desaparece quando o fato que o originou é resolvido.
- Mensagens da solicitação (`GET`/`POST /servicos/{id}/mensagens`, `PUT .../lidas`), restritas às
  duas partes. Implementadas uma vez em `assets/js/chat-servico.js` e usadas pelos dois painéis.
- Vinte funções novas em `assets/js/api.js`, que segue sendo o único arquivo do frontend com `fetch`.

### Alterado

- **Taxa de cancelamento.** Cancelar deixa de estornar sempre integralmente: quando **o cliente**
  desiste de um atendimento **já iniciado** e o prazo de carência passou, parte do valor pago é
  retida e **creditada ao prestador**, que já se deslocou e começou o trabalho. O percentual depende
  da distância entre o endereço do atendimento e a localização do prestador (`taxa-percentual-perto`
  / `taxa-percentual-longe`, separados por `limiar-distancia-km`), tem teto em `taxa-teto` e nunca
  excede o valor pago; distância não apurável aplica o percentual menor. Nos demais casos —
  cancelamento pelo prestador, ou pelo cliente antes do início ou dentro da carência — o estorno
  segue integral. As cinco chaves ficam em `parametro_negocio`, ajustáveis por `/admin/parametros`.
  O painel avisa quanto será retido e quanto volta **antes** de o cliente confirmar.
- `ESTORNADO_PARCIAL` em `StatusPagamento`, com `valorEstornado` e `valorTaxaCancelamento`
  persistidos: os dois valores ficam no registro, e o extrato os apresenta separados em vez de
  mostrar um estorno integral que não aconteceu.
- Concluir passa a exigir `EM_ANDAMENTO`. `ACEITO` → `CONCLUIDO` deixa de existir: não havia como o
  prestador provar que esteve no local.
- Cancelar a partir de `EM_ANDAMENTO` estorna. A guarda `if (status == ACEITO)` do estorno deixaria
  dinheiro retido em custódia justamente no estado em que ele existe.
- No painel do cliente, cancelar passa a ser oferecido também em `EM_ANDAMENTO` — antes o botão
  desaparecia exatamente quando havia valor retido.
- No painel do prestador, `ACEITO` deixa de ser rotulado "Em Andamento" (passa a "Aguardando
  início") e a ação de concluir sai de `ACEITO` para `EM_ANDAMENTO`, atrás do código de confirmação.
- Busca, favoritos, extrato, perfil, endereços, avisos, chat e acompanhamento do painel do cliente
  passam a consumir a API real. As tags de categoria vêm de `/categorias`; a busca livre reconcilia o
  texto digitado contra as categorias que existem e, quando nada casa, apresenta as disponíveis em
  vez de devolver vazio silencioso.
- A localidade do cabeçalho passa a ser a geolocalização concedida, ou o endereço padrão do cliente
  quando a permissão é negada, em vez de "São Paulo, SP" fixo.
- O simulador de custo passa a consultar `/estimativa`; categoria com amostra menor que três exibe a
  mensagem da plataforma e **nenhum** valor.
- A timeline do acompanhamento passa a ser montada dos momentos reais, e etapa não cumprida aparece
  como não cumprida. O mapa mostra apenas o endereço do atendimento.
- `dim_cliente` ganha `uk_dim_cliente_email`, alinhando-se a `dim_prestador`.

### Removido

- Carteira "Saldo TaskGO Pay" e a ação "Adicionar Saldo" — não existe saldo de cliente no modelo, nem
  recarga, nem rota que as sustente.
- Cartão de crédito mockado, o bloco "Adicionar Cartão" e o modal de captura com animação de
  validação bancária. Não há PSP: o pagamento é escrow mockado, e capturar número de cartão numa tela
  que não integra com adquirente é pedir dado sensível sem destino.
- Checkout simulado (`abrirCheckout`, `processarPagamento`, `irParaCheckoutSimulado`), que aprovava
  pagamento por `setTimeout` e somava R$ 4,50 de taxa na página. `abrirPagamentoReal` é o único
  caminho de pagamento.
- Seção "Profissionais em Destaque" e os quatro profissionais inventados, substituídos pelo grid de
  resultados da busca. Destacar prestador é US-14, fora do escopo.
- Assistente virtual (modal, envio e resposta gerada por temporizador) e o banner que o abria. O FAQ e
  os artigos de ajuda permanecem, e o campo de busca da ajuda passa a filtrar o FAQ de fato.
- Chat com resposta enlatada ("Estou indo para o local agora mesmo") e o `bancoDeConversas` em
  memória, substituídos pela conversa real.
- Preferências de notificação, 2FA, "Alterar Senha" e "Sessões Ativas" — nenhuma tem campo, tabela ou
  rota. Listar sessões inventadas em uma tela de segurança é pior que não listar nenhuma.
- Recibo decorativo: identificador sorteado com `Math.random()`, taxa fixa de R$ 4,50 subtraída na
  página, cartão "final 4242", string de autenticação constante e o botão "Baixar Comprovante PDF",
  que não baixava nada. O comprovante passa a ser montado dos valores do extrato.
- Marcador do profissional no mapa, linha entre os pontos e indicador de ETA ("10 min, 2.4 km"): a
  plataforma não recebe a posição do prestador, então não havia o que rastrear.
- Ação "Ligar", o filtro "Últimos 30 dias" e "Marcar todas como lidas", que não tinham efeito, e a
  referência morta a `#notifCountBadge`.
- Nome de pessoa fixo no `<title>`, no cabeçalho e no formulário de perfil.

### Corrigido

- **Login respondia 500 `ERRO_INTERNO` em vez de 401 quando havia e-mail duplicado em
  `dim_cliente`.** `dim_cliente` não tinha restrição de unicidade em `email` (ao contrário de
  `dim_prestador`), então duas contas podiam nascer com o mesmo endereço; o `Optional<Cliente>
  findByEmail` do `AuthService` estourava com múltiplas linhas. Efeito no ar: a conta ficava
  impossível de logar, e o usuário via falha do servidor em vez de credencial inválida. A restrição
  `uk_dim_cliente_email` (`V8`) fecha a porta. `V8` **não** deduplica por conta própria: em base com
  duplicado a migration falha, e isso é o comportamento desejado — a escolha de qual linha manter não
  é do código.
- Conteúdo de mensagem passa a ser inserido no histórico do chat como nó de texto, não por
  `innerHTML +=`. Como o texto é escrito pela outra parte da conversa, a concatenação era um vetor de
  injeção: `<img src=x onerror=...>` executava no navegador de quem lia.


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
