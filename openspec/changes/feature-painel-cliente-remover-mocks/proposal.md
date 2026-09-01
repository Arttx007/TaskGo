## Why

O Painel do Cliente é a tela onde o cliente passa o tempo depois de criar conta — e dela, **três fluxos são reais**: "Meus Pedidos", o pagamento do pedido e a avaliação. Todo o resto é markup fixo ou `setTimeout` que devolve confirmação de sucesso sem tocar em servidor algum.

`assets/js/painel-cliente.js` tem 603 linhas e consome **4 das ~35 funções de `assets/js/api.js`**: `listarSolicitacoes`, `cancelarSolicitacao`, `criarPagamento` e `enviarAvaliacao`. Enquanto isso, `buscarServicos`, `listarCategorias`, `obterEstimativa` e `criarSolicitacao` já existem, são públicos, funcionam e foram integrados nas páginas públicas pela change anterior — mas o painel do cliente não chama nenhum deles. A aba "Buscar Serviços" tem um campo de busca **sem `id` e sem handler** (`pages/painel-cliente.html:953`), quatro tags de filtro **sem `onclick`** (`:956-961`), e quatro "Profissionais em Destaque" inventados (`:966-1021`) — nomes, fotos de `pravatar.cc`, notas e contagens de serviço que o backend não sabe calcular. O botão "Encontrar" dispara um toast e nada mais.

O "Simular Contratação" é o caso mais direto: `painel-cliente.js:524-549` tem quatro preços fixos num `setTimeout(1800)` e apresenta o resultado como "Média na sua Região", acompanhado de "3 disponíveis perto de você" escrito no HTML. É a mesma promessa que a change anterior removeu da home — dessa vez dentro da área autenticada, onde o cliente já confia na plataforma. E o endpoint que responde isso de verdade, `GET /servicos-ofertados/estimativa`, foi criado por aquela change e está sem uso aqui.

Três telas mentem sobre dinheiro e sobre segurança:

- **Pagamentos** exibe saldo "R$ 240,00" (`:1046`), um cartão terminado em 4242 (`:1053`) e três transações inteiras no HTML (`:1087-1154`), com datas, método e comprovante. O modal de recibo gera o número do comprovante com `Math.random()` e **subtrai R$ 4,50 do valor para fabricar o subtotal** (`painel-cliente.js:403-405`). "Adicionar Cartão" anima por 2 segundos e declara sucesso sem persistir nada — e, por colisão de `id`, digitar no modal **destrói o cartão exibido na aba** (`:1053-1056` contra `painel-cliente.js:414-429`).
- **Configurações** tem formulário de perfil com `value` fixo (`:1272`), dois endereços no HTML, toggle de 2FA, e "Sessões Ativas" onde encerrar a sessão do outro dispositivo é apenas remover o elemento do DOM. "Salvar Alterações" exibe um toast de sucesso e não faz requisição alguma. O botão "Excluir Minha Conta" não tem handler nenhum.
- **Em Andamento** mostra um PIN de segurança **`5923` fixo no HTML** (`:1438`), ETA "10 min / 2.4 km", placa de veículo e uma timeline com horários fixos "14:05" e "14:35". A própria FAQ da página (`:1533`) explica ao cliente como usar esse PIN. O backend não tem coluna, entidade ou serviço de PIN, e `fato_servicos` **não tem um único timestamp**.

Fecha o conjunto o assistente de IA, que se declara "em demonstração" numa resposta enlatada (`painel-cliente.js:598`) — o `pom.xml` não tem nenhuma dependência de IA, cliente HTTP ou LLM; o chat com o profissional, que devolve uma frase fixa depois de 2,5 segundos; os dois favoritos fixos cuja remoção só apaga o card da tela; e o sino de notificação com badge **"2"** escrito no HTML (`:932`), cujo "marcar todas como lidas" referencia `#notifCountBadge`, elemento que não existe na página.

Esta change é a continuação direta de `2026-08-28-feature-publico-remover-mocks`, com o mesmo princípio: **cada item ou passa a vir da plataforma, ou sai da tela.**

## What Changes

Cada um dos oito itens ganha decisão explícita. Nenhum permanece como está.

**Integração real sem backend novo** — as duas abas mais visíveis são resolvidas com endpoints que já existem:

