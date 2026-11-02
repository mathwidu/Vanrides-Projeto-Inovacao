# Goal Backlog Triage - 2026-06-18

## Objetivo
Zerar os backlogs planejados ativos do VanRides sem trabalhar em duplicidade.
Esta triagem separa cards implementaveis de cards que precisam ser fechados por
auditoria, reclassificados, bloqueados por dependencia externa ou considerados
substituidos pelo portal atual.

## Fontes lidas
- `vanrides-app/backlog/Flutter-Cards.md`
- `vanrides-app/backlog/Portal-Admin-S1-Plan.md`
- `vanrides-app/backlog/admin-portal-ux-audit-2026-06-18.md`
- `vanrides-app/backlog/Technical-Debt-2026-04-22.md`
- `vanrides-backend/backlog/SpringBoot-Cards.md`
- `vanrides-backend/backlog/Strategic-Delivery-Plan.md`
- `vanrides-backend/backlog/Panel-Cards.md`

## Impact Analysis
- Contexto: existem 45 cards com status exatamente `PLANNED`, mas nem todos
  representam trabalho novo valido. Parte do `Panel-Cards` descreve um painel
  futuro que foi absorvido pelo portal admin Flutter.
- Mudancas tecnicas desta etapa: nenhuma alteracao de runtime; apenas
  classificacao e ordem de execucao.
- Contratos afetados: nenhum agora. Cards futuros podem afetar tickets,
  notificacoes, chat, perfil, onboarding e hardening.
- Riscos: marcar card vivo como obsoleto, manter card duplicado como trabalho
  ativo, ou implementar feature que depende de backend/provider externo.
- Mitigacao: primeiro criar esta matriz; depois, ao tocar em cada card, fazer
  Impact Analysis proprio e validar no repo afetado.
- Validacao minima desta etapa: `git diff --check` e commit documental. Gates de
  app/backend entram quando houver mudanca de codigo.
- Rollback simples: reverter o commit documental desta triagem.

## Resumo numerico
- App Flutter: 20 cards `PLANNED` antes da normalizacao.
- Backend Spring: 11 cards `PLANNED` antes da normalizacao.
- Panel-Cards: 14 cards `PLANNED` antes da normalizacao.
- Total bruto inicial: 45.
- Total recomendado como backlog ativo direto inicial: 28.
- Total recomendado para reclassificacao antes de qualquer implementacao: 17.

### Normalizacoes aplicadas
- 2026-06-18: `Panel-Cards.md` foi reclassificado como historico/futuro; zero
  cards exatos `PLANNED` permanecem naquele arquivo.
- 2026-06-18: app `0124`, `0125`, `0984` e `0985` foram reclassificados como
  `BLOCKED` por provider/dispositivo/evidencia externa.
- 2026-06-18: app `0980` foi reclassificado como `FUTURE` living parking lot.
- 2026-06-18: app `1019` foi concluido com relatorio visual deterministico
  contra backend DEV (`6ebfaf7`).
- 2026-06-18: app `1018` foi concluido com dia operacional menos denso,
  gate app e smoke visual DEV (`e932f72`).
- 2026-06-18: app `0988` foi concluido com ficha operacional completa de
  passageiro, presença do dia, gate app e smoke visual DEV (`53b211d`).
- 2026-06-18: app `0983` foi concluido com primeira dobra de conta por papel,
  smoke visual completo/incompleto e gate app (`60fdb54`).
- 2026-06-18: backend `0133` foi concluido com testes de convite
  invalido/expirado, smoke HTTP DEV de onboarding hibrido e gate backend
  (`e5830ab`, `a65685b`).
- 2026-06-18: app `0144` foi concluido com testes focados, smoke visual
  deterministico dos estados de onboarding/portal e gate app (`d8c27b1`).
- 2026-06-18: backend `0124`/`0125` foram implementados e testados local/CI
  (`d4bd10f`), mas reclassificados como `BLOCKED` ate o deploy DEV aplicar a
  imagem nova e o smoke funcional de tickets passar.
