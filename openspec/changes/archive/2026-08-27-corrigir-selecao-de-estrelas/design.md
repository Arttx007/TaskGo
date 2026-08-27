## Context

Ver `proposal.md` (seção Why) para a motivação. O que este documento registra é o mecanismo exato do defeito, porque ele é contraintuitivo o bastante para que a correção óbvia seja a errada.

Três peças interagem:

- `.eval-stars { flex-direction: row-reverse; }` — a primeira estrela do DOM é renderizada à **direita**. Confirmado medindo as posições: o índice 0 do DOM fica em x=761 e o índice 4 em x=569.
- `.eval-stars i.active, .eval-stars i.active ~ i { color: amarelo }` — acende a estrela marcada e todas as **seguintes no DOM**, que sob `row-reverse` estão à esquerda dela.
- `ativarEstrelas` marca a estrela clicada e caminha por `previousElementSibling`, ou seja, para a **direita**.

O script marca para um lado e o CSS ilumina para o outro. Duas consequências: a nota, que é a contagem de `.active`, sai invertida; e como o conjunto marcado e o conjunto iluminado se somam, **qualquer clique acende as cinco estrelas**, eliminando o único sinal que o usuário teria.

## Goals / Non-Goals

**Goals:**

- Alinhar a contagem à ordem visual, corrigindo nota e preenchimento na mesma mudança.
- Deixar registrado por que a correção não é no CSS, para que ninguém a "conserte" de novo pelo lado errado.

**Non-Goals:**

- Tocar em backend ou em dados. A API já valida e persiste corretamente a nota que recebe.
- Redesenhar o seletor ou acrescentar confirmação numérica.

## Decisions

### Corrigir o script, não o CSS

A correção é trocar `previousElementSibling` por `nextElementSibling` em `ativarEstrelas`. Com isso, clicar no índice *k* do DOM marca de *k* até o fim — que sob `row-reverse` é da estrela clicada para a esquerda, exatamente o conjunto que o seletor `~` já ilumina. Contagem e iluminação passam a coincidir: clicar na estrela mais à direita marca as cinco e registra 5.

*Alternativa considerada e rejeitada:* remover `flex-direction: row-reverse`. Parece a correção natural, e é a armadilha. O par `row-reverse` mais combinador `~` é o padrão consagrado de rating em CSS justamente porque o `~` só alcança irmãos posteriores; sem a inversão, o preenchimento passaria a acender da estrela clicada para a **direita**, quebrando o efeito visual que hoje funciona. Trocaria um defeito por outro.

*Alternativa considerada:* reescrever `ativarEstrelas` para calcular o índice e marcar por posição, em vez de caminhar por irmãos. Rejeitada por ser reescrita onde uma palavra resolve — e a change é de correção, não de refactor.

### O preenchimento visual entra como requisito

O baseline não exigia nada do preenchimento, e por isso o defeito de acender tudo não tinha onde ser detectado. A spec passa a exigir que o preenchimento reflita a seleção, transformando o único sinal visível do usuário em algo verificável.

## Risks / Trade-offs

- **Não há teste automatizado no frontend.** A correção depende de verificação manual. → Mitigação: o roteiro de verificação confere a nota gravada pela API, não a aparência — é o mesmo método que expôs o defeito, e é falsificável.
- **A mudança é de uma palavra e parece inofensiva.** Um `find and replace` distraído em `previousElementSibling` poderia atingir outros pontos do arquivo. → Mitigação: a verificação confirma que só `ativarEstrelas` mudou.
- **Notas antigas seguem invertidas.** Fora de escopo por decisão registrada: as três avaliações do banco são de teste. → Se um dia houver dado real anterior à correção, ele será indistinguível de dado legítimo e deve ser tratado como perdido, não convertido.