- **Buscar Serviços**: as tags de filtro vêm de `GET /servicos-ofertados/categorias`; o campo de busca reconcilia o texto digitado contra essas categorias e dispara `GET /servicos-ofertados/buscar`; "Contratar" chama `POST /servicos`. A localização vem da geolocalização do navegador, com fallback para o endereço padrão do cliente em vez do "São Paulo, SP" fixo do cabeçalho (`:916`).
- **"Profissionais em Destaque" sai como conceito**, não só como markup. A busca exige categoria e localidade — não existe "listar em destaque" no backend, e apresentar resultados arbitrários como destaque é US-14, Fase 2. A seção passa a ser o grid de resultados da busca, com estado vazio quando não há o que buscar. É a mesma decisão que a change anterior tomou no carrossel da home.
- **Simular Contratação**: categorias de `/categorias`, faixa de preço de `GET /servicos-ofertados/estimativa`. Quando a amostra é insuficiente, a interface diz isso — o endpoint já devolve `mensagem` para esse caso. A contagem de profissionais passa a ser a contagem real de resultados, ou é omitida.

**Endpoints novos.** Dezenove rotas, agrupadas por área:

- Conta do cliente: `GET`/`PUT /clientes/me`, `PUT /clientes/me/foto` (multipart), `DELETE /clientes/me` (desativação).
- Endereços: `GET`/`POST /clientes/me/enderecos`, `PUT`/`DELETE /clientes/me/enderecos/{id}`.
- Extrato: `GET /clientes/me/pagamentos` — derivado de `Pagamento` e `FatoServico`, que já existem.
- Favoritos: `GET`/`POST /clientes/me/favoritos`, `DELETE /clientes/me/favoritos/{prestadorId}`.
- Notificações: `GET /clientes/me/notificacoes` — **calculado do estado existente**, sem tabela nova.
- Catálogo de um prestador: `GET /prestadores/{id}/servicos-ofertados` (ativos, prestador `APROVADO`).
- Solicitação: `GET /servicos/{id}` (detalhe, hoje inexistente) e `PUT /servicos/{id}/iniciar` (valida o código de confirmação).
- Mensagens: `GET`/`POST /servicos/{id}/mensagens`, `PUT /servicos/{id}/mensagens/lidas`.

**A RN02 é estendida.** O ciclo passa a ser `SOLICITADO -> ACEITO -> EM_ANDAMENTO -> CONCLUIDO -> AVALIADO`. O código de confirmação de quatro dígitos é gerado no aceite e devolvido **apenas ao cliente dono**; o prestador o informa em `PUT /servicos/{id}/iniciar` para começar o atendimento. Código incorreto responde 403 `ACESSO_NEGADO`. `openspec/config.yaml` e o `CLAUDE.md` da raiz declaram a RN02 na forma antiga e são atualizados junto — as duas fontes não podem divergir.

**A timeline passa a ter horário real.** `fato_servicos` ganha `criado_em`, `aceito_em`, `iniciado_em` e `concluido_em`; sem eles a timeline continuaria ordinal, e "14:05" continuaria inventado.

**A solicitação passa a saber onde acontece.** `SolicitacaoRequest` ganha `enderecoClienteId` **opcional** e `fato_servicos` uma FK anulável. É o que permite ao mapa de "Em Andamento" mostrar o local do serviço em vez do profissional. Aditivo: quem não informa o endereço se comporta como hoje.

**Removido, com o motivo:**

- **Carteira e cartões salvos** (saldo, cartão terminado em 4242, "Adicionar Cartão", "Adicionar Saldo"). Não existe carteira de cliente — `saldoDisponivel` é coluna de `dim_prestador` — nem entidade de cartão, nem gateway real: `PagamentoGatewayMock` aprova tudo. Persistir cartão sem PSP seria trocar um mock de tela por um mock com banco de verdade, o que é pior. O extrato real substitui a tabela de transações, e o recibo passa a vir dele.
- **2FA, "Sessões Ativas" e preferências de notificação.** O JWT é stateless, sem `jti` e sem tabela de sessão: revogar sessão e ativar segundo fator exigem infraestrutura de autenticação própria, não correção de mock. Preferência de notificação não significa nada antes de existir notificação enviada.
- **O assistente de IA.** Segue o precedente da change anterior, que trocou a "Estimativa IA" por preço praticado em vez de integrar um LLM. A Central de Ajuda e o FAQ **ficam**: são conteúdo estático honesto, que não se apresenta como dado da conta.
- **Rastreamento ao vivo do profissional** (marcador no mapa, ETA, placa do veículo). Exigiria o prestador reportando posição em segundo plano; o painel do prestador é web, e app nativo é US-21, Fase 3. O mapa fica, mostrando o endereço do serviço.
- **"Marcar todas como lidas"**, consequência aceita de não persistir estado de leitura. O aviso conta ações pendentes do cliente, então se resolve quando ele age.
- **O checkout de demonstração** (`painel-cliente.js:259-299`), que inventa taxa de R$ 4,50 sobre um preço lido de string. O fluxo real de pagamento já existe em `abrirPagamentoReal()` e passa a ser o único.