- 2026-06-18: backend `0122`/`0123` de Chat foram reclassificados como
  `FUTURE`; app `0123` de QA Suporte foi reclassificado como `BLOCKED` pelo
  mesmo bloqueio de deploy DEV dos tickets.
- 2026-06-18: app `0235`/`0236` foram concluidos com gateways injetaveis de
  cadastro, widget tests, gate app e smokes reais DEV de motorista/passageiro
  (`286f8fc`, `470762c`).
- 2026-06-18: app `0138`/`0239` foram concluidos com ADR de densidade do mapa,
  regra `services` vs `features/data`, auditoria de `lib/services`, `flutter
  analyze` e `flutter test` (`f1b1594`).
- 2026-06-18: backend `0138`/`0139` foram concluidos com ADR de metadata de mapa
  e decisao de manter turn-by-turn nativo fora desta fase (`4b0753c`).
- 2026-06-18: app `0972` foi auditado e fechado com testes focados de deep
  link/onboarding, smoke DEV real de convite/solicitacao de motorista,
  referencias visuais do `0974` e gate app oficial.
- 2026-06-18: backend `0136`/`0137` foram concluidos com evento realtime
  `avisosOperacionais`, testes de limiar/progresso, gate backend e smoke HTTP
  basico DEV (`4189ff2`).
- 2026-06-18: app `0137` foi concluido com parser/UX in-app de avisos
  operacionais, teste de contrato, teste de tela do passageiro, gate app e
  ratchet de tamanho preservado (`535691c`).
- 2026-06-18: backend `0128`/`0129` foram concluidos com `X-Request-Id`,
  rate limiting configuravel, OpenAPI protegido, testes focados, suite
  completa, gate backend e smoke HTTP DEV basico (`6e7a439`, `9a94cc2`).
- 2026-06-18: app `0238` foi concluido com migracao de todas as 11 telas
  legadas do root `lib/` para `features/<area>/presentation/`, imports
  atualizados, `root_screen_count = 0`, `flutter analyze` e `flutter test`
  (`ae9392e`, `e273d24`).
- 2026-06-18: app `0240` foi concluido com analyzer endurecido, limpeza dos
  novos avisos, regras documentadas, baseline do ratchet sincronizado apos a
  migracao estrutural e gate app oficial (`3b59571`).

### Estado atual apos normalizacao, fechamento funcional e hardening final
- App Flutter: 0 cards exatos `PLANNED`.
- Backend Spring: 0 cards exatos `PLANNED`.
- Panel-Cards: 0 cards exatos `PLANNED`.

## Classes usadas
- `ATIVO`: trabalho valido para implementar ou validar.
- `AUDITAR_PARA_FECHAR`: provavelmente ja foi absorvido por trabalho recente,
  mas precisa evidencia atual antes de `DONE`.
- `BLOQUEADO_EXTERNO`: depende de provider, dispositivo fisico, conta externa
  ou configuracao fora do codigo.
- `FUTURO`: importante, mas fora da ordem de zeragem imediata.
- `SUPERSEDED`: substituido por outra frente; deve sair de `PLANNED` com nota.
- `LIVING`: card guarda ideias/parking lot; nao deve contar como backlog
  implementavel.

## App Flutter

