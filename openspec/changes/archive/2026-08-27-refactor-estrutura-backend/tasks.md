Todos os caminhos são relativos a `backend-estrela-main/src/main/java/com/example/Estrela/`. Nenhuma tarefa altera arquivo de teste, recurso, migration ou frontend — se alguma precisar, o refactor mudou comportamento e a proposta deve ser revista antes de prosseguir.

## 1. Linha de base antes de tocar em qualquer arquivo

- [x] 1.1 Rodar `mvn test` a partir de `backend-estrela-main/` e registrar o resultado como referência; verificação: `BUILD SUCCESS` com 32 testes e 0 falhas — um refactor que parte de suíte vermelha não tem como provar que preservou comportamento
- [x] 1.2 Confirmar que a árvore de trabalho não tem alteração pendente no backend, para que o diff final do refactor seja isolável; verificação: `git status --short backend-estrela-main/` sem arquivo modificado

## 2. Injeção por construtor nos controllers

Arquivos: `Controller/DashboardController.java`, `Controller/RelatorioController.java`, `Controller/TempoController.java`, `Controller/LocalizacaoController.java`, `Controller/AdminController.java`.

- [x] 2.1 Converter `DashboardController` (3 repositories) e `RelatorioController` (1 repository) para injeção por construtor, com campos `private final` e sem `@Autowired`; verificação: `mvn test` continua verde e `GET /dashboard` e `GET /relatorio` seguem devolvendo os mesmos campos, incluindo `agendados`
- [x] 2.2 Converter `TempoController` e `LocalizacaoController` (1 repository cada) da mesma forma; verificação: `mvn test` verde — `FluxoCompletoIntegrationTest` cria uma localização e depende de `id_localizacao` na resposta, então uma quebra de contrato aqui reprova o teste
- [x] 2.3 Converter `AdminController` fundindo os 3 repositories em campo com o construtor que já existe para `AdminService`, resultando em um único construtor com 4 dependências; verificação: `mvn test` verde e `GET /admin/dashboard` ainda devolve a linha de texto com as três contagens, não JSON
- [x] 2.4 Confirmar que não sobrou injeção por campo no backend; verificação: `grep -rn "@Autowired" backend-estrela-main/src/main/java/` não retorna nenhuma ocorrência

## 3. Mapeamento duplicado de `PrestadorResponse`

Arquivos: `DTO/PrestadorResponse.java`, `Controller/PrestadorController.java`, `Controller/AdminController.java`.

- [x] 3.1 Adicionar a fábrica estática `de(Prestador)` em `PrestadorResponse`, reproduzindo exatamente a ordem e a origem dos campos do mapeamento atual, com Javadoc; verificação: o corpo da fábrica é idêntico campo a campo ao método que hoje existe nos dois controllers
- [x] 3.2 Substituir o `paraResposta` privado de `PrestadorController` e o de `AdminController` por chamadas à fábrica, removendo os dois métodos; verificação: `mvn test` verde e `grep -c "new PrestadorResponse" backend-estrela-main/src/main/java/` retorna 1
- [x] 3.3 Conferir que as respostas dos dois controllers seguem idênticas, comparando `GET /prestadores/{id}` e `GET /admin/prestadores/pendentes` com o formato descrito na spec de `cadastro-prestador-kyc`; verificação: mesmos campos, mesma ordem, `statusKyc` presente

## 4. Busca duplicada em `FatoServicoService`

Arquivo: `Service/FatoServicoService.java`.

- [x] 4.1 Extrair a busca comum (`findById` mais `RecursoNaoEncontradoException("Solicitação não encontrada")`) para um helper privado, passando os dois validadores de posse a usá-lo; verificação: as duas checagens de posse continuam separadas, cada uma com sua mensagem original
- [x] 4.2 Garantir que as mensagens de recusa não foram unificadas nem reescritas; verificação: `grep -n "pertence a outro" backend-estrela-main/src/main/java/` ainda mostra as duas variantes, prestador e cliente
- [x] 4.3 Rodar a classe que cobre estes caminhos; verificação: `mvn test "-Dtest=FatoServicoServiceTest"` passa com os 14 testes, incluindo `aceitarRejeitaPrestadorQueNaoEDono`, `concluirRejeitaClienteTentandoConcluir` e `cancelarRejeitaQuemNaoParticipaDaSolicitacao`

## 5. Ramos duplicados em `AuthService`

Arquivo: `Service/AuthService.java`. É o item mais sutil do lote e o único sem teste unitário próprio — por isso vem por último.

- [x] 5.1 Unificar `autenticarCliente`, `autenticarPrestador` e `autenticarAdmin` em um helper privado parametrizado pela conta encontrada e pelos extratores de senha, id e nome, mais o `TipoUsuario`; verificação: a mensagem continua a genérica "E-mail ou senha inválidos" para os três tipos, sem distinguir e-mail inexistente de senha errada
- [x] 5.2 Remover o guard `getSenha() == null` do ramo do prestador, agora coberto pelo próprio encoder; verificação: documentar no commit que `AbstractValidatingPasswordEncoder.matches` já devolve `false` para senha armazenada nula, conforme apurado no design
- [x] 5.3 Verificar login pela API com a aplicação no ar, para os três tipos de conta, com senha correta e com senha errada; verificação: 200 com token nos três acertos e 401 `CREDENCIAIS_INVALIDAS` nos três erros
- [x] 5.4 Verificar o caso de senha armazenada nula, que era o único motivo do guard; verificação: uma conta de prestador sem senha definida responde 401 `CREDENCIAIS_INVALIDAS`, e não 500

## 6. Fechamento

- [x] 6.1 Rodar a suíte completa e confirmar que nenhum arquivo de teste foi tocado; verificação: `mvn test` com 32 testes e 0 falhas, e `git status --short backend-estrela-main/src/test/` vazio
- [x] 6.2 Confirmar que o diff ficou restrito ao esperado; verificação: `git diff --name-only backend-estrela-main/src/main/java/` lista exatamente os nove arquivos previstos, sem teste, recurso, migration ou código de frontend
- [x] 6.3 Confirmar que as specs promovidas seguem intactas; verificação: `openspec validate --specs --strict` com 10 passed e nenhum arquivo sob `openspec/specs/` alterado depois do sync — `git status` não serve aqui porque `openspec/` inteiro ainda é untracked no repositório
- [x] 6.4 Registrar os commits separados por etapa, todos com escopo `refactor` e no imperativo, sem misturar com `feat`; verificação: `git log --oneline` mostra um commit por etapa dos grupos 2 a 5

Observação de convenção: esta change é `refactor` — não é `feat`. Por isso não há tarefa de atualização de `CHANGELOG.md`, nem migration Flyway, nem alteração de spec. O artefato `specs` está marcado como `skipped` no `.openspec.yaml` justamente porque nenhum comportamento muda.
