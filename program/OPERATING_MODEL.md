# Modelo Operacional

## Princípios

1. Uma única fonte de verdade para estado e prioridade.
2. Uma entrega vertical ativa por vez em todo o produto.
3. Mudanças pequenas, reversíveis e ligadas a um risco ou resultado.
4. Testes e evidências fazem parte da implementação.
5. Segurança, privacidade e acessibilidade são requisitos de produto.
6. Um gate local verde não autoriza piloto ou produção.
7. Toda exceção é explícita, temporária, aprovada e rastreável.

## Estados de trabalho

| Estado | Significado |
|---|---|
| `proposed` | Ideia registrada, ainda sem análise suficiente. |
| `analyzed` | Impacto e dependências conhecidos, mas ainda não executável. |
| `ready` | Contrato e Definition of Ready completos, sem blocker; pode ser o próximo card. |
| `in_progress` | Único card vertical atualmente em implementação. |
| `validating` | Código concluído; gates e evidências em execução. |
| `waiting_human` | Depende de confirmação humana explícita. |
| `waiting_external` | Depende de provedor, ambiente ou terceiro. |
| `blocked` | Há impedimento conhecido que impede progresso seguro. |
| `done` | Escopo implementado, validado e rastreado. |
| `cancelled` | Não será executado; motivo obrigatório. |
| `superseded` | Substituído por outro item identificado. |

`GO`, `HOLD` e `NO_GO` são decisões de promoção de release, não estados de card.

## Limites de WIP

- no máximo 1 card `in_progress` ou `validating` em todo o programa;
- no máximo 1 candidato de integração aberto;
- no máximo 1 release candidate aberto;
- durante um release candidate, contratos compartilhados ficam congelados;
- pesquisa e descoberta podem ocorrer em paralelo, mas não podem alterar código
  nem assumir o estado `in_progress`.

Exceções exigem uma decisão registrada em `program/decisions/`, com motivo,
prazo, riscos e responsável.

### Estado ocioso legítimo

Quando não existe card `in_progress`/`validating` nem qualquer card `ready`, o
estado correto é:

- `activeCardId = null`;
- `nextCardId = null`;
- cards aguardando pessoa, ambiente ou terceiro permanecem
  `waiting_human`/`waiting_external`, com blocker explícito;
- cards ainda sem DoR permanecem `analyzed` ou `proposed`;
- WIP global fica `0/1`.

Um card bloqueado não pode permanecer artificialmente ativo apenas para
preencher `nextCardId`. `program_control.py next` retorna sucesso informativo
nesse estado, e o dashboard mostra que não há card executável. Se qualquer card
ficar `ready`, `nextCardId` volta a ser obrigatório; ativá-lo continua sendo
uma transição explícita e separada.

Antes de anunciar trabalho, `program_control.py next` executa a validação
estática completa. Estados `ready`, `in_progress` e `validating` não aceitam
`blockers`; os estados de espera exigem ao menos um blocker textual e acionável.

## Definition of Ready

Um card só pode ficar `ready` quando tiver:

- resultado esperado e critérios de aceite observáveis;
- escopo e não-escopo;
- Impact Analysis;
- contratos e repositórios afetados;
- riscos, mitigação e rollback;
- rollout progressivo, observabilidade e critério de abortar;
- dependências satisfeitas;
- plano de testes, smoke e evidências;
- confirmação de que não conflita com trabalho sujo preexistente.

O arquivo Markdown do card deve conter seções de resultado, Impact Analysis,
contratos, riscos, validação, dependências, escopo, fora de escopo, critérios,
rollout, rollback e Definition of Ready.
A Definition of Ready precisa ser um checklist e não pode conter item pendente.
O ratchet continua valendo em `in_progress` e `validating`, impedindo que uma
edição manual contorne a transição por `ready`.

## Definition of Done

Um card só pode ficar `done` quando tiver:

- critérios de aceite cumpridos;
- testes adicionados ou justificativa registrada;
- gate oficial de cada módulo afetado aprovado, executado a partir de seu `moduleRoot`;
- smoke executado na mesma versão que está sendo avaliada;
- evidência canônica `program/evidence/<CARD_ID>.md` existente, contendo o ID do
  card, comandos, resultado, SHA e ambiente; baseline ou evidência de outro card
  pode ser suplementar, nunca substituta;
- documentação, contrato, QA Log e backlog local atualizados quando aplicável;
- commits explícitos no Git compartilhado, com escopo por módulo e sem segredos;
- rollback ainda válido;
- nenhum bloqueio ocultado como sucesso.

O ratchet estrutural v1.1 rejeita `done` quando o card mantém qualquer checkbox
pendente, quando `Critérios de aceite` não contém um checklist concluído, quando
um commit não termina em SHA Git completo de 40 caracteres ou quando a
evidência canônica tem menos de 500 caracteres, menos de três headings, não
registra data, `PASS`, comando reconhecível, um commit declarado e limite de
escopo/rollback. Esse piso não substitui o schema semântico v2 de
`VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001`; ele impede falso verde trivial até
o sidecar, checksums, retenção e sanitização estruturados entrarem em vigor.

## Cards entre módulos e repositórios

`card.epic` é classificação de roadmap, não herança. `epic.dependsOn`, status,
aceite e evidência não são herdados por cards. A executabilidade depende
exclusivamente de `card.dependsOn`, DoR, blockers e estado próprios.

Uma mudança vertical pode ter um card pai único. No monorepo acadêmico,
`repositoryRoot` é a raiz Git compartilhada e `moduleRoot` identifica o diretório
de cada módulo em `program/repositories.json`. Cada módulo afetado mantém:

- identificação explícita do commit compartilhado e dos caminhos avaliados;
- gate próprio;
- evidência própria;
- rollback próprio.

A integração só é considerada validada quando o manifest registra exatamente o
SHA, os módulos, artefatos e schema executados juntos. Repositórios independentes
legados continuam exigindo identificação de versão própria. Baselines e recibos
históricos não comprovam o conteúdo migrado.

### Alocação de migrations

Card `analyzed` não reserva número Flyway futuro nem cria dependência apenas
para ordenar nomes de migration. No freeze de `ready`, cada card que altera
schema revalida a maior versão presente/aplicada, aloca o próximo número
contínuo, atualiza a allowlist para o path exato e prova ausência de collision e
out-of-order. Uma aresta `dependsOn` exige precondição real de domínio/schema.

### Invalidação de revisão humana congelada

Se uma revisão em `waiting_human` ficar tecnicamente obsoleta antes do aceite,
ela não pode ser corrigida no mesmo digest nem permanecer respondível. O
registro vira tombstone `SUPERSEDED / DO_NOT_ACCEPT`, todos os campos humanos
ficam `INVALIDATED`, o card retorna a `analyzed / REFREEZE_REQUIRED` e o
controlador rejeita o digest histórico. Uma nova resposta humana só pode ser
solicitada depois de novo ADR/DOR, commit, digest e revisão independente
imutáveis.

## Decisões humanas obrigatórias

Exigem aprovação humana:

- aceite de risco ou waiver;
- promoção para piloto ou produção;
- confirmação sensorial que uma automação não consegue provar;
- mudança destrutiva, migração irreversível ou rotação de credenciais;
- mudança de finalidade de dados pessoais;
- cutover, rollback de produção e comunicação externa.

## Gestão de exceções

Um waiver deve registrar:

- gate ou risco dispensado;
- justificativa;
- impacto;
- compensação temporária;
- aprovador;
- validade;
- card que removerá a exceção.

Waiver expirado transforma a promoção em `HOLD`.
