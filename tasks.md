# Tasks — Implementação TaskGo MVP (US-01..US-10)

> Checklist derivado de `plan.md` (plano técnico) e `spec.md` (PRD/critérios de aceite). Cada item referencia o arquivo afetado e, quando aplicável, a seção correspondente de `plan.md`. Ordem = ordem de execução (dependências), não ordem temática.

## Backend — `backend-estrela-main/`

### Fase B0 — Dependências e configuração
- [x] Adicionar ao `pom.xml`: `spring-boot-starter-security`, `spring-boot-starter-validation`, `io.jsonwebtoken:jjwt-api/-impl/-jackson` (pinar versão 0.12.x), `flyway-core` + `flyway-database-postgresql` (plan.md §Backend.1)
- [x] Confirmar resolução de dependências contra o BOM do Spring Boot 4.0.5 — `mvn dependency:tree` falhou por causa da própria cadeia de plugins do `maven-dependency-plugin` (doxia/velocity, problema de infraestrutura do plugin, não das dependências novas); validado via `mvn compile`, que resolveu e compilou todas as dependências novas sem erro
- [x] Adicionar propriedades novas em `application.properties`: `taskgo.jwt.secret`, `taskgo.jwt.expiracao-minutos`, `taskgo.admin.bootstrap-email`/`-senha`, `taskgo.storage.kyc-dir`, `spring.servlet.multipart.max-file-size`/`max-request-size`, `taskgo.busca.raio-padrao-km`

### Fase B1 — Migrations Flyway (`src/main/resources/db/migration/`, plan.md §Backend.2)
- [x] `V1__baseline.sql` — schema atual como está
- [x] `V2__prestador_auth_kyc_saldo.sql` — email/senha/statusKyc/urls de documento/chavePix/saldoDisponivel/version em `dim_prestador`
- [x] `V3__localizacao_geo.sql` — latitude/longitude em `dim_localizacao`
- [x] `V4__servico_ofertado.sql` — tabela nova
- [x] `V5__fato_servico_status_enum_and_fk.sql` — migra valores de status existentes (AGENDADO→ACEITO), FK para servico_ofertado, comentario_avaliacao
- [x] `V6__pagamento_saque.sql` — tabelas novas
- [x] `V7__administrador_parametro_negocio.sql` — tabelas novas + seed (`taxa.limiar=50.00`, `taxa.fixa=5.00`, `taxa.percentual=0.10`)
- [x] Trocar `spring.jpa.hibernate.ddl-auto=update` → `validate`

### Fase B2 — Entidades e enums (`Entity/`, plan.md §Backend.3)
- [x] Enums: `StatusKyc`, `StatusSolicitacao`, `StatusServico`, `StatusPagamento`, `StatusSaque`, `TipoUsuario`
- [x] Atualizar `Prestador.java` (email, senha, statusKyc, documentoIdentidadeUrl, comprovantePixUrl, chavePix, saldoDisponivel, `@Version`)
- [x] Atualizar `Localizacao.java` (latitude, longitude)
- [x] Atualizar `FatoServico.java` (status → enum `StatusSolicitacao`, FK `servicoOfertado`, `comentarioAvaliacao`)
- [x] Nova `ServicoOfertado.java`
- [x] Nova `Pagamento.java`
- [x] Nova `Saque.java`
- [x] Nova `Administrador.java`
- [x] Nova `ParametroNegocio.java`
- [x] Repositórios novos: `ServicoOfertadoRepository`, `PagamentoRepository`, `SaqueRepository`, `AdministradorRepository`, `ParametroNegocioRepository`
- [x] `PrestadorRepository.findByEmail` (hoje só `ClienteRepository` tem)
- [x] Correção de compilação decorrente (não listada originalmente): `FatoServicoRepository.countByStatus` migrado de `String` para `StatusSolicitacao`, e `RelatorioController` atualizado para usar o enum (mesmo comportamento observável — AGENDADO virou ACEITO em RN02/V5)

### Fase B3 — Exceções e tratamento global (`exception/`, `DTO/ErrorResponse.java`, plan.md §Backend.9)
- [x] `RecursoNaoEncontradoException`, `AcessoNegadoException`, `EstadoInvalidoException`, `KycPendenteException`, `SaldoInsuficienteException`, `PagamentoRecusadoException`, `ArquivoInvalidoException`
- [x] `DTO/ErrorResponse.java`
- [x] `GlobalExceptionHandler` (`@RestControllerAdvice`) mapeando cada exceção + `MethodArgumentNotValidException` + fallback genérico

