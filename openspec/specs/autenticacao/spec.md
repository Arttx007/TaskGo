## Purpose

Define como a aplicação identifica quem está chamando (cliente, prestador ou administrador), o que cada papel pode acessar, como a posse de um recurso é verificada, e como a sessão se comporta no frontend.

## Requirements

### Requirement: Login único para os três tipos de conta

O sistema SHALL autenticar Cliente, Prestador e Administrador por um único endpoint `POST /auth/login`, que recebe `email`, `senha` e `tipoUsuario` (`CLIENTE`, `PRESTADOR` ou `ADMIN`) e responde com `token`, `tipoUsuario`, `id` e `nome`. O `tipoUsuario` seleciona contra qual conjunto de contas as credenciais são conferidas: o mesmo e-mail em tipos diferentes corresponde a contas distintas.

#### Scenario: Login bem-sucedido
- **WHEN** uma conta existente envia e-mail e senha corretos junto do seu `tipoUsuario`
- **THEN** a resposta é 200 com um token JWT e os dados básicos da conta (`id`, `nome`, `tipoUsuario`)

#### Scenario: Credenciais inválidas não revelam qual campo falhou
- **WHEN** o e-mail não existe para o `tipoUsuario` informado, ou a senha não confere
- **THEN** a resposta é 401 com código de erro `CREDENCIAIS_INVALIDAS` e a mensagem genérica "E-mail ou senha inválidos", sem distinguir e-mail inexistente de senha errada

#### Scenario: Tipo de usuário ausente
- **WHEN** a requisição de login não informa `tipoUsuario`
- **THEN** a resposta é 400 com código `VALIDACAO` e o campo ausente descrito em `fieldErrors`

#### Scenario: Conta de prestador sem senha cadastrada
- **WHEN** um prestador cujo registro não tem senha definida tenta autenticar
- **THEN** a resposta é 401 `CREDENCIAIS_INVALIDAS`, igual a qualquer outra falha de credencial

### Requirement: Token JWT carrega identidade e papel

O token emitido SHALL trazer o identificador da conta como subject e as claims `role` e `nome`, e SHALL expirar após o tempo configurado (120 minutos por padrão). A API MUST ser stateless: cada requisição é autenticada apenas pelo token apresentado, sem sessão em servidor e sem consulta ao banco para autenticar.

#### Scenario: Requisição autenticada por token
- **WHEN** uma requisição envia o header `Authorization: Bearer <token>` com token válido
- **THEN** a operação é executada em nome da conta identificada nas claims do token

#### Scenario: Token inválido, malformado ou expirado
- **WHEN** o header traz um token com assinatura inválida, formato inválido ou prazo vencido
- **THEN** o token é ignorado e a requisição prossegue como não autenticada, sujeita à regra de acesso da rota

### Requirement: Rotas públicas e rotas protegidas

O sistema SHALL permitir acesso sem autenticação apenas a `POST /auth/login`, `POST /clientes`, `POST /prestadores` e `GET /servicos-ofertados/buscar`. Rotas sob `/admin/` SHALL exigir papel `ADMIN`. Todas as demais rotas SHALL exigir uma requisição autenticada.

#### Scenario: Cadastro e busca sem estar logado
- **WHEN** um visitante não autenticado cadastra uma conta ou busca serviços por categoria
- **THEN** a operação é executada normalmente, sem exigir token

#### Scenario: Rota protegida sem token
- **WHEN** uma requisição sem token, ou com token inválido, atinge uma rota que exige autenticação
- **THEN** a resposta é 403 com corpo vazio e a operação não é executada

#### Scenario: Conta não administradora acessa rota administrativa
- **WHEN** uma conta autenticada como `CLIENTE` ou `PRESTADOR` chama qualquer rota sob `/admin/`
- **THEN** a resposta é 403 com corpo vazio e a operação não é executada

### Requirement: Autorização por dono do recurso

Além do papel, operações sobre um recurso identificado SHALL verificar que a conta autenticada é dona daquele recurso. A verificação usa a identidade do token, nunca um identificador enviado pelo cliente da API.

#### Scenario: Prestador age sobre solicitação de outro prestador
- **WHEN** o prestador B tenta aceitar uma solicitação dirigida ao prestador A
- **THEN** a resposta é 403 com código `ACESSO_NEGADO` e a solicitação permanece no estado anterior

#### Scenario: Prestador consulta saldo de outro prestador
- **WHEN** um prestador consulta o saldo informando o identificador de outro prestador
- **THEN** a resposta é 403 `ACESSO_NEGADO` e nenhum saldo é revelado

### Requirement: Formato único de resposta de erro

Toda resposta de erro **originada na aplicação** SHALL ter o mesmo corpo: `timestamp`, `status`, `error` (código estável), `message`, `path` e `fieldErrors`. O campo `error` SHALL ser um código estável consumível por clientes, e não a mensagem legível.

Recusas originadas na **camada de segurança** — ausência de token, token inválido e papel insuficiente — SHALL responder 403 com corpo vazio, sem esse formato. Como consequência, cliente algum consegue distinguir "não autenticado" de "não é o dono do recurso" apenas pelo status: ambos são 403, e só a presença do corpo os separa.

#### Scenario: Negação pela camada de segurança não traz corpo
- **WHEN** a requisição é recusada por falta de token, token inválido ou papel insuficiente
- **THEN** a resposta é 403 com corpo de tamanho zero, sem `error`, `message` nem `path`

#### Scenario: Negação por posse de recurso traz corpo completo
- **WHEN** a requisição é autenticada mas recusada porque quem chama não é o dono do recurso
- **THEN** a resposta é 403 com o corpo padrão, `error` igual a `ACESSO_NEGADO` e a mensagem explicando a recusa

#### Scenario: Erro de validação de entrada
- **WHEN** uma requisição falha na validação dos campos de entrada
- **THEN** a resposta é 400, `error` é `VALIDACAO` e `fieldErrors` mapeia cada campo inválido à sua mensagem

#### Scenario: Falha inesperada
- **WHEN** ocorre uma exceção não prevista durante o processamento
- **THEN** a resposta é 500 com `error` igual a `ERRO_INTERNO` e uma mensagem genérica, sem expor detalhes internos

### Requirement: Sessão e guarda de página no frontend

O frontend SHALL guardar token e dados do usuário no armazenamento local do navegador e SHALL descartar a sessão cujo token já expirou antes de considerá-la válida. Páginas restritas SHALL exigir uma sessão válida do tipo esperado.

#### Scenario: Sessão expirada é descartada
- **WHEN** uma página restrita carrega e o token guardado tem prazo de expiração no passado
- **THEN** a sessão é apagada do armazenamento local e o usuário é redirecionado para a tela de login

#### Scenario: Papel diferente do exigido pela página
- **WHEN** um usuário autenticado como cliente abre o painel do prestador
- **THEN** a página não carrega dados e ele termina no painel do próprio tipo de conta, não na tela de login: a guarda o envia ao login, que por sua vez reconhece a sessão válida e o devolve ao painel correspondente

#### Scenario: Usuário já autenticado abre a tela de login
- **WHEN** alguém com sessão válida acessa a tela de login
- **THEN** é redirecionado direto para o painel correspondente ao seu tipo de conta

#### Scenario: Erro da API chega tratado à interface
- **WHEN** uma chamada à API responde com status fora da faixa 2xx
- **THEN** a interface recebe um erro contendo o status HTTP e os erros por campo, e exibe a mensagem devolvida pela API
