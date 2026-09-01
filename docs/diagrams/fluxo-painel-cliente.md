# Fluxo do painel do cliente

Diagrama **descritivo**: documenta cada aba de `pages/painel-cliente.html` e os endpoints que ela
consome depois da change `feature-painel-cliente-remover-mocks`.

Todas as rotas abaixo são autenticadas — nenhuma é `permitAll` no `SecurityConfig` — e todas as que
começam em `/clientes/me` resolvem o cliente pelo token, nunca por id na URL. As rotas de
solicitação (`/servicos/{id}`) são restritas às duas partes envolvidas: qualquer terceiro recebe
403 `ACESSO_NEGADO`.

## Abas e endpoints

```mermaid
graph TD
    subgraph painel["pages/painel-cliente.html"]
        BUSCA["Buscar Serviços"]
        PEDIDOS["Meus Pedidos"]
        PAGTOS["Pagamentos"]
        FAVS["Favoritos"]
        CONFIG["Configurações"]
        ACOMP["Em Andamento<br/>(revelada por solicitação em<br/>ACEITO ou EM_ANDAMENTO)"]
        AJUDA["Central de Ajuda<br/>(FAQ local, sem API)"]
        AVISOS["Modal de Atividades"]
        CHAT["Modal de Conversa<br/>assets/js/chat-servico.js"]
    end

    subgraph publicas["API — rotas públicas reusadas"]
        CATEGORIAS["GET /servicos-ofertados/categorias"]
        BUSCAR["GET /servicos-ofertados/buscar"]
        ESTIMATIVA["GET /servicos-ofertados/estimativa"]
    end

    subgraph conta["API — conta do cliente"]
        PERFIL["GET / PUT /clientes/me"]
        FOTO["PUT /clientes/me/foto"]
        DESATIVAR["DELETE /clientes/me"]
        ENDERECOS["GET / POST / PUT / DELETE<br/>/clientes/me/enderecos"]
        EXTRATO["GET /clientes/me/pagamentos"]
        FAVORITOS["GET / POST / DELETE<br/>/clientes/me/favoritos"]
        NOTIFS["GET /clientes/me/notificacoes"]
        CATALOGO["GET /prestadores/{id}/servicos-ofertados"]
    end

    subgraph solicitacao["API — ciclo da solicitação"]
        CRIAR["POST /servicos"]
        MINHAS["GET /servicos/minhas"]
        DETALHE["GET /servicos/{id}"]
        PAGAR["POST /servicos/{id}/pagamento"]
        CANCELAR["PUT /servicos/{id}/cancelar"]
        AVALIAR["PUT /servicos/{id}/avaliar"]
        MSGS["GET / POST /servicos/{id}/mensagens<br/>PUT .../lidas"]
    end

    BUSCA -->|"tags de categoria"| CATEGORIAS
    BUSCA -->|"busca por categoria reconciliada"| BUSCAR
    BUSCA -->|"Contratar, com endereço padrão"| CRIAR
    BUSCA -->|"coração no card"| FAVORITOS
    BUSCA -->|"simulador de custo"| ESTIMATIVA

    PEDIDOS --> MINHAS
    PEDIDOS -->|"Pagar"| PAGAR
    PEDIDOS -->|"Cancelar: lê a retenção prevista antes"| DETALHE
    PEDIDOS -->|"Cancelar"| CANCELAR
    PEDIDOS -->|"Avaliar"| AVALIAR
    PEDIDOS -->|"Acompanhar"| ACOMP

    PAGTOS --> EXTRATO

    FAVS --> FAVORITOS
    FAVS -->|"Contratar: escolhe o serviço"| CATALOGO
    FAVS -->|"serviço escolhido"| CRIAR

    CONFIG --> PERFIL
    CONFIG --> FOTO
    CONFIG --> ENDERECOS
    CONFIG -->|"Zona de Perigo"| DESATIVAR

    ACOMP --> DETALHE
    ACOMP --> CHAT
    AVISOS --> NOTIFS
    CHAT --> MSGS

    style publicas fill:#eef7ff,stroke:#1d4ed8
    style AJUDA fill:#f5f5f5,stroke:#888
```

`Central de Ajuda` não consome endpoint algum: o campo de busca filtra o próprio FAQ da página. Não
existe base de artigos no backend, e a alternativa anterior — um botão que apenas confirmava a
consulta — era exatamente o tipo de superfície que esta change removeu.

## Ciclo de vida da solicitação, com as duas telas

