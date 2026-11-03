# ADR-003 — Data operacional e período atual de elegibilidade

- status: `proposed`
- data: 2026-07-18
- responsáveis: `Produto`, `BE`, `SEC`, `QA`
- cards relacionados: `VR-BE-OPERATIONAL-DATE-ADOPTION-001`,
  `VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001`

## Contexto

O backend já possui `OperationalDateProvider`, configurado por timezone de
operação, mas três fluxos ainda usam o dia implícito da JVM:

- fallback de `GET /company-admins/dashboard/summary` quando `data` é omitida;
- substituição de passageiros de um serviço recorrente;
- criação, substituição e remoção de elegibilidades pelo lado do passageiro.

Essas datas não são apenas informativas. `ServicoPassageiro.ativoNaData()` é
consumido por presença, composição de paradas e provisionamento de viagens. Um
dia incorreto ou uma reativação mal definida pode autorizar uma operação no
período errado.

O modelo atual também não é histórico. A constraint
`uk_servico_passageiro` permite uma única linha por serviço e passageiro;
reativar reutiliza a linha e limpa `dataFim`. Além disso, dados migrados por
V16 podem ter `dataInicio = null`, e a implementação atual interpreta esse
valor como início ilimitado. Portanto, dizer que o modelo “preserva histórico”
é incorreto: ele preserva a identidade da linha e representa somente o estado
corrente.

Hoje existem dois algoritmos de transição duplicados. Um deles cria ou reativa
sem `dataInicio`; o outro conserva um início antigo ao reativar e, ao limpar o
fim, pode reabrir retroativamente todo o intervalo em que o vínculo esteve
inativo.

## Decisão proposta

Esta decisão ainda depende de aceite explícito de Produto. Até esse aceite, o
card permanece `analyzed` e nenhum código de produto deve ser alterado.

### 1. Significado de `dataInicio` e `dataFim`

`servico_passageiro` será tratado, neste ciclo, como projeção do período ativo
atual, não como trilha histórica.

- `dataInicio` é o primeiro dia operacional inclusivo do período ativo atual;
- `dataFim` registra a data do evento de desativação. Não significa “último dia
  historicamente elegível”; como `ativo=false` é a autoridade corrente, a
  desativação vale imediatamente inclusive naquele dia;
- `ativo` continua sendo a autoridade do estado corrente;
- nenhuma consulta histórica ou preservação de gaps é prometida por essa
  tabela.

### 2. Fonte do dia

- O dashboard sem data captura `OperationalDateProvider.today()` uma vez por
  request. Cada writer captura uma vez por transação.
- `GET /company-admins/dashboard/summary?data=YYYY-MM-DD` mantém a data
  explícita como autoridade e não consulta o provider.
- Cálculo de idade, data de nascimento, TTL, freshness, rate limit, token e
  timestamps de auditoria permanecem fora do provider operacional.
- Timezone por empresa fica fora deste card; o contrato atual continua usando
  a configuração global de operação.

### 3. Transições do período atual

A alternativa recomendada é “novo período”:

| Estado anterior | Operação | Estado posterior |
|---|---|---|
| linha nova | selecionar/atribuir | `ativo=true`, `dataInicio=dia operacional`, `dataFim=null` |
| linha inativa | reativar | mesma linha e ID, `ativo=true`, `dataInicio=dia operacional`, `dataFim=null` |
| linha já ativa com início conhecido | selecionar novamente | no-op temporal; preserva `dataInicio`, mantém `dataFim=null` |
| linha já ativa legada com início nulo | selecionar novamente | preserva `null` neste card; não fabrica um início sem preflight |
| linha ativa | remover | `ativo=false`, `dataFim=dia operacional` |
| linha já inativa e não selecionada | repetir remoção | no-op |

Reativar não preserva o início antigo. Preservá-lo e limpar `dataFim` faria o
vínculo parecer elegível durante o intervalo em que esteve inativo.

O legado ativo com início nulo não será corrigido incidentalmente por uma
seleção idempotente. Essa escolha evita inventar história ou alterar dados
existentes sem análise, mas mantém um risco conhecido e fail-open para essas
linhas. O risco deve ser fechado pelo card
`VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001`, com preflight, política de
reconciliação e constraints próprios.

### 4. Uma única fronteira de domínio

As mutações de período serão concentradas em métodos explícitos de
`ServicoPassageiro`, equivalentes a:

- iniciar ou reiniciar o período atual numa data;
- manter uma linha já ativa sem reescrever seu início;
- encerrar o período atual numa data.