| Card | Classificacao | O que fazer |
| --- | --- | --- |
| 0122 - Reclamacoes/Suporte | ATIVO_DEPENDE_BACKEND | Implementar UI de tickets depois do backend 0124. |
| 0123 - QA Reclamacoes/Suporte | BLOCKED_DEPLOY_DEV | Fechar depois do deploy DEV/smoke funcional dos tickets. |
| 0124 - Push notifications | BLOQUEADO_EXTERNO | Reclassificar para `BLOCKED` ate Firebase/APNs/provider estarem definidos. |
| 0125 - QA Push | BLOQUEADO_EXTERNO | Reclassificar junto do 0124. |
| 0137 - Notificacoes operacionais da corrida | DONE | Concluido com parser de `avisosOperacionais`, alerta compacto no sheet do passageiro e gate app. |
| 0138 - Spike UX labels/pins/fotos | DONE | Concluido com ADR de labels, markers, foto futura e densidade de mapa. |
| 0144 - QA onboarding hibrido passageiro | DONE | Concluido com testes focados, smoke visual deterministico e gate app. |
| 0980 - Parking lot | LIVING | Reclassificar para `FUTURE` ou manter fora da contagem de backlog implementavel. |
| 0235 - MotoristaCadastroGateway | DONE | Concluido com gateway injetavel, testes e smoke DEV. |
| 0236 - PassageiroCadastroGateway | DONE | Concluido com gateway injetavel, Places fake/testavel e smoke DEV. |
| 0238 - Migrar telas legadas para features | DONE | Concluido com 0 telas `*_screen.dart` no root de `lib/`, imports migrados e gate app. |
| 0239 - Regra services vs features/data | DONE | Concluido com `lib/README.md`, ADR e auditoria de `lib/services`. |
| 0240 - Endurecer analyzer | DONE | Concluido com regras customizadas, limpeza de avisos, docs e baseline do ratchet atualizado. |
| 0972 - Driver invite mobile deep link | DONE | Concluido com testes focados, smoke DEV real, evidencias visuais do 0974 e gate app. |
| 0983 - Perfil por papel e primeira dobra | DONE | Concluido com screenshots completo/incompleto por papel, upload/foto coberto e gate app. |
| 0984 - Smoke real de e-mail e convites | BLOQUEADO_EXTERNO_PARCIAL | Precisa provider/mailbox DEV observavel; pode virar roteiro + smoke quando credenciais existirem. |
| 0985 - Roteiro iPhone fisico para deep links | BLOQUEADO_EXTERNO | Depende de iPhone fisico/TestFlight ou execucao assistida pelo usuario. |
| 0988 - Passageiro como ficha operacional completa | DONE | Concluido com presenca do dia, gate app e smoke visual DEV. |
| 1018 - Dia operacional menos denso | DONE | Concluido com `Estado do dia`, manutencao enxuta, gate app e smoke DEV. |
| 1019 - E2E visual deterministico | DONE | Concluido com manifesto visual deterministico e smoke DEV. |

## Backend Spring

| Card | Classificacao | O que fazer |
| --- | --- | --- |
| 0136 - Notificacoes operacionais por limiar | DONE | Concluido com evento realtime `avisosOperacionais` sem recalculo agressivo de rota. |
| 0137 - QA notificacoes operacionais | DONE | Concluido com testes de limiar/progresso, gate backend e smoke HTTP basico DEV. |
| 0138 - ADR labels/foto/densidade | DONE | Concluido com ADR de metadata segura para mapa. |
| 0139 - ADR turn-by-turn nativo | DONE | Concluido com decisao de handoff externo nesta fase. |
| 0122 - Chat persistente | FUTURE | Reclassificado; baixa prioridade frente a operacao admin/tickets/notificacoes. |
| 0123 - QA Chat | FUTURE_DEPENDE_0122 | Reclassificado; aguardar decisao futura sobre chat. |
| 0124 - Tickets + inbox empresa | BLOCKED_DEPLOY_DEV | Implementado em `d4bd10f`; aguardando deploy DEV/smoke funcional. |
| 0125 - QA Tickets | BLOCKED_DEPLOY_DEV | Testes e smoke script prontos; smoke DEV depende da imagem nova no service. |
| 0128 - Hardening observabilidade/rate/OpenAPI | DONE | Concluido com request-id, rate limit, OpenAPI protegido, gate backend e smoke HTTP DEV. |
| 0129 - QA Hardening | DONE | Concluido com testes focados, suite completa, quality ratchet e gate backend. |
| 0133 - QA onboarding hibrido passageiro | DONE | Concluido com testes de convite invalido/expirado, smoke HTTP DEV e gate backend. |

## Panel-Cards

Decisao recomendada: todos os 14 cards `PLANNED` de `Panel-Cards.md` devem sair
da fila de implementacao direta antes de qualquer codigo. O proprio arquivo diz
que ainda nao existe repo dedicado do painel. Hoje, o portal empresa vive no
Flutter web e ja cobre login, dashboard, motoristas, passageiros, responsaveis,
servicos, viagens, conta, convites e smokes visuais.