```mermaid
stateDiagram-v2
    [*] --> SOLICITADO: cliente<br/>POST /servicos<br/>(criadoEm)

    SOLICITADO --> ACEITO: prestador<br/>PUT /{id}/aceitar<br/>(aceitoEm + pinConfirmacao)
    SOLICITADO --> RECUSADO: prestador<br/>PUT /{id}/recusar
    SOLICITADO --> CANCELADO: cliente<br/>PUT /{id}/cancelar

    ACEITO --> EM_ANDAMENTO: prestador<br/>PUT /{id}/iniciar + pin<br/>(iniciadoEm)
    ACEITO --> CANCELADO: qualquer parte<br/>estorno integral

    EM_ANDAMENTO --> CONCLUIDO: prestador<br/>PUT /{id}/concluir<br/>(concluidoEm, libera líquido)
    EM_ANDAMENTO --> CANCELADO: cancelamento<br/>estorno integral OU parcial

    CONCLUIDO --> AVALIADO: cliente<br/>PUT /{id}/avaliar

    RECUSADO --> [*]
    CANCELADO --> [*]
    AVALIADO --> [*]

    note right of ACEITO
        Pagar exige ACEITO.
        Iniciar exige pagamento RETIDO
        e o pin correto — o pin vai
        na resposta apenas do cliente.
    end note
```

`ACEITO → CONCLUIDO` deixou de existir: sem o passo de iniciar, o prestador não tinha como provar
que esteve no local.

## Onde a taxa de cancelamento incide (RN03)

```mermaid
flowchart TD
    C["PUT /servicos/{id}/cancelar"] --> Q1{"quem cancelou?"}

    Q1 -->|prestador| INT["estorno integral"]
    Q1 -->|cliente| Q2{"estado?"}

    Q2 -->|SOLICITADO ou ACEITO| INT
    Q2 -->|EM_ANDAMENTO| Q3{"now &gt; iniciadoEm +<br/>carencia-minutos?"}

    Q3 -->|não| INT
    Q3 -->|sim| Q4{"distância apurável?<br/>(endereço e prestador<br/>com lat/lon)"}

    Q4 -->|"não"| PERTO["percentual menor<br/>taxa-percentual-perto"]
    Q4 -->|"sim, ≤ limiar-distancia-km"| PERTO
    Q4 -->|"sim, &gt; limiar-distancia-km"| LONGE["taxa-percentual-longe"]

    PERTO --> TETO
    LONGE --> TETO

    TETO["aplica taxa-teto<br/>e limita ao valor pago"] --> PARCIAL

    PARCIAL["ESTORNADO_PARCIAL<br/>valorEstornado + valorTaxaCancelamento<br/>taxa creditada ao prestador"]
    INT["ESTORNADO<br/>valor integral devolvido"]

    style PARCIAL fill:#fff4e6,stroke:#d97706
```

Distância não apurável aplica o percentual **menor**, não o maior: a lacuna é do cadastro, não do
cliente. Em qualquer caminho, nenhum pagamento daquela solicitação permanece em `RETIDO` depois do
cancelamento, e `taxaCancelamentoPrevista` em `GET /servicos/{id}` diz de antemão quanto seria
retido — é o valor que o painel exibe antes de o cliente confirmar.

## Código de confirmação: quem o vê

```mermaid
sequenceDiagram
    participant CL as Cliente
    participant API as API
    participant PR as Prestador

    PR->>API: PUT /servicos/{id}/aceitar
    API->>API: SecureRandom → pin de 4 dígitos<br/>gravado em fato_servicos.pin_confirmacao
    API-->>PR: SolicitacaoResponse<br/>pinConfirmacao = null
    API-->>CL: SolicitacaoResponse<br/>pinConfirmacao = "4821"

    CL->>API: POST /servicos/{id}/pagamento
    API-->>CL: pagamento RETIDO em custódia

    Note over CL,PR: prestador chega ao local
    CL-->>PR: informa "4821" pessoalmente

    PR->>API: PUT /servicos/{id}/iniciar { pin: "4821" }
    API-->>PR: EM_ANDAMENTO (iniciadoEm)

    Note over API: pin errado → 403 ACESSO_NEGADO,<br/>e o pin armazenado não muda
```

O preenchimento condicional acontece num único ponto do service, ao montar a resposta — não em cada
controller — para que nenhuma rota nova vaze o código por esquecimento.

## Avisos de atividade: apurados, não armazenados

```mermaid
flowchart LR
    E["estado atual do cliente"] --> N["NotificacaoService"]

    N --> T1["ACEITO sem pagamento<br/>PAGAMENTO_PENDENTE"]
    N --> T2["EM_ANDAMENTO<br/>ATENDIMENTO_EM_ANDAMENTO"]
    N --> T3["CONCLUIDO sem avaliação<br/>AVALIACAO_PENDENTE"]
    N --> T4["RECUSADO<br/>SOLICITACAO_RECUSADA"]
    N --> T5["CANCELADO com estorno<br/>CANCELAMENTO_ESTORNADO"]
    N --> T6["mensagens não lidas<br/>MENSAGENS_NAO_LIDAS"]

    T1 --> R["GET /clientes/me/notificacoes<br/>do mais recente ao mais antigo"]
    T2 --> R
    T3 --> R
    T4 --> R
    T5 --> R
    T6 --> R
```

Não há tabela de notificação nem campo de leitura: o aviso existe enquanto a pendência existe e
desaparece quando ela é resolvida. Avaliar o serviço faz o aviso de avaliação sair da lista sem que
nada seja marcado como lido — por isso o painel do cliente não tem "marcar todas como lidas".