Os dois services deixam de manipular diretamente combinações de `ativo`,
`dataInicio` e `dataFim`. A fronteira de gestão pelo passageiro será
transacional de forma explícita, em vez de depender silenciosamente da
transação de cada chamador. Cada mutator resolve os inputs tenant-scoped antes
de iniciar suas próprias escritas de elegibilidade. Workflows externos, como a
aprovação de onboarding, podem ter mutação anterior e devem provar em
PostgreSQL que a transação externa reverte tudo se o assignment falhar.
Coerência estrutural entre as três FKs continua no card de integridade.

### 5. Contrato HTTP e consumidores

Não haverá mudança de rota, payload, tipo, status, CORS ou papel autorizado.
`dataInicio` e `dataFim` continuam strings ISO `YYYY-MM-DD` anuláveis no wire.
A mudança é semântica e observável somente nas datas gravadas e nos
consumidores de `ativoNaData()`.

Devem permanecer invariantes:

- sem autenticação: `401`;
- papel diferente de `COMPANY_ADMIN`: `403`;
- recurso fora do tenant: `404`, sem mutação;
- IDs nulos ou duplicados: `400`, sem mutação;
- solicitação já processada: `409`;
- `servicoIds` omitido na aprovação continua no-op e lista vazia continua
  válida.

O Flutter já desserializa as duas datas como `String?` e nenhuma UI atual as
interpreta. Não há mudança de código produtivo no app. Os fixtures dos dois
consumidores administrativos serão ampliados com datas ISO não nulas em
worktree limpa, seguidos do gate oficial do app, para provar essa
compatibilidade em vez de inferi-la apenas por inspeção.

### 6. Dados legados e schema

Este card não edita V16, não cria migration e não faz backfill. Também não usa
`created_at` como suposta verdade histórica. Integridade estrutural, coerência
de tenant, concorrência, nulls legados e eventual histórico multi-intervalo
ficam no card separado `VR-BE-ELIGIBILITY-PERIOD-INTEGRITY-001`.

### 7. Prova e promoção

A implementação somente poderá ser concluída com:

- testes de domínio, services, controllers e consumidores downstream;
- gate canônico do backend no SHA final limpo;
- smoke HTTP com PostgreSQL descartável, JAR único e timezone da JVM
  deliberadamente diferente do timezone operacional;
- manifestação de SHA/tree/hash do JAR, datas observadas, migration vigente e
  cleanup;
- regressão Flutter focada em checkout limpo;
- API Matrix, backlog, QA Log e evidência sincronizados.

Gate local não autoriza deploy, piloto ou produção.

## Alternativas consideradas

### Preservar o início antigo ao reativar

Não recomendada. Como a mesma linha volta a `ativo=true` e perde `dataFim`, o
gap inativo passa a parecer elegível para presença, paradas e viagens.

### Corrigir toda linha ativa legada com início nulo quando for tocada

Mais fail-closed, porém não recomendada dentro deste card sem preflight. Uma
seleção que deveria ser idempotente passaria a inventar uma fronteira temporal
e alterar legado silenciosamente. Pode ser escolhida por Produto, desde que o
aceite registre explicitamente essa consequência e amplie testes e rollback.

### Fazer backfill global com `created_at`

Não recomendada sem dados reais e autorização. `created_at` é apenas um limite
técnico conhecido, não prova quando o direito de uso começou. Uma migration
deve abortar de forma sanitizada diante de ambiguidade e nunca escolher uma
história automaticamente.

### Criar histórico imutável de múltiplos períodos agora

Arquiteturalmente mais completo, mas amplia schema, consultas as-of, APIs,
auditoria, retenção, migração e rollback. O requisito será decidido no card de
integridade antes de qualquer implementação. Não é necessário para remover o
timezone implícito dos writers atuais.

### Usar o timezone padrão da JVM

Rejeitada. O comportamento dependeria do host/container e poderia mudar sem
alteração do produto.

## Consequências

- Novas associações e reativações passam a ter fronteira operacional
  determinística.
- Uma reativação não autoriza retroativamente o intervalo inativo.
- Os dois caminhos administrativos compartilham a mesma invariável de domínio.
- A identidade da linha é preservada, mas intervalos anteriores continuam não
  auditáveis pela tabela atual.
- Linhas ativas legadas com início nulo continuam sendo dívida explícita, não
  um problema ocultado como resolvido.
- Reverter somente o código não desfaz datas já gravadas por reativações. Antes
  de promoção futura, backup ou forward-fix precisa fazer parte do plano de
  rollout.

## Validade e revisão

Após aceite, revisar quando houver timezone por empresa, requisito de consulta
histórica, mudança de semântica de `ativoNaData()`, migration de integridade ou
nova finalidade/auditoria para os períodos de elegibilidade.

Se Produto escolher alternativa diferente, esta ADR deve ser atualizada antes
de mudar o card para `ready`; a decisão substituta precisa congelar criação,
reativação, repetição ativa, legado nulo, remoção, rollback e prova.
