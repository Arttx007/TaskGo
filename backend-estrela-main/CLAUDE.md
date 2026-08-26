# Backend Java TaskGo Guidelines (Spring Boot)

## Estrutura de pacotes (com.example.Estrela)
- `Controller/` — endpoints REST
- `DTO/` — objetos de entrada/saída (nunca expor Entity direto no Controller)
- `Entity/` — entidades JPA
- `repository/` — interfaces JpaRepository
- `Service/` — regras de negócio
- `EstrelaApplication.java` — classe main

## Arquitetura: modelo estrela (data warehouse)

O nome do projeto ("Estrela") reflete o modelo de dados: `FatoServico` é a tabela fato central (`fato_servicos`), com `@ManyToOne` para quatro dimensões — `Cliente` (`dim_cliente`), `Prestador` (`dim_prestador`), `Tempo` (`dim_tempo`) e `Localizacao` (`dim_localizacao`). `DashboardController` e `RelatorioController` fazem agregações diretas sobre `FatoServicoRepository`/`ClienteRepository`/`PrestadorRepository` (contagens por status, totais) para alimentar analytics — não são CRUD comuns. Ao adicionar métricas novas, prefira agregar via repository (`count`, `countByX`) em vez de carregar listas inteiras em memória.

`FatoServicoService` é o único service com regra de negócio real hoje (criação valida as 4 FKs de dimensão, transições de status AGENDADO → CONCLUIDO/CANCELADO, recálculo de `nota_media` do Prestador ao avaliar). Os demais controllers (`ClienteController`, etc.) ainda chamam o repository diretamente, sem passar por um Service — inconsistente com a regra "Controller -> Service -> repository" abaixo; ao tocar nesses fluxos, alinhe com o padrão do `FatoServicoService` em vez de replicar o atalho.

## Banco de dados

- Dev local: PostgreSQL, configurado em `src/main/resources/application.properties` (`jdbc:postgresql://localhost:5432/estrela`). É necessário ter um Postgres rodando localmente com esse schema antes de `mvn spring-boot:run`.
- `spring.jpa.hibernate.ddl-auto=update` — o schema é migrado automaticamente a partir das entidades, não há migrations versionadas (Flyway/Liquibase).
- H2 está no classpath como dependência de teste, mas os testes atuais (`EstrelaApplicationTests`) só verificam que o contexto Spring sobe — não há testes de repository/integração ainda.

## Build & Test Commands
- Rodar a suíte de testes: `mvn test`
- Rodar uma única classe de teste: `mvn test -Dtest=EstrelaApplicationTests`
- Build completo do projeto: `mvn clean install`
- Subir a aplicação localmente: `mvn spring-boot:run`

## Code Style & Rules
- Fluxo obrigatório: Controller -> Service -> repository. Controller nunca acessa repository diretamente.
- Use Lombok para reduzir boilerplate — evite gerar getters/setters manualmente.
- Leia variáveis de ambiente via `application.properties`, nunca hardcoded.
- DTOs ainda não são usados de forma consistente (só existe `LoginRequest`; a maioria dos controllers aceita/retorna Entity diretamente). Ao criar endpoints novos, siga a regra de DTO; não é necessário retrofitar os endpoints existentes fora do escopo da tarefa.

## Docstrings (Javadoc)
- Toda classe em `Controller/` e `Service/` deve ter Javadoc.
- Incluir `@throws` sempre que o método puder lançar exceção.