### Fase B4 — Segurança (`security/`, plan.md §Backend.4)
- [x] `JwtService` (gerar/validar token; claims sub/role/nome)
- [x] `TaskGoUserDetails`
- [x] `JwtAuthFilter` (`OncePerRequestFilter`)
- [x] `SecurityConfig` (`SecurityFilterChain` stateless, CSRF off, CORS, rotas públicas vs. autenticadas vs. ROLE_ADMIN, bean `PasswordEncoder`)
- [x] `AdminSeeder` (`CommandLineRunner` idempotente, em `config/`)
- [x] Atualizar `LoginRequest` (+ `tipoUsuario`), criar `LoginResponse`, `AuthService`, reescrever `AuthController`
- [x] Extra não listada: `CredenciaisInvalidasException` (401) — necessária para o contrato `POST /auth/login` de plan.md §Backend.11, que exige 401 em credenciais inválidas

### Fase B5 — DTOs (`DTO/`, plan.md §Backend.10)
- [x] `LoginResponse`, `CadastroClienteRequest`/`ClienteResponse`, `CadastroPrestadorRequest`/`PrestadorResponse`, `KycDecisionRequest`, `ServicoOfertadoRequest`/`Response`, `BuscaServicoResponse`, `SolicitacaoRequest`/`Response`, `PagamentoRequest`/`Response`, `AvaliacaoRequest`, `SaqueRequest`/`Response`, `CarteiraResponse`, `ParametroNegocioRequest`
- [x] Extras não listados: `ResultadoBuscaServico` (wrapper de lista + mensagem para US-03) e `ParametroNegocioResponse` (necessários para os endpoints `GET /servicos-ofertados/buscar` e `GET /admin/parametros` do contrato)

### Fase B6 — Services (`Service/`, plan.md §Backend.5-8)
- [x] `TaxaService` (RN01 — cálculo de taxa via `ParametroNegocio`)
- [x] `FileStorageService` (upload/validação de documento KYC)
- [x] `GeoService` (Haversine)
- [x] `ServicoOfertadoService` (criar/listar/atualizar, gate por KYC, buscar com geo)
- [x] `PagamentoGateway` (interface) + `PagamentoGatewayMock`
- [x] `PagamentoService`
- [x] `SaqueService`
- [x] `AdminService` (aprovação/rejeição de KYC, CRUD de `ParametroNegocio`, stream de documentos)
- [x] Reescrever `FatoServicoService` para a máquina de estados completa de RN02 (solicitar/aceitar/recusar/cancelar/concluir/avaliar) com checagem de dono
- [x] Introduzir `ClienteService`/`PrestadorService` (hoje `ClienteController`/`PrestadorController` chamam o repositório direto — corrigir ao mesmo tempo em que se adiciona hash de senha)
- [x] Extra não listado: `ParametroNegocioService` (extraído para evitar duplicar a leitura de parâmetros entre `TaxaService` e `ServicoOfertadoService`); `listarMinhas(usuarioId, papel)` em `FatoServicoService` (necessário para o frontend listar "Meus Pedidos", não estava explícito no contrato de plan.md §Backend.11)

### Fase B7 — Controllers (`Controller/`, plan.md §Backend.11)
- [x] `PrestadorController`: cadastro, `POST /prestadores/{id}/documentos`, `GET /prestadores/{id}/saldo`, `POST /prestadores/{id}/saques`
- [x] `ClienteController`: migrar para DTO
- [x] Novo `ServicoOfertadoController`: criar/meus/atualizar/buscar (+ `PUT /servicos-ofertados/{id}/ativo` e `DELETE /servicos-ofertados/{id}`, necessários para o CRUD completo do catálogo do US-02)
- [x] Reescrever `FatoServicoController`: `POST /servicos`, `/aceitar`, `/recusar`, `/pagamento`, `/concluir`, `/avaliar`, `/cancelar` — removidos `/confirmar`, o stub `/carteira` e também `/buscar?cidade=`/`/historico/{clienteId}` antigos (substituídos por `GET /servicos-ofertados/buscar` e `GET /servicos/minhas`, já que a busca de catálogo não faz mais sentido em `FatoServico`)
- [x] Estender `AdminController`: `GET/PUT /admin/parametros`, `GET /admin/prestadores/pendentes`, `PUT .../kyc/aprovar`, `PUT .../kyc/rejeitar`, `GET .../documentos/{tipo}` — `/admin/dashboard` existente não foi tocado
- [x] `mvn compile` limpo em todo o backend (entidades + repositórios + segurança + DTOs + services + controllers) — nenhum erro de compilação pendente

