# Backend Java TaskGo Guidelines (Spring Boot)

## Estrutura de pacotes (com.example.Estrela)
- `Controller/` — endpoints REST
- `Service/` — regras de negócio
- `repository/` — interfaces JpaRepository
- `Entity/` — entidades JPA e enums de status
- `DTO/` — records de entrada/saída (nunca expor Entity direto no Controller)
- `security/` — JWT (`JwtService`, `JwtAuthFilter`, `TaskGoUserDetails`) e `SecurityConfig`
- `exception/` — exceções de domínio + `GlobalExceptionHandler`
- `config/` — `AdminSeeder`
- `EstrelaApplication.java` — classe main

## Arquitetura

### Duas camadas sobrepostas: modelo estrela + domínio de marketplace

O nome "Estrela" vem do modelo dimensional original: `FatoServico` é a tabela fato central (`fato_servicos`), com `@ManyToOne` para quatro dimensões — `Cliente` (`dim_cliente`), `Prestador` (`dim_prestador`), `Tempo` (`dim_tempo`) e `Localizacao` (`dim_localizacao`). Esse esquema foi **mantido** e o marketplace do MVP foi construído em cima dele: a mesma `FatoServico` é hoje a *solicitação de serviço* transacional, com máquina de estados, pagamento e avaliação. Por isso a linha do tempo (`Tempo`) é criada sob demanda a cada solicitação (`FatoServicoService.obterOuCriarTempoDeHoje`).

Entidades do marketplace acrescentadas por cima: `ServicoOfertado` (catálogo do prestador), `Pagamento`, `Saque`, `Administrador`, `ParametroNegocio`.

### O núcleo: FatoServicoService

`FatoServicoService` é o dono da máquina de estados de `StatusSolicitacao` (RN02):

```
SOLICITADO --aceitar--> ACEITO --concluir--> CONCLUIDO --avaliar--> AVALIADO
     |                    |
     +--recusar--> RECUSADO
     +---------- cancelar ----------> CANCELADO
```

Invariantes que qualquer mudança precisa preservar:
- **Toda transição valida o dono** (`buscarEValidarDonoPrestador` / `buscarEValidarDonoCliente`) — `AcessoNegadoException` se outro usuário tentar agir sobre a solicitação. Há teste de autorização cruzada em `FluxoCompletoIntegrationTest`.
- **`concluir` libera o pagamento antes de mudar o status** — concluir sem pagamento em custódia falha (`EstadoInvalidoException`).
- **`cancelar` a partir de ACEITO estorna integralmente**, sem cobrar taxa (RN03). Não debita o prestador, porque o crédito só acontece em `PagamentoService.liberar`.
- **`avaliar` recalcula `nota_media` do prestador** varrendo todas as solicitações avaliadas dele.
- Cliente não pode ter duas solicitações em aberto (SOLICITADO/ACEITO) com o mesmo prestador.

### Dinheiro: taxa, custódia e saque

`PagamentoService` implementa custódia (escrow) real e persistida — `PENDENTE` → `RETIDO` → `LIBERADO`/`ESTORNADO` — mas a cobrança em si passa por `PagamentoGateway`, cuja única implementação é `PagamentoGatewayMock` (não há PSP real; `PagamentoRequest.simularFalha()` força uma recusa para testar o caminho 402). `SaqueService` debita `saldoDisponivel` do prestador e marca o Pix como `PROCESSADO` imediatamente — o repasse também é simulado.

`TaxaService` (RN01) aplica taxa fixa abaixo de um limiar e percentual a partir dele. Os três valores (`taxa.limiar`, `taxa.fixa`, `taxa.percentual`) e o raio padrão de busca (`busca.raio-padrao-km`) vivem na tabela `parametro_negocio`, **não** em `application.properties`, porque o PRD exige ajuste por admin sem deploy. `ParametroNegocioService` lê sem cache de propósito. Ao adicionar um parâmetro ajustável, siga esse caminho (seed via migration Flyway) em vez de criar uma property.

### Segurança

Autenticação stateless por JWT: `/auth/login` (`AuthService`) emite o token, `JwtAuthFilter` valida o header `Bearer` e popula o `SecurityContext` com `TaskGoUserDetails` (id + `TipoUsuario` + nome) — **sem consultar o banco**. Controllers obtêm a identidade via `@AuthenticationPrincipal TaskGoUserDetails usuario` e repassam o id para o Service, que faz a checagem de dono do recurso. Rotas públicas (`SecurityConfig`): `POST /auth/login`, `POST /clientes`, `POST /prestadores`, `GET /servicos-ofertados/buscar`. `/admin/**` exige role ADMIN; todo o resto exige autenticação.

Senhas com BCrypt. `AdminSeeder` cria um admin no boot se a tabela estiver vazia (não há autocadastro de admin), usando `TASKGO_ADMIN_EMAIL`/`TASKGO_ADMIN_SENHA`.

