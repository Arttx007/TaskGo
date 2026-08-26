# Plano Técnico de Implementação — TaskGo MVP (US-01..US-10)

## Contexto

`spec.md` (raiz do projeto) já define o PRD do TaskGo: 10 user stories de MVP (US-01 a US-10) com critérios de aceite em Gherkin, 4 regras de negócio (RN01-RN04), e um backlog de Fase 2/Fase 3. Este documento traduz esse PRD em um plano técnico executável — que arquivos criar/alterar no backend (Spring Boot) e no frontend (HTML/CSS/JS estático), que decisões de arquitetura tomar (auth, geolocalização, escrow/pagamento, KYC), e em que ordem.

O plano foi desenhado a partir do estado real do código (não só das convenções descritas no `CLAUDE.md`). Descobertas centrais que moldam as decisões abaixo:
- **Backend não tem autenticação real** (login compara senha em texto puro, sem token), **não tem geolocalização** (só cidade/estado/bairro em texto), **não tem pagamento/wallet** (endpoint `/servicos/carteira` é um stub hardcoded `{"saldo": 0}`), **não tem upload de arquivo**, **não tem tratamento de exceção global**, e **`Prestador` nem tem campo de email/senha** — ou seja, login de prestador não existe hoje, nem em stub.
- **Frontend não chama a API nenhuma vez** — todos os fluxos (login, cadastro, aceitar/recusar pedido, saque Pix, avaliação) são simulados com `alert()`/`setTimeout()`/manipulação de DOM. Várias telas já têm a *forma* certa (ex.: modal de saque Pix, cards de solicitação em `painel-profissional.html`, grid de portfólio de serviços) — o trabalho é majoritariamente *substituir simulação por chamada real*, não desenhar do zero.
- Duas paletas de design tokens distintas coexistem (páginas públicas `.tprt-*` vs. dashboards com tema escuro `--bg-dark`/`--primary-blue`) — qualquer UI nova deve seguir a paleta da página onde entra, não inventar uma terceira.

O plano abaixo cobre as 10 stories do MVP. Fase 2/3 (US-11..US-21) ficam fora de escopo desta rodada de implementação, apenas com a base arquitetural (ex.: `Pagamento` como entidade própria) já pensada para não precisar de retrabalho quando chegarem.

## Decisões de arquitetura (resumo executivo)

| Área | Decisão | Por quê |
|---|---|---|
| Autenticação | JWT stateless emitido por `/auth/login`, com Spring Security só para o filter chain (sem OAuth2/Authorization Server) | Menor solução que cobre "saber quem chama" + "checar dono do recurso" (US-05/US-07) + "gatear por KYC" (RN04), sem federação/multi-cliente que o projeto não precisa |
| Schema/migrations | Adotar Flyway agora, abandonando `ddl-auto=update` | Mudanças deste lote incluem tabelas novas e uma coluna que muda de semântica (`status` texto→enum) — não há dado de produção real a proteger, e sem Flyway não há rollback/backfill seguro |
| Taxa de serviço (RN01) | Tabela `ParametroNegocio` (chave/valor no banco), não `application.properties` | O PRD exige ajuste sem deploy — properties exige restart |
| Escrow/pagamento/Pix | `PagamentoGateway` como interface, com implementação mock síncrona; saque Pix simulado como instantâneo | PSP real está fora de escopo do PRD (spec.md, seção de riscos) — mas o estado de custódia (RETIDO/LIBERADO/ESTORNADO) e o cálculo de taxa são reais e testáveis |
| Geolocalização (US-03) | Haversine em memória (Java), sem PostGIS | Zero infraestrutura geo hoje, volume de dados de portfólio não justifica dependência geoespacial, e mantém a query portável entre Postgres (dev) e H2 (testes) |
| KYC (US-01) | Upload para filesystem local (`./storage/kyc`), aprovação manual via novos endpoints de admin | Sem infra de nuvem hoje; endpoint de admin é necessário porque a persona "Administrador" do PRD só existe hoje como um dashboard read-only |
| Frontend: sessão | Token em `localStorage`, threading explícito do `usuarioAtual.id` em cada chamada de API | Site estático sem cookies httpOnly/backend de sessão; trade-off de segurança aceito e documentado, não uma decisão "segura por padrão" |
| Frontend: busca geo | Lista ordenada por proximidade, sem mapa (Leaflet fica só na aba de tracking, que já existe e não muda) | Critério de aceite do US-03 pede lista ordenada com distância — não pede mapa; evitar nova dependência |
| Frontend: cartão/pagamento | Mantém captura simulada (`salvarCartaoAnimacao`), mas agora persiste um "método de pagamento mock" real via API | Sem PSP escolhido, mas o restante do fluxo de pagamento (criação de cobrança, taxa, custódia) deve ser real, não só a tokenização de cartão |