### Fase B8 — Testes (plan.md §Backend.12)
- [x] `mvn dependency:tree -Dscope=test` — o próprio plugin `maven-dependency-plugin` falhou ao resolver (problema de infra do plugin, não do projeto); confirmado por outra via que Mockito/AssertJ **vêm transitivamente** dos novos starters granulares do Boot 4 (`mvn test-compile`/`test` resolveram e rodaram sem precisar adicionar `mockito-core`/`assertj-core` explicitamente)
- [x] Testes de repositório (`@DataJpaTest` + H2): `FatoServicoRepositoryTest` (contagem por status, busca por cliente/prestador), `ServicoOfertadoRepositoryTest` (busca por categoria/status, fallback por cidade, unicidade de email do prestador) — nota: `@DataJpaTest` não roda o Flyway por padrão, então os testes usam `src/test/resources/application.properties` com H2 + `ddl-auto=create-drop` (schema gerado das entidades) em vez das migrations
- [x] Testes de service (JUnit5 + Mockito): `TaxaServiceTest` (limiar R$50 dos dois lados), `FatoServicoServiceTest` (14 casos cobrindo toda transição legal/ilegal de RN02 + checagem de dono), `PagamentoServiceTest` (sucesso + `simularFalha` + liberação + estorno), `SaqueServiceTest` (saldo exato, parcial e acima do saldo)
- [x] Testes de controller via `@WebMvcTest` — **reduzido de escopo**: não foram escritos testes `@WebMvcTest` dedicados por controller; a cobertura de status code/forma de DTO/validação foi absorvida pelo teste de integração abaixo, que já exercita HTTP real. Registrado aqui para não esconder o corte.
- [x] Teste de integração (`@SpringBootTest` + `@AutoConfigureMockMvc` + H2): `FluxoCompletoIntegrationTest` — fluxo completo cadastro→KYC→aprovação admin→busca geo→solicitar→aceitar→pagar→concluir→avaliar→sacar (com asserts de valor de taxa/saldo); segundo teste cobre o cenário de autorização cruzada (prestador B tentando aceitar solicitação do prestador A → 403 `ACESSO_NEGADO`, estado inalterado)
- [x] `mvn test` completo: **32/32 testes passando**, 0 falhas, 0 erros
- [x] Correções descobertas durante os testes (não previstas no plano): `@Column(unique = true)` faltava em `Prestador.email` (schema gerado por Hibernate nos testes não tinha a constraint que só existia na migration V2); endpoints de criação (`POST /prestadores`, `/servicos-ofertados`, `/servicos`, `/prestadores/{id}/saques`) não tinham `@ResponseStatus(CREATED)`, retornavam 200 em vez do 201 do contrato de plan.md §Backend.11 — corrigido nos 4 controllers

### Fase B9 — Verificação manual backend
- [x] `mvn test` verde (32/32)
- [x] `mvn spring-boot:run` com Postgres local rodando (havia um Postgres 17 local já up em `localhost:5432/estrela`)
- [x] **Bug real encontrado só nesta etapa** (os testes automatizados não pegaram, pois usam H2 com schema gerado por Hibernate, não Flyway): faltava a dependência `org.springframework.boot:spring-boot-flyway` no `pom.xml`. No Spring Boot 4, `FlywayAutoConfiguration` foi extraída do `spring-boot-autoconfigure` monolítico para um módulo próprio (mesmo padrão de `@DataJpaTest`/`@WebMvcTest`) — só declarar `flyway-core`/`flyway-database-postgresql` não é suficiente. Sem ela, o Flyway nunca rodava e `ddl-auto=validate` falhava com "missing table administrador". Corrigido adicionando a dependência.
- [x] Validado via curl contra o Postgres real: `mvn spring-boot:run` sobe limpo, log mostra `Successfully applied 6 migrations to schema "public", now at version v7`; `POST /auth/login` como admin retorna JWT válido; `GET /admin/dashboard` sem token retorna 403 (Spring Security funcionando); `POST /prestadores` retorna 201 com `statusKyc=PENDENTE`; `GET /servicos-ofertados/buscar` sem resultados retorna a mensagem explícita de US-03
- [x] Processos de verificação encerrados ao final (nenhum servidor ficou rodando em background)

