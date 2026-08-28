# Fluxo de descoberta pública

Diagrama **descritivo**: documenta o caminho que um visitante sem conta percorre nas páginas
públicas e quais endpoints cada superfície consome, depois da change
`feature-publico-remover-mocks`.

Todas as rotas abaixo são `permitAll` no `SecurityConfig` e todas filtram por serviço `ATIVO` de
prestador `APROVADO` (RN04).

## Superfícies e endpoints

```mermaid
graph TD
    subgraph publico["Páginas públicas (sem autenticação)"]
        HOME["index.html<br/>home"]
        CAT["pages/servicos.html<br/>catálogo de categorias"]
        LISTA["pages/profissionais-prximos.html<br/>lista de profissionais"]
    end

    subgraph api["API — rotas públicas"]
        CATEGORIAS["GET /servicos-ofertados/categorias"]
        ESTIMATIVA["GET /servicos-ofertados/estimativa"]
        AVALIACOES["GET /avaliacoes/recentes"]
        BUSCAR["GET /servicos-ofertados/buscar"]
    end

    subgraph privado["Exige login"]
        SOLICITAR["POST /servicos<br/>solicitação (US-04)"]
    end

    HOME -->|"vitrine de categorias"| CATEGORIAS
    HOME -->|"faixa de preço ao escolher categoria"| ESTIMATIVA
    HOME -->|"depoimentos"| AVALIACOES
    HOME -->|"submit nativo: ?service=&amp;radius="| LISTA

    CAT -->|"contagem por card e outras categorias"| CATEGORIAS
    CAT -->|"card ou Enter na busca: ?service="| LISTA

    LISTA -->|"busca, filtros e mapa"| BUSCAR
    LISTA -->|"vitrine de novos: apenasSemAvaliacao=true"| BUSCAR
    LISTA -->|"depoimentos"| AVALIACOES
    LISTA -->|"botão Solicitar"| SOLICITAR

    style privado fill:#fff4e6,stroke:#d97706
```

## Filtros da busca e onde são aplicados

```mermaid
flowchart LR
    A["categoria<br/>(obrigatória)"] --> SEL

    subgraph SEL["Seleção de candidatos (banco)"]
        direction TB
        S1{"lat e lon?"}
        S1 -->|sim| S2["todos da categoria"]
        S1 -->|não| S3{"cidade?"}
        S3 -->|sim| S4["da categoria naquela cidade"]
        S3 -->|não| S5["lista vazia"]
    end

    SEL --> MEM

    subgraph MEM["Filtros e ordenação (em memória)"]
        direction TB
        F1["descarta prestador não APROVADO"]
        F2["notaMinima / apenasSemAvaliacao"]
        F3["precoMin / precoMax"]
        F4["Haversine: distância e raio"]
        F5["ordena por distância"]
        F1 --> F2 --> F3 --> F4 --> F5
    end

    MEM --> R["resultados com<br/>distanciaKm e coordenada arredondada"]
```

Os filtros ficam em memória, no mesmo passo que já descartava prestador não aprovado e calculava
distância, porque a busca já materializa a lista de candidatos para o Haversine — acrescentar
predicados ali não soma carga. Uma consulta com parâmetros anuláveis teria de reproduzir em JPQL a
seleção de três ramos acima. Sem paginação, essa escolha não tem custo realizável; quando paginação
entrar, filtro e paginação devem descer juntos para a query.

## Vitrine "Novos na sua região"

```mermaid
sequenceDiagram
    participant V as Visitante
    participant P as Lista de profissionais
    participant API as GET /servicos-ofertados/buscar

    V->>P: escolhe nota mínima 4,5
    P->>API: categoria + localidade + notaMinima=4.5
    API-->>P: só quem alcança 4,5
    Note over P: o filtro necessariamente excluiu<br/>quem ainda não tem nota
    P->>API: mesma busca, apenasSemAvaliacao=true, sem notaMinima
    API-->>P: só prestadores sem nota média
    P-->>V: lista principal + vitrine de novos

    Note over P,API: combinar notaMinima com apenasSemAvaliacao<br/>é recusado com 400 VALIDACAO
```

A vitrine só é exibida quando há filtro de nota ativo. Sem esse filtro, os prestadores sem avaliação
já estão na lista principal, e exibi-los também na vitrine mostraria o mesmo prestador duas vezes.