## Backend — `backend-estrela-main/`

### 1. Dependências novas (`pom.xml`)
`spring-boot-starter-security`, `spring-boot-starter-validation`, `io.jsonwebtoken:jjwt-api/-impl/-jackson` (pinar versão 0.12.x e conferir compatibilidade de Jackson contra o BOM do Spring Boot 4.0.5 antes de fixar), `flyway-core` + `flyway-database-postgresql` (Boot 4 separou o suporte a Postgres em artefato próprio — confirmar contra o BOM real antes de implementar, já que `spring-boot-starter-webmvc`/`-webmvc-test` neste `pom.xml` já mostram que nomes de artefato mudaram do padrão SB2/3).

### 2. Migrations Flyway (`src/main/resources/db/migration/`)
Sequência: `V1__baseline.sql` (schema atual como está) → `V2__prestador_auth_kyc_saldo.sql` (email/senha/statusKyc/urls de documento/chavePix/saldoDisponivel/version em `dim_prestador`) → `V3__localizacao_geo.sql` (latitude/longitude em `dim_localizacao`) → `V4__servico_ofertado.sql` (tabela nova) → `V5__fato_servico_status_enum_and_fk.sql` (migra valores de status existentes: AGENDADO→ACEITO, mantém CONCLUIDO/CANCELADO; adiciona FK para servico_ofertado e comentario_avaliacao) → `V6__pagamento_saque.sql` → `V7__administrador_parametro_negocio.sql` (+ seed de `taxa.limiar=50.00`, `taxa.fixa=5.00`, `taxa.percentual=0.10`). Trocar `spring.jpa.hibernate.ddl-auto=update` → `validate` no `application.properties`.

### 3. Entidades — mudanças e novas (`Entity/`)
- **`Prestador`**: + `email`, `senha` (hash), `statusKyc` (enum `StatusKyc`, default PENDENTE), `documentoIdentidadeUrl`, `comprovantePixUrl`, `chavePix`, `saldoDisponivel` (BigDecimal, default 0), `@Version version` (evita lost-update entre crédito de saldo na conclusão e débito no saque).
- **`Localizacao`**: + `latitude`, `longitude` (Double, nullable — linhas existentes ficam NULL).
- **`FatoServico`**: `status` passa de `String` livre para `@Enumerated(STRING) StatusSolicitacao` (SOLICITADO/ACEITO/RECUSADO/CANCELADO/CONCLUIDO/AVALIADO — RN02); + FK `servicoOfertado` (nullable), + `comentarioAvaliacao`.
- **Novas**: `ServicoOfertado` (id, prestador FK, categoria, descricao, preco, status ATIVO/INATIVO, localizacao FK — separa "catálogo publicado" de "solicitação/transação", que hoje estão indevidamente misturados em `FatoServico`), `Pagamento` (fatoServico FK 1:1, valorBruto/valorTaxa/valorLiquido, status PENDENTE/RETIDO/LIBERADO/ESTORNADO/RECUSADO, metodoPagamento, timestamps), `Saque` (prestador FK, valor, chavePixDestino snapshot, status, timestamp), `Administrador` (nome, email, senha hash), `ParametroNegocio` (chave PK, valor, descricao).
- **Novos enums** (`Entity/`, todos `EnumType.STRING`): `StatusKyc`, `StatusSolicitacao`, `StatusServico`, `StatusPagamento`, `StatusSaque`, `TipoUsuario`.

### 4. Segurança (`security/`, novo pacote)
`JwtService` (gera/valida token; claims sub/role/nome; segredo e expiração via `application.properties`, nunca hardcoded), `TaskGoUserDetails`, `JwtAuthFilter` (`OncePerRequestFilter`, popula `SecurityContextHolder`), `SecurityConfig` (`SecurityFilterChain` stateless, CSRF off, CORS habilitado para a origem do frontend, `/auth/login` + `POST /clientes` + `POST /prestadores` + `GET /servicos-ofertados/buscar` públicos, resto autenticado, `/admin/**` exige ROLE_ADMIN; bean `PasswordEncoder`=`BCryptPasswordEncoder`).

