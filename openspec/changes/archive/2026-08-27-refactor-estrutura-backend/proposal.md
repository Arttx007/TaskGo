## Why

O baseline `2026-08-27-baseline-aplicacao` acabou de ser promovido: `openspec/specs/` descreve, em 10 capabilities, o comportamento que o sistema tem hoje. Isso destrava um tipo de trabalho que antes era arriscado — mexer na estrutura interna do backend com uma referência escrita do que **não** pode mudar.

Os débitos estruturais já estavam nomeados no `CLAUDE.md` do backend e no `design.md` do baseline, mas nenhum deles aparece nas specs, e isso é correto: spec descreve comportamento observável, e injeção por campo ou código duplicado não têm comportamento. São inconsistências internas — o código novo do projeto usa injeção por construtor e DTOs, enquanto uma parte do código antigo ficou para trás.

Fazer isso agora, e não junto com uma correção, é exigência do próprio projeto: as regras de refactor proíbem alterar comportamento observável, e a convenção de commits proíbe `refactor` compartilhar escopo com `feat`.

## What Changes

- Cinco controllers passam a receber suas dependências por construtor em vez de `@Autowired` em campo, alinhando-os ao padrão que o restante do código já segue: `AdminController`, `DashboardController`, `RelatorioController`, `TempoController` e `LocalizacaoController`.
- O mapeamento de `Prestador` para `PrestadorResponse`, hoje duplicado palavra por palavra entre `PrestadorController` e `AdminController`, passa a existir em um lugar só.
- `FatoServicoService` deixa de repetir a mesma busca de solicitação nos dois validadores de posse, mantendo intactas as duas checagens e suas mensagens distintas.
- `AuthService` deixa de ter três métodos privados quase idênticos para autenticar cliente, prestador e administrador.
- Nenhuma mudança de comportamento observável: rotas, contratos de entrada e saída, status HTTP, códigos e mensagens de erro seguem exatamente como estão. Nenhum **BREAKING**.
- Nenhum arquivo de teste é alterado. A suíte atual, com 32 testes, é o critério de aceite.

## Não-objetivos

- **Corrigir qualquer defeito documentado no baseline.** São seis: seletor de estrelas invertido, 500 em upload acima do limite, 500 ao excluir serviço já solicitado, motivo de rejeição de KYC descartado, 200 no cadastro de cliente contra 201 no de prestador, e rotas analíticas abertas a qualquer conta autenticada. Cada um muda comportamento e exige seu próprio delta `MODIFIED` — misturar aqui violaria as regras de refactor do projeto e esconderia a correção dentro de um diff estrutural.
- **Padronizar nomes de pacote** (`Controller/`, `DTO/`, `Entity/`, `Service/` em maiúscula contra `config/`, `exception/`, `repository/`, `security/` em minúscula). Renome que difere só na caixa é hostil ao git no Windows, e o diff alcançaria quase todo import do projeto — risco desproporcional ao ganho.
- **Uniformizar os demais mapeamentos de DTO.** `ClienteResponse`, `ServicoOfertadoResponse` e `SolicitacaoResponse` têm um mapeamento cada; movê-los seria busca por consistência, não remoção de duplicação.
- Frontend, migrations, dependências, configuração e qualquer feature nova.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

Nenhuma. A change declara `skip_specs: true` no seu `.openspec.yaml`: como nenhum comportamento muda, nenhuma spec deve mudar. Se a implementação exigir alterar uma spec, isso é sinal de que deixou de ser refactor e a proposta precisa ser revista antes de continuar.

## Impact

- **Código afetado:** nove arquivos em `backend-estrela-main/src/main/java/com/example/Estrela/` — seis em `Controller/` (cinco pela injeção por construtor, mais `PrestadorController` pelo mapeamento compartilhado), `DTO/PrestadorResponse.java`, `Service/FatoServicoService.java` e `Service/AuthService.java`.
- **Sem impacto** em API, banco de dados, migrations, build, dependências ou frontend.
- **Rede de proteção:** `FluxoCompletoIntegrationTest` exercita login dos três tipos de conta e o ciclo completo de atendimento; `FatoServicoServiceTest` cobre os três caminhos de posse negada. São essas duas classes que reprovam o refactor se ele escorregar para mudança de comportamento.
