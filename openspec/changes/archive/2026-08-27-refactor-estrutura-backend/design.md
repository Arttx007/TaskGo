## Context

Ver `proposal.md` (seção Why) para a motivação. O que molda o desenho aqui é uma restrição só: **nenhum comportamento observável pode mudar**, e "observável" inclui mais do que o caminho feliz — inclui status HTTP, código de erro e o texto de `message` no `ErrorResponse`, porque tudo isso está escrito nas specs promovidas e é consumido pelo frontend.

Isso muda o critério de aceite de cada item: não basta que o código fique mais limpo, é preciso demonstrar *por que* a mudança não pode alterar o que sai pela API. Onde essa demonstração não for possível, o item sai de escopo.

Estado relevante:

- A suíte tem 32 testes e está verde. `FluxoCompletoIntegrationTest` cobre login dos três tipos de conta; `FatoServicoServiceTest` cobre os três caminhos de posse negada. Os dois pontos de maior risco deste refactor caem justamente sob esses testes.
- Não existe teste algum de `AuthService` isolado, nem dos controllers analíticos. Para esses, a rede é o teste de integração e a checagem manual.

## Goals / Non-Goals

**Goals:**

- Alinhar o código antigo ao padrão que o código novo já segue, sem inventar um padrão terceiro.
- Deixar cada remoção de duplicação com uma justificativa de neutralidade verificável, não com uma alegação de que "deve dar na mesma".

**Non-Goals:**

- Buscar simetria pela simetria. Onde a duplicação carrega diferença observável, ela fica.
- Introduzir camada, pacote ou abstração nova para acomodar o refactor.
- Melhorar cobertura de teste. Aumentar a rede é trabalho legítimo, mas é outra change: escrever teste novo aqui misturaria escopo `refactor` com escopo `test`.

## Decisions

### Injeção por construtor sem `@Autowired`

Com um único construtor, o Spring injeta as dependências sem anotação — é o que os controllers novos do projeto já fazem. Os campos passam a `private final`.

*Alternativa considerada:* manter `@Autowired` no construtor. Rejeitada por ser redundante desde o Spring 4.3 e destoar do código novo.

`AdminController` é o único caso misto: hoje tem três repositories em campo e um construtor para `AdminService`. Vira um construtor só, com as quatro dependências.

### O mapeamento compartilhado vira fábrica estática no próprio record

`PrestadorResponse.de(Prestador)` em `DTO/PrestadorResponse.java`, chamada por `PrestadorController` e `AdminController`.

*Trade-off aceito:* o record passa a importar `Entity`, o que inverte levemente a direção de dependência que se espera entre DTO e domínio. Em troca, o mapeamento fica ao lado da forma que ele produz, e nenhum arquivo novo é criado.

*Alternativa considerada:* um pacote `mapper/` com uma classe dedicada. Rejeitada: resolveria a inversão, mas introduz uma camada nova para um único mapeamento duplicado — mais estrutura do que o problema pede, e um padrão que o projeto teria de seguir daí em diante.

### Em `FatoServicoService`, extrair a busca e **não** a checagem de posse

Os dois validadores repetem `findById` + `RecursoNaoEncontradoException("Solicitação não encontrada")`. Só isso vira um helper comum. As duas checagens de posse continuam separadas.

A razão é dura: as mensagens diferem — "Esta solicitação pertence a outro prestador" contra "...a outro cliente" — e vão para o campo `message` do `ErrorResponse`. Unificar as checagens exigiria parametrizar a mensagem, e a tentação seguinte seria uniformizá-la. Isso quebraria a spec de `autenticacao`, que exige a mensagem explicando a recusa, e degradaria o diagnóstico de quem consome a API.

A duplicação que sobra é de três linhas e carrega diferença real. A que sai é de duas linhas e não carrega nenhuma.

### Em `AuthService`, unificar os três ramos — e por que o guard de senha nula pode cair

Os três métodos diferem em repository, getters, `TipoUsuario`, e num detalhe: só `autenticarPrestador` tem `prestador.getSenha() == null ||` antes do `passwordEncoder.matches(...)`.

Esse guard é **redundante**, e isso foi verificado no artefato que roda, não presumido da documentação:

- `BCryptPasswordEncoder` estende `AbstractValidatingPasswordEncoder` (spring-security-crypto 7.0.4). O bytecode de `matches(CharSequence, String)` testa os dois argumentos contra null e retorna `false` direto, sem chamar `matchesNonNull`. Não há NPE possível com senha armazenada nula.
- No schema: `administrador.senha` é `NOT NULL`, então o caso nem existe para admin. `dim_cliente.senha` e `dim_prestador.senha` são nullable — e nos dois casos uma senha nula já produz `false`, logo `CredenciaisInvalidasException`, logo 401. Idêntico ao que o guard explícito produz.

Ou seja, o guard nunca mudou o resultado para ninguém: apenas evitava uma chamada. Um helper único parametrizado pelos extratores de cada entidade preserva os três ramos, incluindo a mensagem genérica "E-mail ou senha inválidos" que a spec de `autenticacao` exige para não distinguir e-mail inexistente de senha errada.

*Alternativa considerada:* extrair uma interface comum às três entidades (`ContaAutenticavel`). Rejeitada: mexeria em `Entity/`, que é território de mapeamento JPA, para ganhar tipagem que funções extratoras já resolvem sem tocar no domínio.

## Risks / Trade-offs

- **`AuthService` não tem teste unitário.** É o item de maior risco do lote e o único cuja verificação depende do teste de integração e de conferência manual. → Mitigação: implementar por último, e verificar login dos três tipos com senha certa e errada antes de dar a tarefa por concluída.
- **Injeção por construtor pode mascarar dependência circular** que o campo tolerava por inicialização tardia. → Mitigação: `EstrelaApplicationTests` falha no `mvn test` se o contexto não subir — a detecção é imediata e barata.
- **A fábrica estática no record cria precedente.** Se for adotada sem critério, todo DTO acaba importando `Entity`. → Mitigação: os não-objetivos da proposta limitam a mudança ao único mapeamento duplicado; estender é decisão de uma change futura.
- **Refactor sem mudança de comportamento é invisível na revisão.** Um erro sutil pode passar se a revisão olhar só o diff. → Mitigação: o critério de aceite é a suíte passar **sem edição de teste** — se um teste precisar mudar, o refactor mudou comportamento e a premissa caiu.

## Migration Plan

Não há schema, deploy nem dado envolvido. A ordem é do mais mecânico para o mais sutil, para que qualquer quebra apareça cedo e isolada:

1. Injeção por construtor nos cinco controllers.
2. Mapeamento compartilhado de `PrestadorResponse`.
3. Busca extraída em `FatoServicoService`.
4. Unificação de `AuthService`.

Rollback: cada etapa é um commit `refactor` independente e revertível isoladamente. Nenhuma depende da anterior para funcionar.