Autorização por dono do recurso (US-05/US-07): o Controller extrai o id do usuário autenticado e passa explicitamente para o Service (ex.: `service.aceitar(solicitacaoId, prestadorIdAutenticado)`), que compara contra o dono real e lança `AcessoNegadoException` — mantém a lógica de autorização testável no Service sem mockar Spring Security.

`LoginRequest` ganha `tipoUsuario` (CLIENTE/PRESTADOR/ADMIN) para o login saber qual repositório checar. Bootstrap de admin via `CommandLineRunner` idempotente a partir de `taskgo.admin.bootstrap-email`/`-senha`. Dado local de `dim_cliente` com senha em texto puro será invalidado pela migração para hash — aceitável por ser dado de dev pré-lançamento (recriar via novo cadastro, sem migração de rehash).

### 5. Regra de taxa (RN01)
`Service/TaxaService.calcular(BigDecimal valorServico)` lê `ParametroNegocio` a cada chamada (sem cache — volume de portfólio não justifica), retorna `{valorTaxa, valorLiquido}`. Endpoints novos `GET/PUT /admin/parametros` via `AdminService` (não acesso direto a repositório no Controller).

### 6. Escrow / pagamento / saque (RN03, US-06/07/08)
`PagamentoGateway` (interface) + `PagamentoGatewayMock` (sempre aprova, exceto flag `simularFalha` para testar o cenário de exceção) + `PagamentoService` (orquestra taxa + gateway + persistência) + `SaqueService`. Fluxo: `POST /servicos/{id}/pagamento` (cliente, exige status ACEITO) calcula taxa, "cobra", cria `Pagamento{RETIDO}`; `PUT /servicos/{id}/concluir` (prestador dono, exige `Pagamento.RETIDO`) credita `saldoDisponivel` e libera o pagamento, tudo em um método `@Transactional`; `POST /prestadores/{id}/saques` valida saldo suficiente antes de debitar; cancelamento com pagamento retido estorna 100% sem cobrar taxa (nunca há débito a reverter no prestador, pois o crédito só ocorre na conclusão).

### 7. Geolocalização (US-03)
`Localizacao.latitude/longitude` + `Service/GeoService.distanciaKm(...)` (Haversine) + `ServicoOfertadoService.buscar(categoria, lat, lon, raioKm)` filtra por `status=ATIVO` e `prestador.statusKyc=APROVADO`, ordena por distância. `GET /servicos-ofertados/buscar?categoria=&lat=&lon=&raioKm=&cidade=` — sem lat/lon, cai no fallback por nome de cidade (satisfaz o cenário Gherkin de "busca manual por endereço"); resultado vazio retorna `200` com mensagem explícita, não array vazio silencioso.

### 8. Upload de documento KYC (US-01)
`Service/FileStorageService.store(MultipartFile, subdir)` valida tipo (`image/png`, `image/jpeg`, `application/pdf`) e tamanho (5MB via `spring.servlet.multipart.max-file-size`), salva em `./storage/kyc/{prestadorId}/{uuid}.{ext}` (path configurável). `POST /prestadores/{id}/documentos` (multipart, dono autenticado). Endpoints de admin novos: `GET /admin/prestadores/pendentes`, `PUT /admin/prestadores/{id}/kyc/aprovar`, `PUT /admin/prestadores/{id}/kyc/rejeitar`, `GET /admin/prestadores/{id}/documentos/{tipo}` (stream do arquivo para o admin conferir antes de aprovar) — tudo via novo `AdminService`, sem tocar no `/admin/dashboard` existente (regra de refactor do CLAUDE.md preservada para o que já existe).

### 9. Tratamento de exceção global
Novo pacote `exception/`: `RecursoNaoEncontradoException`(404), `AcessoNegadoException`(403), `EstadoInvalidoException`(409), `KycPendenteException`(422), `SaldoInsuficienteException`(422), `PagamentoRecusadoException`(402), `ArquivoInvalidoException`(400), mais o 400 automático de `@Valid`. `GlobalExceptionHandler` (`@RestControllerAdvice`) mapeia tudo para `DTO/ErrorResponse` consistente (`timestamp/status/error/message/path`). Isso é o que torna testável quase todo cenário "Exceção/Segurança" do `spec.md`.

