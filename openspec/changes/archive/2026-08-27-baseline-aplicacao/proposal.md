## Why

O repositório tem PRD (`spec.md`), plano técnico (`plan.md`) e checklist de execução (`tasks.md`), mas `openspec/specs/` está vazio: não existe nenhuma especificação versionada do que a aplicação **faz hoje**. Sem esse baseline, toda change futura vira um delta sobre o nada — não há contra o que validar se uma alteração preserva ou quebra o comportamento existente, e a regra de refactor do projeto ("não alterar comportamento observável") não tem referência escrita.

Esta change registra o comportamento atual do MVP (US-01..US-10), extraído do código e dos testes que já existem. Ela não propõe nenhuma alteração.

## What Changes

- Cria as especificações de baseline das capabilities que compõem o MVP, descrevendo o comportamento **observável hoje**: contratos HTTP (rotas, status, códigos de erro), a máquina de estados da solicitação, as regras RN01–RN04 como implementadas, e o comportamento do frontend que consome a API.
- Registra também, como parte do baseline, as inconsistências e limitações reais de hoje — por exemplo: `POST /clientes` responde 200 enquanto `POST /prestadores` responde 201; a cobrança de pagamento e o repasse Pix são simulados; partes do painel do cliente são decorativas e não chamam a API. Documentar não é endossar: são comportamento observável e precisam estar no baseline para que uma mudança futura seja avaliada contra a realidade, não contra uma versão idealizada.
- Nenhuma mudança de código, schema, endpoint, migration ou UI. Nenhum comportamento novo, nenhuma correção.
- Não há **BREAKING**: a change é puramente documental.

## Não-objetivos

- **Corrigir** qualquer inconsistência documentada aqui. Cada correção deve ser sua própria change, com sua própria proposta.
- Propor melhorias, refactors ou features — explicitamente fora desta etapa, conforme o pedido.
- Documentar Fase 2/3 (US-11..US-21): não estão implementadas, e spec descreve comportamento existente.
- Descrever implementação (nomes de classe, estrutura de pacotes, decisões de arquitetura). Isso já vive em `plan.md` e nos `CLAUDE.md`; as specs aqui ficam no nível do comportamento observável.

## Capabilities

### New Capabilities

- `autenticacao`: login único para CLIENTE/PRESTADOR/ADMIN com emissão de JWT, autorização por papel e por dono do recurso, rotas públicas vs. protegidas, e o comportamento de sessão do frontend (persistência, expiração, guarda de página).
- `cadastro-cliente`: cadastro e listagem de clientes, validação dos campos de entrada e hash de senha.
- `cadastro-prestador-kyc`: cadastro do prestador, envio de documentos, ciclo de status KYC (PENDENTE/APROVADO/REJEITADO) e o gate RN04 sobre o que um prestador não verificado pode fazer (US-01).
- `catalogo-servicos`: publicação, edição, ativação/inativação e exclusão dos serviços ofertados pelo prestador, incluindo a validação de dono (US-02).
- `busca-servicos`: busca pública de serviços ativos por categoria, com ordenação por distância quando há coordenadas e fallback por cidade quando não há (US-03).
- `solicitacao-servico`: a máquina de estados RN02 ponta a ponta — solicitar, aceitar, recusar, concluir, avaliar e cancelar — com as validações de dono e de estado de cada transição (US-04, US-05, US-07, US-09, US-10).
- `pagamento-custodia`: cálculo da taxa RN01, cobrança via gateway, custódia do valor (RETIDO/LIBERADO/ESTORNADO) e crédito do líquido no saldo do prestador (US-06, RN03).
- `carteira-saque`: consulta de saldo do prestador e saque via Pix limitado ao saldo disponível (US-08).
- `administracao`: fila de KYC pendente, aprovação/rejeição, consulta dos documentos enviados e ajuste dos parâmetros de negócio sem deploy.
- `relatorios-analiticos`: os endpoints de contagem agregada (`/dashboard`, `/relatorio`) e o CRUD das dimensões do modelo estrela (`/localizacoes`, `/tempos`), incluindo quem hoje consegue acessá-los.

### Modified Capabilities

Nenhuma. `openspec/specs/` está vazio — todas as capabilities acima são novas.

## Impact

- **Código de aplicação:** nenhum arquivo alterado. A change produz apenas artefatos de planejamento em `openspec/changes/baseline-aplicacao/`.
- **Fonte da evidência:** o baseline é derivado do código em `backend-estrela-main/src/main/java/` e `ProjetoTaskGoFinalizado-main/assets/js/`, e dos testes existentes (`FluxoCompletoIntegrationTest`, os testes de Service com Mockito e os `@DataJpaTest` de repository). Onde o comportamento não é coberto por teste, a spec é derivada do código e isso é sinalizado.
- **Efeito posterior:** ao ser sincronizado (`/opsx:sync`) ou arquivado, este baseline popula `openspec/specs/`, passando a ser o alvo de delta de toda change seguinte.
- **Sem impacto** em API, banco de dados, dependências ou build.