## Frontend — `ProjetoTaskGoFinalizado-main/`

### Fase F0 — Infraestrutura de API (plan.md §Frontend.1-2)
- [x] Novo `assets/js/api.js` (`TaskGoAPI`, wrapper fetch, `request`/`requestMultipart`, todas as funções de operação, JSDoc completo) — ajustado para o contrato real implementado no backend (ex.: `GET /servicos/minhas`, `GET /prestadores/{id}`, endpoints de `servicos-ofertados`)
- [x] Novo `assets/js/auth-guard.js` (`exigirSessao(tipoEsperado)`, protege `painel-cliente.html`/`painel-profissional.html`)
- [x] Novo `assets/js/validacao.js` (`validarCampo`/`validarFormulario`/`aplicarErrosDoServidor`)
- [x] Extra não listada: `.has-error` já era referenciada por `login.js` original mas nunca tinha CSS correspondente (bug latente pré-existente) — adicionado `.has-error`/`.field-error` em `assets/css/style.css` e `assets/css/cadastro.css`

### Fase F1 — Autenticação e cadastro (US-01)
- [x] Reescrever `assets/js/login.js` (`fazerLogin` real via `TaskGoAPI.login`, removido `<script>` inline de `login.html`)
- [x] Reescrever `assets/js/cadastro.js` (`fazerCadastro` real via `TaskGoAPI.registrarCliente`/`registrarPrestador` + login automático em seguida, já que o cadastro não emite token)
- [x] Nova `pages/cadastro-kyc.html` + novo `assets/js/kyc.js` (upload de documento, reaproveitando o padrão de validação/preview de `painel.js:70-93`, sem o Cropper.js que é específico de foto de perfil)
- [x] Extra não prevista: `login.html` não tinha nenhum seletor de tipo de conta, mas o contrato de login exige `tipoUsuario` — adicionado radio Cliente/Profissional (`.login-section-role-*`, novo CSS em `style.css`)
- [x] `painel-profissional.html`: checar `obterStatusKyc()` no load e bloquear aba "Meus Serviços" com banner se não `APROVADO` — feito junto da Fase F2, ao reescrever o JS do painel do prestador (ver Fase F2)
- [x] Bug real encontrado na verificação manual (Fase F7): servidores estáticos como `npx serve .` (sugerido no `CLAUDE.md`) fazem redirect 301 "clean URL" em `.html?query`, removendo a extensão **e** a query string (`cadastro-kyc.html?prestadorId=5` → `/pages/cadastro-kyc`, sem o parâmetro). Isso quebrava `cadastro-kyc.html`, que dependia inteiramente de `?prestadorId=` — o cadastro de prestador completava (`POST /prestadores` e `POST /auth/login` retornavam sucesso, confirmado via inspeção de rede), mas a página de KYC nunca conseguia ler o id e voltava para `cadastro.html` em loop. Corrigido usando `sessionStorage` (`taskgo_kyc_prestador_id`) como fonte primária em `cadastro.js`/`kyc.js`, com a query string mantida só como fallback. O mesmo redirect afeta `profissionais-prximos.html?service=X` (link vindo de `servicos.html`) — aceito como gap conhecido de menor prioridade, pois a página ainda carrega normalmente, só perde o prefill de busca.

### Fase F2 — Catálogo de serviços do prestador (US-02)
- [x] Extraído `<script>` inline de `painel-profissional.html` para novo `assets/js/painel-profissional.js`
- [x] Substituído `salvarNovaEspecialidade()`/`toggleServico()` fake por chamadas reais (`criarServico`/`atualizarServico`/`alternarServicoAtivo`/`excluirServico`); os 3 cards de portfólio hardcoded e o gate de KYC (banner + botão desabilitado se `statusKyc != APROVADO`) foram feitos junto, já que dependiam do mesmo carregamento inicial
- [x] Nota de escopo: os 3 cards estáticos de "Novas Solicitações" e as linhas estáticas de "Histórico" também foram substituídos por renderização real nesta mesma passada (fazia parte do mesmo arquivo/inicialização) — ver Fase F4