### 10. DTOs (`DTO/`, records + Bean Validation)
`LoginRequest`/`LoginResponse`, `CadastroClienteRequest`/`ClienteResponse`, `CadastroPrestadorRequest`/`PrestadorResponse`, `KycDecisionRequest`, `ServicoOfertadoRequest`/`Response`, `BuscaServicoResponse` (com `distanciaKm`), `SolicitacaoRequest` (id do cliente vem do JWT, nunca do corpo — evita impersonação), `SolicitacaoResponse`, `PagamentoRequest`/`Response`, `AvaliacaoRequest`, `SaqueRequest`/`Response`, `CarteiraResponse`, `ParametroNegocioRequest`, `ErrorResponse`. Como `ClienteController`/`PrestadorController` já precisam mudar para hash de senha, aproveitar para migrá-los de Entity direta para DTO no mesmo lote (não é retrofit à parte).

### 11. Contrato REST completo (por story)

| Story | Endpoint | Auth | Sucesso | Erros principais |
|---|---|---|---|---|
| — | `POST /auth/login` | público | 200 `LoginResponse` | 401 |
| US-01 | `POST /prestadores` | público | 201 (statusKyc=PENDENTE) | 400, 409 email duplicado |
| US-01 | `POST /prestadores/{id}/documentos` | PRESTADOR dono | 200 | 400 formato/tamanho, 403 |
| US-01 | `GET /admin/prestadores/pendentes`, `PUT .../kyc/aprovar`, `PUT .../kyc/rejeitar` | ADMIN | 200 | 403, 404 |
| US-02 | `POST /servicos-ofertados`, `GET .../meus`, `PUT /servicos-ofertados/{id}` | PRESTADOR | 201/200 | 422 KYC pendente, 400 preço inválido, 403 não-dono |
| US-03 | `GET /servicos-ofertados/buscar` | público | 200 (lista + `distanciaKm`, ou mensagem vazia) | 400 sem localização |
| US-04 | `POST /servicos` | CLIENTE | 201 (SOLICITADO) | 409 duplicidade, 422 serviço/prestador indisponível |
| US-05 | `PUT /servicos/{id}/aceitar`, `/recusar` | PRESTADOR dono | 200 | 403 não é o prestador certo, 409 estado errado |
| US-06 | `POST /servicos/{id}/pagamento` | CLIENTE dono | 200 (RETIDO) | 402 recusado, 409 não está ACEITO |
| US-07 | `PUT /servicos/{id}/concluir` | PRESTADOR dono | 200 (CONCLUIDO, saldo creditado) | 403 cliente tentando concluir, 409 pagamento não confirmado |
| US-08 | `GET /prestadores/{id}/saldo`, `POST /prestadores/{id}/saques` | PRESTADOR dono | 200/201 | 422 saldo insuficiente, 403 |
| US-09 | `PUT /servicos/{id}/avaliar` | CLIENTE dono | 200 (AVALIADO) | 409 já avaliado / não concluído |
| US-10 | `PUT /servicos/{id}/cancelar` | CLIENTE ou PRESTADOR dono | 200 (CANCELADO + estorno se havia pagamento retido) | 409 já concluído |

**Breaking changes vs. API atual:** `PUT /servicos/{id}/confirmar` (setava CONCLUIDO direto) é substituído pela sequência aceitar→pagamento→concluir; `GET /servicos/carteira` (stub `{"saldo":0}`) é substituído por `GET /prestadores/{id}/saldo`.

### 12. Estratégia de testes
Antes de escrever qualquer teste, rodar `mvn dependency:tree -Dscope=test` para confirmar se `spring-boot-starter-webmvc-test`/`-data-jpa-test` (os novos starters granulares do Boot 4) já trazem JUnit5/Mockito/AssertJ transitivamente, ou se é preciso adicionar `mockito-core`/`assertj-core` explicitamente.