**Não há BREAKING de contrato.** Os dezenove endpoints são novos; as duas alterações em rotas existentes são aditivas (`SolicitacaoRequest` ganha campo opcional, `SolicitacaoResponse` ganha campos). O que muda de comportamento é a máquina de estados: **um prestador que hoje conclui direto de `ACEITO` passa a precisar iniciar o atendimento antes**, e isso é mudança deliberada de regra de negócio, não de contrato.

**O cancelamento passa a estornar de qualquer estado.** `FatoServicoService.cancelar` bloqueia por lista negra (`CONCLUIDO`, `AVALIADO`) mas estorna por lista branca (`== ACEITO`). Com `EM_ANDAMENTO`, cancelar um atendimento já iniciado seria permitido e **não estornaria** — o pagamento ficaria preso em `RETIDO` para sempre, e a resposta seria 200. O estorno passa a ser processado sempre, apoiado em `estornarSeRetido`, que já não faz nada quando não há retido.

**E o estorno deixa de ser sempre integral: nasce a taxa de cancelamento.** Se **o cliente** desiste de um atendimento que já começou, depois de dois minutos de carência, uma taxa é retida do valor pago e **creditada integralmente ao prestador** — ele se deslocou até o local e teve o tempo comprometido. A taxa é de 15% do valor do serviço, ou 20% quando a distância entre o local do atendimento e a localização do prestador passa de 10 km, limitada a um teto de R$ 50,00 e nunca maior que o valor pago. Carência, percentuais, limiar de distância e teto vivem em `parametro_negocio`, ajustáveis por administrador sem deploy.

**A plataforma não fica com nada nesse cancelamento**: a taxa de serviço da RN01 continua sendo devolvida ao cliente junto com o restante. A retenção existe para compensar quem se deslocou, não para o TaskGo lucrar com desistência. E cancelamento **pelo prestador** estorna integral, mesmo com o atendimento já iniciado: quem desiste não é a parte prejudicada.

Antes de confirmar, a interface tem de dizer ao cliente quanto será retido e quanto voltará. Uma taxa cobrada sem aviso seria exatamente o tipo de coisa que esta change existe para eliminar.

**US e RN tocadas:** US-03 (busca por geolocalização), US-04 e US-05 (solicitação e aceite), US-07 (conclusão) e US-13 (chat, Fase 2, antecipada por decisão de produto registrada nesta change). **RN02 é estendida** com o estado `EM_ANDAMENTO`. **A RN03 muda de conteúdo, não só de redação**: onde ela diz "cancelar a partir de `ACEITO` estorna integral, sem taxa", passa a valer para qualquer estado de onde se possa cancelar, e ganha a exceção da taxa de cancelamento em favor do prestador. **RN01 permanece intacta** — a taxa de serviço da plataforma não muda de cálculo, de limiar ou de destino; a taxa de cancelamento é outro conceito, e as duas nunca incidem no mesmo desfecho. RN04 (KYC) é preservada: todo endpoint novo que expõe prestador filtra por `APROVADO`.

## Não-objetivos

