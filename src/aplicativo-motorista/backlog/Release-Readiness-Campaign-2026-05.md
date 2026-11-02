# Release Readiness Campaign — 2026-05

Objetivo: fechar o trabalho acumulado do VanRides com o menor número possível de ações manuais, validando backend, app, portal, guardian, corrida `map-first`, documentação e governança de issues.

## Escopo congelado

Dentro da campanha:
- Portal Admin S1
- onboarding híbrido do passageiro
- verificação de e-mail e sessão
- corrida `map-first`
- guardian mobile
- lifecycle da corrida
- QA logs, backlogs, roteiros manuais e issues GitHub

Fora da campanha:
- billing
- chat
- push real com Firebase/APNs
- turn-by-turn nativo
- redesign grande sem relação direta com fechamento

## Automação principal

Rodar a partir do app:

```bash
cd ~/projetos/pessoal/vanrides/vanrides-app
./scripts/qa_release_campaign.sh
```

Com E2E mobile completo, quando houver device/simulator estável:

```bash
./scripts/qa_release_campaign.sh --mobile-e2e --device "<device-id>"
```

O script gera evidências em:

```text
output/release-readiness/<timestamp>/
```

Pre-requisito do ambiente DEV: o backend publicado precisa estar com `VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev`. Sem isso, o seed cria contas da fixture com e-mail pendente, o bootstrap operacional para antes dos logins seguintes e a campanha retorna `BLOCKED_VALIDATION_EMAIL_VERIFICATION` com exit code `86`.

## O que a campanha roda

- backend `./scripts/qa_gate_card.sh` com smoke profundo de auth, admin e guardian
- backend `./scripts/qa_guardian_suite.sh`
- backend `./scripts/qa_rbac_suite.sh`
- fixture `seed_operational_service_day_fixture.sh`
- app `./scripts/qa_gate_card.sh --no-e2e`
- portal web `./scripts/qa_admin_portal_web_e2e.sh`
- guardian mobile suite com as credenciais da fixture
- E2E mobile completo opcional

## Dados da fixture

A fixture deve emitir:
- admin
- motorista M1 e M2
- passageiro principal vinculado a serviços manhã/tarde/noite
- passageiro de apoio com presença parcial
- responsável vinculado
- serviços recorrentes manhã/tarde/noite
- viagens operacionais do dia, com motorista por viagem e snapshot operacional da rota
- `fixture_date`, para que o app use a mesma data operacional UTC do backend DEV durante E2E

Esses dados alimentam automaticamente a campanha e reduzem o QA manual do guardian.

## Classificação de resultado

- `PASS`: evidência automatizada ou manual executada com sucesso
- `FAIL_BLOCKER`: quebra fluxo principal; corrigir antes de fechar
- `FAIL_NON_BLOCKER`: registrar bug/débito e seguir se não bloquear release
- `BLOCKED_BY_DEVICE`: depende de device físico/simulator
- `BLOCKED_VALIDATION_EMAIL_VERIFICATION`: ambiente publicado não está auto-verificando contas QA `@vanrides.dev`
- `BLOCKED_BY_DEPLOY`: ambiente publicado não contém a versão esperada em outro ponto da campanha

## Manual mínimo restante

- validar permissões reais de localização/background em iPhone físico
- revisar visualmente a cena de corrida no mapa quando o E2E rodar fora de device real
- fazer push/deploy manualmente, conforme regra operacional do projeto

## Fechamento de governança

Antes de marcar cards como `DONE`:
- registrar evidência em `backlog/QA-Execution-Log.md`
- atualizar `backlog/Flutter-Cards.md`
- atualizar `qa-manual/resultados/SUMARIO.md`
- atualizar resultados dos roteiros `01` a `05`
- sincronizar issues GitHub com `scripts/build_backlog_issue_drafts.py` e `scripts/publish_pending_backlog_issues.py`
- comentar/fechar issues apenas quando DoD e evidência estiverem alinhados