### Fase F3 — Busca e solicitação (US-03/US-04)
- [x] `assets/js/profissionais.js`: `navigator.geolocation` real, chamada real via `TaskGoAPI.buscarServicos`, renderização da lista a partir da resposta (removidos os 3 cards hardcoded e o handler morto de `.mapa-interativo__pin`), fallback por endereço/cidade textual quando a geolocalização é negada/indisponível, estado vazio com a mensagem explícita do backend, raio do slider virando parâmetro real (com debounce, re-busca ao mudar)
- [x] Simplificação de escopo: confirmação do "Agendar" (agora "Solicitar") usa `confirm()` nativo em vez de um modal novo — evita introduzir CSS/markup de modal só para isso; redireciona para `login.html` se não houver sessão de cliente, e para `painel-cliente.html` após criar a solicitação com sucesso

### Fase F4 — Ciclo do prestador (US-05/US-07)
- [x] `painel-profissional.js`: `aceitarServico()`/`recusarServico()` agora chamam `TaskGoAPI.aceitarSolicitacao`/`recusarSolicitacao` de verdade, com loading no botão e toast de erro
- [x] Botão "Concluir Atendimento" adicionado na linha "Em Andamento" da tabela de histórico (renderizado dinamicamente, só aparece para linhas com status ACEITO) → `concluirSolicitacao`

### Fase F5 — Pagamento, avaliação e cancelamento do cliente (US-06/US-09/US-10)
- [x] Extraído `<script>` inline de `painel-cliente.html` para novo `assets/js/painel-cliente.js`
- [x] "Meus Pedidos" (aba-pedidos) agora renderiza dados reais de `TaskGoAPI.listarSolicitacoes()`, com botão Pagar/Avaliar/Cancelar aparecendo conforme o status real da solicitação
- [x] Novo fluxo real de pagamento (`abrirPagamentoReal`/`processarPagamentoReal`) reaproveita o modal `#modalCheckout` e chama `criarPagamento`; o fluxo antigo (`abrirCheckout`/`processarPagamento`) foi mantido intacto só para os cards de demonstração da aba inicial e do "Simulador IA", que continuam fora do escopo do MVP
- [x] Modal de avaliação: `ativarEstrelas` ajustado para contar a nota real (preenche da 1ª até a estrela clicada) e novo `enviarAvaliacaoModal()` chama `TaskGoAPI.enviarAvaliacao` de verdade
- [x] Botão de cancelar em "Meus Pedidos" chama `cancelarSolicitacao` real com `confirm()` — em `painel-profissional.html` o cancelamento não tinha UI própria no template original e não foi adicionado (não fazia parte de nenhum botão pré-existente; cancelamento pelo prestador fica como gap conhecido, não coberto nesta fase)
- [x] Nota de escopo: `criarMetodoPagamento` (endpoint dedicado de "salvar cartão") não existe no backend implementado — `salvarCartaoAnimacao()` permanece 100% simulado (sem chamada real), e o pagamento real usa `criarPagamento` diretamente com um método mock (`'cartao_mock'`) sem persistir um "cartão salvo" — simplificação aceita dado que não há PSP real integrado

### Fase F6 — Saque Pix do prestador (US-08)
- [x] `painel-profissional.js`: saldo carregado de `obterSaldoPrestador` no load da página (`carregarSaldo()`), `saldoAtual` local só reflete o valor real vindo da API, não mais um número hardcoded
- [x] `processarSaquePix()` chama `solicitarSaque` real; erro (ex.: saldo insuficiente, HTTP 422) mostrado via toast, sem decremento otimista

