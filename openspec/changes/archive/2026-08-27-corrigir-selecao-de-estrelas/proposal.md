## Why

O seletor de estrelas da avaliação grava a nota invertida: clicar na estrela mais à direita, que todo mundo lê como "excelente", registra **1**. Clicar na mais à esquerda registra 5.

Pior que o número errado é a ausência de sinal. O CSS acende a estrela marcada e todas as seguintes no DOM, e como a marcação caminha para o lado oposto ao da renderização, **qualquer clique acende as cinco estrelas**. O cliente não tem como perceber que enviou o contrário do que quis, e o prestador recebe uma nota que ninguém pretendeu dar.

O banco já mostra o efeito: existe uma avaliação com o comentário "Excelente atendimento, muito profissional!" gravada com nota **1**.

Isso corrompe a reputação dos prestadores em silêncio — `nota_media` é recalculada a cada avaliação e alimenta o que o cliente vê na busca. Cada dia com o defeito no ar é mais dado inutilizável.

## What Changes

- O seletor passa a registrar a nota que corresponde à posição clicada: estrela mais à direita vale 5, mais à esquerda vale 1.
- O preenchimento visual passa a refletir a seleção — as estrelas acesas vão da esquerda até a clicada, em vez de acenderem todas.
- **BREAKING** para quem já dependia do comportamento anterior: a mesma interação passa a produzir nota diferente. É exatamente a intenção da change, e o baseline documentava o comportamento antigo como o comportamento atual.
- Nenhuma mudança no backend: a API já valida e persiste corretamente a nota que recebe. O defeito é só de apresentação.
- Nenhuma alteração nos dados já gravados.

## Não-objetivos

- **Corrigir, inverter ou apagar as avaliações existentes.** As três do banco são registros de teste, e nenhuma é dado real de usuário. Mexer em dado sem necessidade só adiciona risco.
- **Acrescentar confirmação numérica ao seletor** (algo como "4 de 5"). Seria uma melhoria de usabilidade legítima, mas é feature, e a convenção do projeto não permite misturá-la com correção.
- Alterar o CSS do seletor. Ele implementa o padrão consagrado de rating e está correto — quem diverge dele é o script.
- Os outros defeitos do baseline. Cada um é a sua própria change.

## Capabilities

### New Capabilities

Nenhuma.

### Modified Capabilities

- `solicitacao-servico`: o requisito de avaliação pelo cliente deixa de descrever o seletor invertido e passa a exigir que a nota registrada corresponda à posição clicada, e que o preenchimento visual reflita a seleção.

## Impact

- **Código afetado:** um arquivo, `ProjetoTaskGoFinalizado-main/assets/js/painel-cliente.js`, na função que marca as estrelas.
- **Sem impacto** em API, banco de dados, migrations, build ou dependências.
- **Verificação:** não há suíte de testes no frontend, então a checagem é manual no navegador, conferindo contra o que a API registra. É o mesmo método pelo qual o defeito foi encontrado.
- **Efeito colateral desejado:** avaliações feitas a partir da correção passam a produzir `nota_media` fiel, o que muda o que aparece nos resultados de busca.