| Camada | Ferramenta | Foco |
|---|---|---|
| Repository | `@DataJpaTest` + H2 | queries de busca geo/categoria, contagem por status, unicidade de email |
| Service | JUnit5 + Mockito | `TaxaService` (limiar de R$50 dos dois lados), máquina de estados de `FatoServicoService` (RN02, toda transição legal/ilegal), `PagamentoService` (sucesso + `simularFalha`), `SaqueService` (saldo exato e acima do saldo) |
| Controller | `@WebMvcTest` + MockMvc | forma de DTO, status codes, 400 de `@Valid` |
| Integração | `@SpringBootTest` + MockMvc + H2 + filter chain real | fluxo completo por story (login→criar serviço→buscar→solicitar→aceitar→pagar→concluir→avaliar); cenário de autorização cruzada (prestador B tentando aceitar pedido do prestador A → 403 + status inalterado no banco) |

## Frontend — `ProjetoTaskGoFinalizado-main/`

### 1. `assets/js/api.js` (novo — toda story depende dele)
Wrapper `fetch` simples exposto como `TaskGoAPI` (script global, sem ES modules, para não exigir servidor/CORS especial em `file://`): `request(path, {method, body, auth})` normaliza erro (`ApiError{status, message, fieldErrors}`), `requestMultipart(path, formData)` para upload de KYC. Uma função por operação: `login`, `registrar`, `enviarDocumentoKyc`, `obterStatusKyc`, `criarServico`/`listarMeusServicos`/`atualizarServico`/`alternarServicoAtivo`, `listarPrestadoresProximos`, `criarSolicitacao`/`listarSolicitacoes`/`aceitarSolicitacao`/`recusarSolicitacao`/`cancelarSolicitacao`/`concluirSolicitacao`, `criarPagamento`/`criarMetodoPagamento`/`obterCarteira`, `solicitarSaque`/`obterSaldoPrestador`, `enviarAvaliacao`. Todas com JSDoc (`@param`/`@returns`, obrigatório pelo `CLAUDE.md`).

### 2. Sessão/auth no frontend
Token + usuário em `localStorage` (`taskgo_session`) — trade-off de segurança documentado, não uma escolha "segura por padrão" (site estático sem cookies httpOnly). `login.js` e `cadastro.js` passam a chamar `TaskGoAPI.login`/`registrar` de verdade em vez de só redirecionar; novo `assets/js/auth-guard.js` protege `painel-cliente.html`/`painel-profissional.html` (sem sessão válida → redireciona para login). `usuarioAtual.id` é lido uma vez por página e passado explicitamente em cada chamada de API.

### 3. Tabela de wiring por story

| Story | Arquivos | Substitui (mock atual) | Novo comportamento |
|---|---|---|---|
| US-01 (cadastro) | `pages/cadastro.html`, `assets/js/cadastro.js` | `fazerCadastro()` alert+redirect | chamada real, loading state, erro inline |
| US-01 (KYC) | **novo** `pages/cadastro-kyc.html` + `assets/js/kyc.js` | inexistente | reaproveita o padrão de upload já existente em `painel.js:70-93` (validação MIME/tamanho, preview, spinner), trocando o `setTimeout` fake por `enviarDocumentoKyc` real |
| US-02 | `painel-profissional.html` (extrair `<script>` inline para novo `assets/js/painel-profissional.js`) | `salvarNovaEspecialidade()`/`toggleServico()` fake | CRUD real de `ServicoOfertado` |
| US-03 | `profissionais-prximos.html`, `assets/js/profissionais.js` | 3 cards hardcoded, distância cosmética, form sem backend | `navigator.geolocation` real + busca real, lista renderizada da resposta, fallback por endereço textual, estado vazio explícito |
| US-04 | `profissionais-prximos.html` (botão "Agendar") | botão morto | modal de confirmação → `criarSolicitacao` → redireciona para "Meus Pedidos" |
| US-05 | `painel-profissional.html`, `assets/js/painel-profissional.js` | `aceitarServico()`/`recusarServico()` só move DOM | chamadas reais com loading/erro no card |
| US-06 | `painel-cliente.html`, novo `assets/js/painel-cliente.js` | `processarPagamento()` timeout fake | `criarPagamento` real; modal de cartão mantém captura simulada mas persiste via `criarMetodoPagamento` real |
| US-07 | `painel-profissional.html` — **UI nova** (não existe) | — | botão "Concluir Atendimento" na linha "Em Andamento" → `concluirSolicitacao` |
| US-08 | `painel-profissional.html`, `assets/js/painel-profissional.js` | `processarSaquePix()` com saldo em variável JS fake | saldo real via `obterSaldoPrestador`, débito validado pelo backend |
| US-09 | `painel-cliente.html`, `assets/js/painel-cliente.js` | modal de estrelas sem submit | handler real de envio → `enviarAvaliacao` |
| US-10 | `painel-cliente.html`/`painel-profissional.html` (botão cancelar) | só `showToast`, sem chamada | `cancelarSolicitacao` real com confirmação |