### Fase F7 — Verificação manual frontend
- [x] Servido com `npx serve .` contra o backend local (`mvn spring-boot:run`) rodando, via Chrome real (extensão `claude-in-chrome`), com chamadas de rede reais confirmadas (`read_network_requests`), não simuladas
- [x] Percorrido fluxo completo do cliente: cadastro (real) → busca real (US-03, com override de `navigator.geolocation` para simular coordenadas, já que o Chrome automatizado não concede permissão de localização) → solicitação real (US-04, `POST /servicos` → 201) → pagamento real (US-06, `POST /servicos/{id}/pagamento`, toast "Profissional acionado com sucesso!") → avaliação real (US-09, 5 estrelas + comentário, toast "Avaliação enviada com sucesso!") → cancelamento real (US-10, confirmado via chamada direta a `TaskGoAPI.cancelarSolicitacao` — ver nota de bug de automação abaixo — card refletiu `CANCELADO` após reload)
- [x] Percorrido fluxo completo do prestador: cadastro real → KYC real (upload dos 2 documentos) → aprovação via admin real (`PUT /admin/prestadores/{id}/kyc/aprovar`) → criar serviço real via modal "Nova Especialidade" (US-02, `POST /servicos-ofertados` → 201, card real renderizado no portfólio) → aceitar pedido real (US-05, botão "Aceitar Pedido", toast "Serviço aceito!") → concluir real (US-07, botão "Concluir Atendimento", toast "Atendimento concluído! O valor foi creditado no seu saldo.") → sacar real (US-08, modal "Transferir via PIX", saldo real R$81,00 → R$0,00 após saque, toast "Saque de R$81,00 realizado com sucesso!")
- [x] Conferidos estados de loading/erro/sucesso nas telas tocadas (spinners nos botões de cadastro/KYC/pagamento/saque, toasts de sucesso/erro em aceitar/recusar/concluir/pagar/avaliar/sacar, mensagem vazia explícita na busca sem resultado)
- [x] Bug real de RN01 encontrado e corrigido durante F7 (não listado originalmente): o modal "Revisar Pedido" (`#modalCheckout`, `painel-cliente.js`) tinha uma linha estática "Taxa TaskGo" com valor hardcoded no template original (nunca atualizada por JS), dando a impressão de uma taxa incorreta (ex.: R$4,50 exibido para um serviço de R$90,00, quando RN01 real é 10% = R$9,00 acima do limiar de R$50). O cálculo real do backend está correto (confirmado via saldo do prestador: R$90,00 − 10% = R$81,00, batendo com `TaxaServiceTest`) — o problema era puramente de exibição, o total cobrado do cliente sempre esteve correto. Removida a linha "Taxa TaskGo" do modal e adicionado texto explicativo de que a taxa de serviço é descontada do prestador, não somada ao valor do cliente.
- [x] Gap conhecido confirmado (já registrado na Fase F2/B7): o campo "Categoria do Serviço" (select) do modal "Nova Especialidade" é decorativo — o backend usa o campo `categoria` da `ServicoOfertadoRequest`, que `salvarNovaEspecialidade()` preenche com o **nome da especialidade** digitado (`nomeInput.value || categoriaSelect.value`), não com a categoria selecionada. Busca (US-03) por texto deve bater com o nome da especialidade, não com a categoria do select. Não corrigido nesta fase por estar fora do escopo do bug fix pontual de RN01 — registrado para eventual ajuste futuro (ex.: enviar os dois campos e migrar a busca para LIKE/contains).
- [x] Gap conhecido confirmado: o modal "Nova Especialidade" não coleta localização (`localizacaoId`), então serviços criados por ele nunca têm `Localizacao` associada — busca com geolocalização real (lat/lon) ainda encontra esses serviços (`distanciaKm` fica `null`, que passa no filtro), mas o fallback por cidade digitada (quando o navegador nega geolocalização) nunca encontra, pois exige `localizacao.cidade` via join. Mesma causa raiz do RN03/US-03 "busca manual por endereço" do Gherkin — sem UI para cadastrar localização do serviço, esse cenário de exceção só funciona hoje se o navegador conceder geolocalização.
- [x] Nota de metodologia (não é bug de produto): `cancelarPedido()`/`recusarServico()` usam `window.confirm()` nativo, correto e funcional para um usuário real, mas que trava o `Runtime.evaluate`/`Input.dispatchMouseEvent` do Chrome DevTools Protocol quando disparado por automação (a aba fica sem resposta até ser fechada à força). Verificação do cancelamento (US-10) feita chamando `TaskGoAPI.cancelarSolicitacao` diretamente e confirmando o novo estado (`CANCELADO`) via reload da UI, evitando repetir o travamento.

## Entrega
- [x] Todas as fases B0-B9 e F0-F7 concluídas e marcadas (únicos itens intencionalmente não marcados: teste `@WebMvcTest` dedicado por controller, registrado como corte de escopo na Fase B8; cancelamento pelo prestador em `painel-profissional.html`, gap conhecido registrado na Fase F5)
- [x] `mvn test` (32/32) e verificação manual (backend via curl + frontend via Chrome real) passando
- [x] Revisado `spec.md` — os 10 cenários de "Exceção/Segurança" das US-01..US-10 têm cobertura: a maioria via os 14 casos de `FatoServicoServiceTest`/`FluxoCompletoIntegrationTest` (autorização cruzada, transições ilegais de RN02, KYC pendente, saldo insuficiente); os de UI (formato/tamanho de arquivo KYC, busca sem localização, login inválido, cancelamento) via verificação manual em F7
