O único arquivo de aplicação alterado é `ProjetoTaskGoFinalizado-main/assets/js/painel-cliente.js`. Nenhuma mudança de backend, banco, migration ou CSS — se alguma parecer necessária, a premissa da change caiu e a proposta deve ser revista.

## 1. Preparação

- [x] 1.1 Confirmar que o renome editorial do cenário já está na spec principal, feito durante o planejamento para que o delta pudesse validar; verificação: `grep -c "envia nota invertida" openspec/specs/solicitacao-servico/spec.md` retorna 0 e o cenário aparece como "Nota registrada ao clicar numa estrela"
- [x] 1.2 Subir backend e frontend e reproduzir o defeito antes de corrigir, para ter a evidência de partida; verificação: clicar na estrela mais à direita e confirmar pela API que a nota gravada é 1, e que as cinco estrelas acenderam

## 2. Correção

- [x] 2.1 Em `ativarEstrelas`, trocar a travessia de `previousElementSibling` para `nextElementSibling`; verificação: a função passa a marcar da estrela clicada até o fim da lista, e `grep -c "previousElementSibling" assets/js/painel-cliente.js` retorna 0
- [x] 2.2 Confirmar que nenhuma outra parte do arquivo foi afetada; verificação: `git diff --stat` mostra um arquivo e uma linha alterada
- [x] 2.3 Confirmar que o CSS não foi tocado; verificação: `git diff --name-only` não inclui `pages/painel-cliente.html`

## 3. Verificação do comportamento

Exige backend no ar e o frontend servido por HTTP. Cada item confere a nota **gravada pela API**, não a aparência.

- [x] 3.1 Avaliar clicando na estrela mais à direita; verificação: a solicitação fica com `avaliacao` igual a 5
- [x] 3.2 Avaliar clicando na estrela mais à esquerda; verificação: a solicitação fica com `avaliacao` igual a 1
- [x] 3.3 Avaliar clicando numa estrela intermediária, a terceira da esquerda para a direita; verificação: a solicitação fica com `avaliacao` igual a 3
- [x] 3.4 Conferir o preenchimento visual em cada um dos casos acima; verificação: acendem apenas as estrelas da primeira até a clicada, e nunca as cinco quando a nota é menor que 5
- [x] 3.5 Confirmar que a nota média do prestador reflete as avaliações enviadas; verificação: `notaMedia` devolvida por `GET /prestadores/{id}` é a média aritmética das notas gravadas
- [x] 3.6 Confirmar que a validação de faixa segue intacta; verificação: enviar avaliação sem selecionar estrela alguma continua sendo barrado pela interface, sem chamada à API

## 4. Fechamento

- [x] 4.1 Confirmar que nenhum dado existente foi alterado, conforme a decisão de escopo; verificação: as três avaliações de teste no banco seguem com os mesmos valores de `avaliacao` e `comentario_avaliacao`
- [x] 4.2 Registrar um commit `fix` no imperativo, sem misturar com outro escopo; verificação: `git log --oneline` mostra um commit contendo apenas `painel-cliente.js`

Observação de convenção: esta change é `fix`. Não há tarefa de `CHANGELOG.md` — a regra do projeto pede entrada de changelog para `feat`, e estender isso a `fix` seria inventar convenção. Também não há migration nem alteração de backend.