- **Carteira de cliente e cartões salvos.** Ver acima: sem PSP, persistir cartão é fingir com mais infraestrutura. Quando houver gateway real, é change própria, com tokenização e decisão sobre o que se armazena.
- **IA de verdade no assistente.** Adicionaria dependência, chave de API, custo por chamada e latência a um produto que hoje não tem nenhuma dependência de IA. É escopo próprio, com decisão de produto sobre o que o assistente responde e sobre que dado da conta ele alcança.
- **Rastreamento GPS ao vivo.** Depende de app do prestador reportando posição em segundo plano (US-21, Fase 3).
- **2FA, revogação de sessão e refresh token.** Exigem tabela de sessão, identificador no token e revisão do `JwtAuthFilter`, que hoje nunca toca o banco. É change de autenticação, e a segurança do login merece revisão própria.
- **Exclusão física de conta.** `DELETE /clientes/me` desativa. Apagar de fato quebraria as FKs de `fato_servicos` e `pagamento` e destruiria histórico financeiro do prestador. Anonimização com retenção contábil é decisão jurídica, não técnica.
- **Estado de leitura das notificações.** Exigiria tabela e evento de domínio; o feed derivado entrega o valor com zero migration. Notificação enviada por e-mail ou push (US-11) segue fora.
- **Busca por texto livre no backend.** O campo de busca reconcilia contra `/categorias` em vez de ganhar um parâmetro `termo` com `LIKE`. `categoria` é texto livre digitado pelo prestador, então busca parcial é problema de qualidade de busca — precisa de decisão sobre casamento, ranking e sinônimos, e afeta as páginas públicas também.
- **Perfil público de prestador.** Os cards de favorito e de resultado levam ao catálogo do prestador, não a um perfil. `GET /prestadores/{id}` exige JWT hoje, e o que se expõe de um autônomo é decisão própria — a change anterior já registrou isso como não-objetivo.
- **Anexos e imagens no chat.** Mensagens são texto. Anexo exigiria decidir armazenamento, limite e moderação.
- **Notificação em tempo real** (WebSocket, SSE). O chat e o aviso de atividade usam consulta periódica. Conexão persistente muda o modelo de deploy do backend.
- **Padronizar avatar de pessoa.** Os `pravatar.cc` saem das áreas que passam a vir da API, mas o painel do prestador continua usando os seus. Varredura completa é outra change.
- **Unificar o feedback visual e os modais do painel.** `openModal`/`closeModal` alternam `style.display` e `window.onclick` é atribuído globalmente (`painel-cliente.js:67-69`); o painel usa `confirm()` nativo em alguns pontos e `showToast` em outros. É refactor, e a convenção do projeto proíbe misturar refactor com feat.
- **Paginação do extrato e do histórico de mensagens.** Ambos devolvem o conjunto inteiro, como a busca já faz. Paginar exige contrato de página e ordenação estável.
- **Painel do prestador.** Ele tem os seus próprios mocks — visitas no perfil e taxa de conversão — que continuam documentados como decorativos. Fora do escopo pedido. Mas o prestador **ganha a ação de iniciar atendimento**, porque sem ela o estado `EM_ANDAMENTO` é inalcançável.

## Capabilities

### New Capabilities

- `painel-cliente`: o que o painel autenticado do cliente pode afirmar — proíbe saldo, transação, profissional, endereço, código de confirmação e contagem fabricados; exige que todo controle visível tenha efeito observável; define a busca e o simulador de custo do painel sobre os endpoints reais, e o aviso de atividade derivado do estado da conta. Espelha `vitrine-publica`, que a change anterior criou para as páginas públicas, aplicando a mesma regra à área logada.
- `favoritos-cliente`: marcar prestador como favorito, listar os próprios favoritos e removê-los, com o caminho de contratação a partir de um favorito.
- `mensagens-servico`: troca de mensagens entre o cliente e o prestador de uma solicitação, restrita às duas partes, com marcação de leitura.

### Modified Capabilities

- `cadastro-cliente`: ganha a gestão da própria conta — leitura e atualização de perfil, foto, endereços e desativação. A capability já é a dona da conta do cliente, então os requisitos entram nela em vez de criar capability nova.
- `solicitacao-servico`: o requisito do ciclo de vida passa a incluir `EM_ANDAMENTO`; a abertura ganha o endereço opcional; a conclusão passa a exigir atendimento iniciado; o cancelamento passa a declarar que o estorno não depende do estado de onde se cancela. Ganha o detalhe da própria solicitação e o início do atendimento por código. E o requisito **"Superfícies decorativas nos painéis"** é **removido**, substituído por um restrito ao painel do prestador: ele hoje *exige* que o checkout, o chat, o recibo e o simulador do cliente não façam requisição de rede, o que esta change contradiz diretamente — reescrevê-lo mantendo o nome deixaria a spec autorizando exatamente o que se está eliminando.
- `pagamento-custodia`: ganha a consulta do próprio extrato de pagamentos pelo cliente. A capability já é a dona do pagamento e da custódia; o extrato é leitura do que ela produz.
- `catalogo-servicos`: ganha a consulta dos serviços ativos de um prestador específico. Hoje o catálogo só é legível pelo dono (`/servicos-ofertados/meus`) ou pela busca por categoria.