Classificacao recomendada:
- `0001` a `0013`: `SUPERSEDED` pelo portal admin Flutter atual, com nota
  apontando para `Flutter-Cards.md` e para os cards admin 0987-1019.
- `0980`: `LIVING`/`FUTURE`, nao implementavel como card normal.

Acao segura:
1. Criar nota no topo de `Panel-Cards.md` dizendo que o arquivo e historico.
2. Mudar status dos cards para `FUTURE` ou `BLOCKED` apenas se quisermos
   preservar a ideia de um repo dedicado futuro.
3. Nao implementar esses cards agora.

## Ondas de execucao recomendadas

### Onda 0 - Normalizacao do backlog
Objetivo: reduzir a contagem falsa de pendencias.
- Reclassificar `Panel-Cards` como historico/superseded/futuro.
- Reclassificar `0980` como living parking lot.
- Auditar backend `0133` e app `0144` para fechar ou listar gap real do
  onboarding hibrido. **Concluido em 2026-06-18**.
- Registrar a decisao em QA/triage.

### Onda 1 - Portal admin realmente final
Objetivo: fechar a principal dor atual de uso operacional.
- `1019`: E2E visual deterministico com manifesto de screenshots.
- `1018`: dia operacional menos denso.
- `0988`: ficha operacional completa de passageiro com presença do dia.

### Onda 2 - Onboarding, e-mail e deep links
Objetivo: garantir que convite e criacao de usuarios funcionam fora do caminho
feliz local.
- `0972`: convite de motorista no app mobile. **Concluido em 2026-06-18**.
- `0984`: smoke real de e-mail/convites, se provider observavel estiver
  disponivel.
- `0985`: roteiro de iPhone fisico/TestFlight.
- `0144` + backend `0133`: consolidar QA do onboarding hibrido. **Concluido
  em 2026-06-18**.

### Onda 3 - Suporte/tickets
Objetivo: abrir e responder suporte de forma real.
- Backend `0124` + `0125`.
- App `0122` + `0123`.
- Smoke portal + usuario final.

### Onda 4 - Notificacoes e mapa
Objetivo: avisos operacionais confiaveis sem custo alto.
- Backend `0138` e app `0138`: decisao de labels/pins/fotos.
- Backend `0139`: decisao turn-by-turn.
- Backend `0136` + `0137` e app `0137`: eventos e UX de notificacoes
  operacionais.
- App `0124`/`0125` ficam bloqueados ate provider push estar pronto.

### Onda 5 - Divida tecnica e arquitetura
Objetivo: reduzir custo de manutencao antes de novas features grandes.
- App `0239`: regra services vs feature/data.
- App `0235` e `0236`: gateways de cadastro.
- App `0238`: migracao de telas legadas em ondas.
- App `0240`: analyzer mais forte.
- Backend `0128` + `0129`: hardening. **Concluido em 2026-06-18**.

### Onda 6 - Chat
Objetivo: comunicacao rica depois que suporte/tickets e notificacoes estiverem
menos nebulosos.
- Backend `0122` + `0123`.
- App de chat deve ser reaberto apenas depois da decisao de UX do produto.

## Proximo card recomendado
Nenhum card exato `PLANNED` permanece nos backlogs ativos lidos nesta triagem.

Motivo:
- backend, app e Panel-Cards chegaram a 0 cards exatos `PLANNED`;
- cards restantes foram concluidos, bloqueados por dependencia externa,
  reclassificados como futuro/living ou marcados como historico/superseded;
- `0238` e `0240` fecharam a ultima onda estrutural do app.

## Criterio de fechamento desta triagem
- Esta matriz foi criada e commitada.
- Backlogs ativos lidos chegaram a 0 cards exatos `PLANNED`.
- Gates locais finais foram executados nos repos afetados e registrados no
  `QA-Execution-Log.md`.
- Proximas iniciativas devem nascer como novos cards, nao como pendencias
  herdadas desta zeragem.