KYC (RN04): `FileStorageService` grava documentos em `taskgo.storage.kyc-dir` (`./storage/kyc`, git-ignorado — são dados pessoais); um prestador sem `StatusKyc.APROVADO` não publica serviço nem recebe solicitação.

### Erros

`GlobalExceptionHandler` mapeia cada exceção de domínio para um `ErrorResponse` com `codigo` estável — ex.: `RecursoNaoEncontradoException` → 404 `RECURSO_NAO_ENCONTRADO`, `EstadoInvalidoException` → 409 `ESTADO_INVALIDO`, `PagamentoRecusadoException` → 402, `SaldoInsuficienteException`/`KycPendenteException`/`RecursoIndisponivelException` → 422. O frontend depende desses status/códigos. Lance a exceção de domínio adequada em vez de montar um `ResponseEntity` de erro à mão.

### Geolocalização

`GeoService` usa Haversine em memória (sem PostGIS/hibernate-spatial), para manter a query portável entre Postgres (dev) e H2 (testes). `ServicoOfertadoService.buscar` filtra candidatos no banco por categoria/status e ordena/corta por distância em Java; sem lat/lon, cai no fallback por nome de cidade.

## Banco de dados

- **Flyway é a fonte da verdade do schema** (`src/main/resources/db/migration/`, V1..V7) e `spring.jpa.hibernate.ddl-auto=validate`. Mudança de entidade **exige** uma migration nova `V{n}__descricao.sql` — o Hibernate não cria mais coluna nenhuma, e a aplicação falha no boot se entidade e schema divergirem.
- Dev local: PostgreSQL em `jdbc:postgresql://localhost:5432/estrela`, credenciais em `application.properties`.
- Testes: `src/test/resources/application.properties` sobrescreve o arquivo principal por inteiro (mesmo nome no classpath), sem datasource — o Spring Boot cai em H2 em memória, com Flyway desligado e `ddl-auto=create-drop`. Consequência prática: **os testes não precisam de Postgres rodando**, mas também não exercitam as migrations.

## Build & Test Commands
- Suíte completa: `mvn test`
- Uma classe: `mvn test -Dtest=FatoServicoServiceTest`
- Um método: `mvn test -Dtest=FatoServicoServiceTest#nomeDoMetodo`
- Várias classes: `mvn test "-Dtest=TaxaServiceTest,SaqueServiceTest"`
- Build completo: `mvn clean install`
- Subir localmente (exige Postgres): `mvn spring-boot:run`

Sem Maven instalado, use o wrapper: `./mvnw` (`mvnw.cmd` no PowerShell).

### Organização dos testes
- `Service/*Test` — unitários com Mockito (`@ExtendWith(MockitoExtension.class)`), onde vive a maior parte da cobertura de regra de negócio.
- `repository/*Test` — `@DataJpaTest` sobre H2.
- `FluxoCompletoIntegrationTest` — `@SpringBootTest` + MockMvc, percorre US-01..US-10 ponta a ponta. É o teste que quebra primeiro quando um contrato de endpoint muda; rode-o ao mexer em Controller, DTO ou status HTTP.
- `EstrelaApplicationTests` — só verifica que o contexto sobe.

## Code Style & Rules
- Fluxo obrigatório: Controller -> Service -> repository. Controller nunca acessa repository diretamente.
- Injeção por construtor (padrão do código novo), não `@Autowired` em campo.
- Use Lombok para reduzir boilerplate — evite gerar getters/setters manualmente.
- Leia variáveis de ambiente via `application.properties`, nunca hardcoded. Segredos (`TASKGO_JWT_SECRET`, `TASKGO_ADMIN_SENHA`) entram por env var, com default só para dev.
- Endpoints recebem e devolvem DTOs (records em `DTO/`), com `@Valid` na entrada.

### Débitos conhecidos (não "conserte" de surpresa fora de escopo)
`TempoController` e `LocalizacaoController` ainda usam `@Autowired` de repository direto e trafegam Entity — resquício do CRUD dimensional original. `DashboardController`, `RelatorioController` e o `GET /admin/dashboard` também vão direto ao repository, mas de propósito: são agregações analíticas (`count`), e o certo ali é continuar agregando via repository (`count`, `countByX`) em vez de carregar listas em memória. Note que `GET /admin/dashboard` devolve uma `String` concatenada, não JSON. Ao tocar nesses fluxos, alinhe com o padrão dos services novos; não é necessário retrofitar o resto.

## Docstrings (Javadoc)
- Toda classe em `Controller/` e `Service/` deve ter Javadoc.
- Incluir `@throws` sempre que o método puder lançar exceção — inclusive as de domínio que viram status HTTP, indicando o status (o código existente faz isso; mantenha).
- Quando uma decisão for deliberada e não óbvia (mock de gateway, ausência de cache, Haversine em memória), documente o *porquê* — o código atual segue esse padrão.
