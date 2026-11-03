# Padrão de Qualidade por Card — App Flutter

Objetivo: entregar cada card com confiança, sem quebrar fluxos já existentes.

## 1) Conceito (por que isso existe)
- Toda mudança de comportamento deve gerar **teste automatizado correspondente**.
- O teste deve ficar perto do risco da mudança (unit/widget/integration).
- Smoke manual no app é complementar, não substitui automação.

## 2) Regra obrigatória por card
Antes de marcar `DONE`, o card precisa ter:
1. Harness preflight executado (`status` + `check`) no workspace VanRides.
2. Impact Analysis e plano definidos.
3. Teste novo/ajustado para a alteração.
4. Quality ratchet sem regressão nova (`scripts/quality_gate.sh`) ou exceção documentada.
5. Execução do gate do app (`scripts/qa_gate_card.sh --no-e2e` para card local; `scripts/ci_quality_gate.sh` para perfil CI).
6. Smoke manual do fluxo alterado (quando aplicável).
7. Evidência no `backlog/QA-Execution-Log.md`.
8. Backlog atualizado com `DONE (commit: <hash>)`.
9. Harness `done-check` e `refresh-dashboard` executados antes de encerrar o bloco.

## 3) Como decidir o tipo de teste
- Mudou regra de serviço/parse/headers:
  - unit test.
- Mudou UI, validação de formulário ou navegação local:
  - widget test.
- Mudou fluxo ponta a ponta (cadastro/login/presença/localização):
  - integration test (preferencialmente no iOS Simulator).

## 4) Template rápido para usar no card
Copiar e preencher no card em andamento:

```md
### Test Plan do Card (obrigatório)
- Risco principal:
- Testes novos/ajustados:
  - [ ] Tipo:
  - [ ] Arquivo:
  - [ ] Cenário feliz:
  - [ ] Cenário de erro/borda:
- Regressão mínima executada:
  - [ ] flutter analyze
  - [ ] flutter test
  - [ ] python3 scripts/quality_metrics_test.py
  - [ ] ./scripts/quality_gate.sh
  - [ ] integration_test relevante
- Evidência registrada em QA Log:
  - [ ] Sim
```

## 5) Comando oficial de gate por card
```bash
./scripts/qa_gate_card.sh --no-e2e
```

Nota: no estado atual do projeto, o gate do app trata infos/warnings de lint como dívida técnica existente e bloqueia principalmente erros reais + falhas de teste.
O script tambem executa os testes do comparador (`scripts/quality_metrics_test.py`), o quality ratchet (`scripts/quality_gate.sh`), le `scripts/dev.env.local` e tenta resolver automaticamente o melhor device para E2E, priorizando iOS Simulator e depois `macOS`.

O workflow `.github/workflows/flutter-quality.yml` usa o perfil de CI:
```bash
./scripts/ci_quality_gate.sh
```

Esse entrypoint valida o wiring de CI e delega para `qa_gate_card.sh --no-e2e`,
mantendo o CI sem E2E real por padrão.

Exemplo com device explícito:
```bash
./scripts/qa_gate_card.sh "<DEVICE_ID>"
```
