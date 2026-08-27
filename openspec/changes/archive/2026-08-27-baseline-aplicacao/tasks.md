## 1. Conferência contra a suíte de testes existente

Arquivos consultados: `backend-estrela-main/src/test/java/com/example/Estrela/**`. Nenhum arquivo de aplicação é alterado neste grupo.

- [x] 1.1 Rodar `mvn test` a partir de `backend-estrela-main/` e confirmar que a suíte passa inteira antes de qualquer conferência — um baseline extraído de suíte vermelha não vale; verificação: saída `BUILD SUCCESS` com zero falhas
- [x] 1.2 Conferir os cenários de `specs/solicitacao-servico/spec.md` contra `FluxoCompletoIntegrationTest.percorreOCicloCompletoDeUmAtendimento`, confirmando estados e status HTTP de cada transição; verificação: cada cenário do ciclo tem passo correspondente no teste, ou fica marcado como não coberto
- [x] 1.3 Conferir os cenários de `specs/pagamento-custodia/spec.md` contra `TaxaServiceTest` e `PagamentoServiceTest`, confirmando as duas faixas de taxa e as transições de custódia; verificação: as faixas fixa e percentual e os estados retido/liberado/estornado aparecem nos testes
- [x] 1.4 Conferir os cenários de `specs/carteira-saque/spec.md` contra `SaqueServiceTest`, confirmando o débito do saldo e a recusa por saldo insuficiente; verificação: ambos os caminhos estão cobertos por teste
- [x] 1.5 Conferir o cenário de autorização cruzada de `specs/autenticacao/spec.md` contra `FluxoCompletoIntegrationTest.prestadorNaoPodeAceitarSolicitacaoDeOutroPrestador`; verificação: o teste afirma 403 com código `ACESSO_NEGADO`

## 2. Verificação manual do backend não coberto por teste

Exige a aplicação em execução (`mvn spring-boot:run` com PostgreSQL local). Nenhum arquivo de aplicação é alterado neste grupo.

- [x] 2.1 Resolver a questão aberta do `design.md`: chamar uma rota protegida sem token e registrar o status HTTP exato devolvido; verificação: status observado (401 ou 403) anotado e o cenário "Rota protegida sem token" em `specs/autenticacao/spec.md` atualizado para citá-lo
- [x] 2.2 Verificar os cenários de `specs/administracao/spec.md` percorrendo a fila de KYC, a consulta de documento e a decisão de aprovar e rejeitar; verificação: cada cenário reproduzido, incluindo a confirmação de que o motivo da rejeição não é persistido
- [x] 2.3 Verificar os cenários de ajuste de parâmetro de negócio, alterando um valor de taxa e refazendo um pagamento; verificação: o novo valor passa a valer sem reinício da aplicação
- [x] 2.4 Verificar os cenários de `specs/relatorios-analiticos/spec.md`, incluindo o acesso de conta não administradora a `/dashboard`, `/relatorio`, `/clientes`, `/localizacoes` e `/tempos`; verificação: cada rota confirmada como acessível ou negada conforme descrito na spec
- [x] 2.5 Verificar os cenários de recusa de arquivo de `specs/cadastro-prestador-kyc/spec.md` enviando um arquivo de formato não aceito e um acima do limite de tamanho; verificação: resposta 400 com código `ARQUIVO_INVALIDO` no primeiro caso, e o comportamento do segundo anotado na spec
- [x] 2.6 Verificar a diferença de status entre `POST /clientes` e `POST /prestadores` descrita em `specs/cadastro-cliente/spec.md`; verificação: 200 e 201 respectivamente, confirmados na resposta real

## 3. Verificação do frontend

Exige backend em execução e o frontend servido por HTTP (`npx serve .` em `ProjetoTaskGoFinalizado-main/`). Nenhum arquivo de aplicação é alterado neste grupo.

- [x] 3.1 Verificar os cenários de sessão de `specs/autenticacao/spec.md`: sessão expirada descartada, papel divergente redirecionado e usuário já logado desviado da tela de login; verificação: os três comportamentos reproduzidos no navegador, o primeiro adulterando a expiração do token guardado
- [x] 3.2 Verificar o cenário de aviso de KYC no painel do prestador para os status pendente e rejeitado; verificação: mensagens distintas exibidas para cada status
- [x] 3.3 Percorrer o fluxo do cliente ponta a ponta pela interface — buscar, solicitar, pagar, acompanhar e avaliar; verificação: cada passo reflete a mudança de estado correspondente no backend
- [x] 3.4 Confirmar quais áreas do painel do cliente não chamam a API (checkout de demonstração, chat, recibo, simulador de custo) e que a distinção entre elas e os fluxos reais está registrada nas specs; verificação: nenhuma dessas áreas gera requisição de rede ao ser usada

## 4. Fechamento do baseline

- [x] 4.1 Corrigir nas specs todo cenário que a verificação dos grupos 1 a 3 mostrar impreciso, mantendo a descrição fiel ao comportamento observado e não ao desejado; verificação: `openspec validate baseline-aplicacao --strict` continua passando
- [x] 4.2 Remover a seção Open Questions do `design.md` depois que a questão 2.1 for respondida; verificação: o documento não deixa pergunta em aberto
- [x] 4.3 Promover o baseline para `openspec/specs/` com `/opsx:sync` ou `/opsx:archive`; verificação: `openspec/specs/` passa a conter as dez capabilities e `openspec list` reflete o novo estado da change

Observação de convenção: esta change é `docs` — não é `feat` nem `refactor`. Por isso não há tarefa de atualização de `CHANGELOG.md` (a convenção do projeto pede entrada de changelog para `feat`) nem tarefa de migration, e nenhum arquivo de `backend-estrela-main/src/main/` ou `ProjetoTaskGoFinalizado-main/assets/` é alterado em nenhum grupo.