`busca-servicos` **não** é modificada: nenhum parâmetro, filtro ou campo de resposta da busca muda. O que muda é quem a consome.

## Impact

**Banco de dados** — uma migration, `V8__painel_cliente.sql`. `dim_cliente` ganha `telefone`, `foto_url`, `ativo` e restrição de unicidade em `email` (que hoje não existe, apesar de `dim_prestador` ter a sua). Três tabelas novas: `endereco_cliente`, `favorito`, `mensagem`. `fato_servicos` ganha `pin_confirmacao`, `id_endereco_cliente` e os quatro timestamps. Com `ddl-auto=validate`, sem essa migration a aplicação não sobe.

**Backend** (`backend-estrela-main/`) — três entidades novas em `Entity/` e duas alteradas (`Cliente`, `FatoServico`), mais o enum `StatusSolicitacao`; três repositórios novos; controllers novos para conta do cliente, favoritos, notificações e mensagens, mais endpoints acrescentados a `FatoServicoController`, `PrestadorController` e `ServicoOfertadoController`; cerca de quinze DTOs. `Service/FatoServicoService.java` é o arquivo de maior risco: ele é o dono da máquina de estados, e ganha `EM_ANDAMENTO`, geração e validação do código de confirmação e os timestamps. `Controller/RelatorioController.java` conta por status e passa a ver um status que não conhecia. `security/SecurityConfig.java` não muda: todas as rotas novas são autenticadas, e `anyRequest().authenticated()` já as cobre. **`PATCH` não está liberado no CORS**, então toda atualização usa `PUT`.

**Testes** — `FluxoCompletoIntegrationTest` é o primeiro a quebrar: ele cobre US-01..US-10 e conclui a solicitação direto de `ACEITO`, transição que deixa de existir. Precisa do passo de iniciar atendimento com código. A migration **não é exercida pelos testes** (H2 com Flyway desligado e `create-drop`), então validá-la contra Postgres é passo manual obrigatório.

**Frontend** (`ProjetoTaskGoFinalizado-main/`) — `assets/js/api.js` ganha cerca de dezenove funções, mantendo a invariante de que nenhum outro arquivo chama `fetch`. `pages/painel-cliente.html` e `assets/js/painel-cliente.js` são reescritos em boa parte: são 1866 e 603 linhas, e todo controle mockado é `onclick=` inline no HTML, então cada integração toca os dois arquivos. `painel-cliente.html` também passa a carregar `assets/js/validacao.js`, que hoje não inclui, para ter erro por campo nos formulários de perfil e endereço. `pages/painel-profissional.html` e `assets/js/painel-profissional.js` ganham a ação de iniciar atendimento.

**Correções que entram no caminho** (commits `fix`, separados dos de `feat`): a colisão dos `id` `previewNumber`/`previewName`/`previewExpiry` entre a aba e o modal de cartão; a referência morta a `#notifCountBadge`; a montagem de mensagem com entrada não escapada nos dois chats (`painel-cliente.js:334-386` e `:570-603`), que hoje aceita HTML digitado pelo usuário; e o **login quebrado por e-mail duplicado**: `ClienteRepository.findByEmail` devolve `Optional`, então mais de uma linha com o mesmo e-mail faz o Spring Data lançar, e o `GlobalExceptionHandler` transforma isso em **500 `ERRO_INTERNO`** em vez de credencial inválida. O banco de desenvolvimento tem `carlos.browser1@teste.com` em quatro linhas de `dim_cliente` — essa conta não consegue logar hoje. A restrição de unicidade que `V8` cria fecha a porta; a deduplicação das linhas existentes é passo manual anterior a ela.

**Verificação** — `mvn test` a partir de `backend-estrela-main/`. No frontend não há suíte: a checagem é manual no navegador, com Postgres e backend no ar e as páginas servidas por HTTP, porque `file://` quebra as chamadas à API.

**Documentação** — entrada em `CHANGELOG.md` (seção "Não publicado"), diagrama Mermaid descritivo em `docs/diagrams/`, e a atualização da RN02 em `openspec/config.yaml` e no `CLAUDE.md` da raiz.

**Efeito colateral desejado** — hoje um cliente que abre o painel vê saldo, cartão, extrato, endereços, código de confirmação e profissionais que não existem, e recebe confirmação de perfil salvo sem nada ter sido salvo. Depois desta change, o que estiver na tela ou vem da conta dele, ou não está lá.