### 4. UI nova necessária
- **KYC**: página própria `cadastro-kyc.html` (não um passo dentro do form de cadastro) — dá um checkpoint recarregável para o estado PENDENTE; `painel-profissional.html` passa a checar `obterStatusKyc()` no carregamento e bloquear a aba "Meus Serviços" com um banner se não estiver APROVADO (reforço client-side de RN04; a garantia real continua sendo no backend).
- **Escolha de serviço/prestador (US-04)**: sem página de perfil nova — modal de confirmação direto em `profissionais-prximos.html`.
- **Concluir atendimento (US-07)**: novo botão na tabela de histórico "Em Andamento" já existente em `painel-profissional.html`.
- **Busca geo (US-03)**: lista, não mapa — ver decisão na tabela executiva.

### 5. Validação de formulário
Sem biblioteca nova. Novo `assets/js/validacao.js` generaliza o padrão já usado em `login.html` (`.has-error` + `checkValidity()`) para os novos formulários (cadastro, KYC, criação de serviço, saque, cartão), com mensagens inline (substituindo os `alert()` usados hoje) e mapeamento de erros de validação vindos do backend (`ApiError.fieldErrors`) para os mesmos elementos de erro.

### 6. O que fica simulado vs. real
Real: login/cadastro, upload/aprovação de KYC, CRUD de serviço, busca geo, todo o ciclo de solicitação (criar/aceitar/recusar/cancelar/concluir), criação da cobrança/custódia, saldo e saque Pix, avaliação. Simulado (intencionalmente, por decisão de produto documentada em `spec.md`): captura/tokenização real de cartão (sem PSP escolhido) — mas o registro do "método de pagamento" e a chamada que cria a cobrança são reais. Fora de escopo: chat (US-13, Fase 2), mapa de tracking com coordenadas ao vivo (nenhuma US do MVP exige).

## Arquivos críticos
- `backend-estrela-main/src/main/java/com/example/Estrela/Entity/FatoServico.java` (status vira enum, ganha FK de servico ofertado)
- `backend-estrela-main/src/main/java/com/example/Estrela/Entity/Prestador.java` (ganha auth+KYC+saldo)
- `backend-estrela-main/src/main/java/com/example/Estrela/Service/FatoServicoService.java` (máquina de estados RN02)
- `backend-estrela-main/src/main/java/com/example/Estrela/Controller/FatoServicoController.java` (endpoints substituídos)
- `backend-estrela-main/pom.xml`, `backend-estrela-main/src/main/resources/application.properties`
- `ProjetoTaskGoFinalizado-main/assets/js/api.js` (novo, toda story depende)
- `ProjetoTaskGoFinalizado-main/pages/painel-profissional.html` + novo `assets/js/painel-profissional.js`
- `ProjetoTaskGoFinalizado-main/pages/painel-cliente.html` + novo `assets/js/painel-cliente.js`
- `ProjetoTaskGoFinalizado-main/pages/profissionais-prximos.html`, `assets/js/profissionais.js`
- `ProjetoTaskGoFinalizado-main/pages/cadastro.html`, `assets/js/cadastro.js`, novo `pages/cadastro-kyc.html`

## Verificação
- Backend: `mvn test` (após implementar os testes descritos na seção 12) e `mvn spring-boot:run` com Postgres local rodando; validar manualmente com curl/Postman a sequência completa de uma story (ex.: `POST /prestadores` → `POST /prestadores/{id}/documentos` → aprovação de admin → `POST /servicos-ofertados` → `GET /servicos-ofertados/buscar` → `POST /servicos` → `PUT /aceitar` → `POST /pagamento` → `PUT /concluir` → `PUT /avaliar`).
- Frontend: sem build, testar abrindo via `npx serve .` (já sugerido no `CLAUDE.md`) contra o backend rodando localmente; percorrer manualmente o fluxo de cliente (cadastro → busca → solicitação → pagamento → avaliação) e o de prestador (cadastro → KYC → aprovação simulada via admin → criar serviço → aceitar pedido → concluir → sacar), conferindo estados de loading/erro/sucesso em cada tela tocada.
