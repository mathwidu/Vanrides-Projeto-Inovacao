# QA Phase 0470 — Matriz final de fluxos

Data: 2026-06-17
Ambiente: local Mac, iPhone 16e Simulator iOS 26.2, Flutter debug, DEV API
Branch: `quality/app-quality-stack-2026-06-13`
API: `http://127.0.0.1:8080`

## Objetivo

Validar a fase de motorista e o app funcional por papel antes de considerar a
base pronta para uma rodada de uso real. Esta matriz separa o que passou, o que
e limite conhecido e o que precisa virar proximo card.

## Evidencias executadas

- `flutter analyze --no-fatal-infos --no-fatal-warnings`: PASS, sem issues.
- `RUN_E2E=0 ./scripts/qa_gate_card.sh`: PASS.
  - quality ratchet tests: PASS, 5 testes.
  - E2E preflight tests: PASS, 7 testes.
  - `flutter analyze`: PASS, sem issues.
  - `flutter test`: PASS, 483 testes.
  - quality ratchet: PASS.
- `./scripts/qa_driver_link_dev_smoke.sh --api http://127.0.0.1:8080 --fixture-env output/visual-smoke/phase-0466/20260616-235847/fixture/fixture.env`: PASS.
  - admin login: PASS.
  - convite por link e preview: PASS.
  - resgate do convite com motorista: PASS.
  - busca manual, solicitacao e aprovacao: PASS.
- `./scripts/qa_phase_0466_visual_smoke.sh --api http://127.0.0.1:8080 --fixture-env output/visual-smoke/phase-0466/20260616-235847/fixture/fixture.env --output-dir output/visual-smoke/phase-0466/20260617-final-flow-matrix-0979`: PASS.

## Prints revisados

Pasta: `output/visual-smoke/phase-0466/20260617-final-flow-matrix-0979`

- Passageiro:
  - `passenger-trip.png`
  - `passenger-presence.png`
  - `passenger-account.png`
- Motorista:
  - `driver-home.png`
  - `driver-account.png`
- Responsavel:
  - `guardian-home.png`
  - `guardian-presence.png`
  - `guardian-location.png`
  - `guardian-account.png`
- Portal admin:
  - `admin-web/admin-dashboard.png`
  - `admin-web/admin-drivers.png`
  - `admin-web/admin-passengers.png`
  - `admin-web/admin-guardians.png`
  - `admin-web/admin-links.png`
  - `admin-web/admin-account.png`

## Matriz por fluxo

| Fluxo | Cobertura | Status |
| --- | --- | --- |
| Admin: bootstrap/login | Fixture operacional DEV e smoke visual admin com sessao autenticada. | PASS |
| Admin: convite de motorista | `qa_driver_link_dev_smoke.sh` cria convite e valida preview. `admin-drivers.png` confirma UX do portal. | PASS |
| Admin: aprovacao de motorista | `qa_driver_link_dev_smoke.sh` cria solicitacao manual e aprova via portal API admin. | PASS |
| Admin: passageiros/responsaveis | `admin-passengers.png`, `admin-guardians.png` e testes do gate cobrem telas e estado visual. | PASS |
| Admin: servicos/viagens | Fixture operacional e `admin-dashboard.png`/`admin-links.png` cobrem operacao do dia e vinculos. | PASS |
| Passageiro: cadastro | `cadastro_guided_signup_test.dart` no gate cobre cadastro guiado e nascimento obrigatorio. | PASS |
| Passageiro: e-mail pendente/login | `login_screen_test.dart` no gate cobre estados de login, payload invalido e onboarding sem empresa. | PASS com limite |
| Passageiro: perfil | `passenger-account.png`, testes de conta e smoke HTTP de `/account/me`. | PASS |
| Passageiro: presenca | `passenger-presence.png` e testes de presenca por servico. | PASS |
| Passageiro: viagem/mapa | `passenger-trip.png`, testes map-first e smoke visual. | PASS |
| Motorista: convite por link | `qa_driver_link_dev_smoke.sh` cobre preview, cadastro/login, redeem e session-upgrade. | PASS |
| Motorista: busca manual | `qa_driver_link_dev_smoke.sh` cobre busca, solicitacao e status `PENDING_APPROVAL`. | PASS |
| Motorista: aprovacao/vinculo | `qa_driver_link_dev_smoke.sh` aprova solicitacao e valida `/account/me.empresa.id`. | PASS |
| Motorista: home operacional | `driver-home.png` e testes da home do motorista no gate. | PASS |
| Motorista: conta | `driver-account.png` e testes de conta por papel. | PASS |
| Responsavel: passageiros vinculados | `guardian-home.png`, `guardian-location.png` e testes guardian. | PASS |
| Responsavel: presenca | `guardian-presence.png` e testes de presenca do responsavel. | PASS |
| Responsavel: localizacao | `guardian-location.png` e testes map-first guardian. | PASS |
| Responsavel: conta | `guardian-account.png` e testes de conta por papel. | PASS |

## Limites conhecidos

- O smoke valida estados de e-mail no app e contratos autenticados, mas nao
  confirma entrega real no provedor de e-mail/Resend. Isso deve virar um smoke
  dedicado com evento de provider ou caixa de QA.
- As contas mobile mostram a primeira dobra limpa, mas informacoes especificas
  de papel podem exigir rolagem quando o usuario ainda tem pendencias de perfil.
- A fixture operacional ainda deixa `nascimento` e `foto` ausentes para alguns
  papeis. A UI orienta a edicao, mas uma proxima rodada deve validar tambem a
  criacao com perfil completo e upload real de foto por papel.

## Proximos cards sugeridos

1. `Backlog 0983 — PROFILE-APP-0003: completude de perfil por papel e primeira dobra`
   - Garantir que passageiro, motorista, responsavel e admin tenham uma jornada
     de completar nascimento/foto sem parecer erro.
   - Validar primeira dobra de conta com e sem pendencias.
   - Adicionar fixture visual com perfil completo.
2. `Backlog 0984 — EMAIL-QA-0001: smoke real de e-mail e convites em DEV`
   - Criar smoke que dispara verificacao/convite e confirma tentativa real no
     provider ou mailbox de QA.
   - Registrar explicitamente quando o app esta bloqueado por e-mail pendente.
3. `Backlog 0985 — FLOW-QA-0001: roteiro de iPhone fisico para deep links`
   - Validar convite de motorista em device fisico com build local/TestFlight
     quando a distribuicao mobile estiver pronta.

## Resultado

0979 pode ser fechado como PASS funcional: os fluxos principais estao cobertos
por testes, smoke HTTP real em DEV e prints reais do app. A experiencia ainda
tem refinamentos claros para a proxima fase, principalmente e-mail real,
perfil completo e validacao em device fisico.
