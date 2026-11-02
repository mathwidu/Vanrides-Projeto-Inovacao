# QA Performance Control - VanRides App

Baseline: 2026-07-06

Objetivo: manter performance observavel nos fluxos criticos de mapa, tracking,
sheets e motion sem transformar o gate rapido em um benchmark caro, fragil ou
dependente de device, backend e credenciais.

## Gate rapido

- Runner canonico: `scripts/qa_performance_smoke.sh`
- Check rapido no gate: `./scripts/qa_performance_smoke.sh --check-only`
- Saida atual esperada:
  `PERFORMANCE_CONTROL_OK surfaces=7 evidence_root=output/performance-smoke`

O check rapido valida que este contrato existe, que as superficies criticas
foram nomeadas, que a pasta de evidencias esta definida, que existe regra de
follow-up antes de release e que falhas de setup nao sao confundidas com
regressao de performance.

## Superficies Criticas

| ID | Dono principal | Risco monitorado | Evidencia minima quando mudar |
| --- | --- | --- | --- |
| `passenger-tracking-map` | `TripLiveMapPanel`, `MapFirstTripTrackingView` | mapa, geometria de rota e updates de localizacao ao vivo podem travar ou atrasar resposta. | Widget/smoke focado e nota no QA log; profile/manual smoke quando o mapa real mudar. |
| `passenger-footer-sheet` | `PassengerMapFooterSheetScaffold` e sheets da viagem | drag, snap, expanded content e texto aumentado podem gerar jank ou overflow caro. | Widget/visual smoke e observacao de performance no QA log. |
| `driver-navigation-panel` | `DriverNavigationPanel`, `DriverHomeNavigationPanel` | atualizacao de instrucao, ETA, fallback e voz podem reconstruir UI demais durante a rota. | Teste focado de navegacao e nota de risco/resposta. |
| `driver-active-trip-sheet` | widgets da sheet ativa do motorista | lista de paradas, proxima acao e contexto de rota podem ficar densos em movimento. | Teste focado da sheet e follow-up se houver carregamento excessivo. |
| `guardian-location-surface` | superficies de mapa/localizacao do responsavel | troca de passageiro vinculado e status de localizacao podem atrasar ou piscar. | Widget/smoke do guardian ou justificativa documentada. |
| `shared-motion-policy` | `VanRidesMotionPolicy`, `TripLiveMapMotionPolicy` | animacoes, camera settle e reduced motion podem causar desconforto ou custo de frame. | Testes de motion e nota de reduced motion quando houver mudanca. |
| `map-marker-layer` | markers, placeholders e superficies deterministicas do mapa | densidade de marcador, custom paint e repaint podem pesar no mapa. | Testes de marker/surface e follow-up se aparecer jank evidente. |

## Pasta De Evidencias

Evidencias pesadas ficam fora do Git em:

```text
output/performance-smoke/<card-ou-fase>/<timestamp>/
```

Exemplos:

```text
output/performance-smoke/qa-performance-001/20260706-check/
output/performance-smoke/app-driver-nav-001/profile-mode/
output/performance-smoke/ux-driver-sheet-001/manual-iphone/
```

Essas pastas podem guardar logs, notas de perfil, screenshots de DevTools,
contagens de frame ou videos curtos. Nao versionar videos, logs com credenciais,
fixtures privadas, dumps pesados ou artefatos de profile com dados reais de
passageiro, motorista, responsavel, escola ou empresa parceira.

## Classificacao De Falhas

| Sinal | Classificacao | Acao |
| --- | --- | --- |
| `PERFORMANCE_CONTROL_OK` ausente no `--check-only` | Regressao de contrato QA | Corrigir documento, script ou wiring antes de fechar o card. |
| Device, profile mode, fixture ou backend ausente | setup | Registrar limite no QA log; nao chamar de bug de produto. |
| Log de perfil nao coletado porque o card nao mudou UI pesada | nao aplicavel | Registrar que o check rapido cobriu o contrato e que nao houve smoke manual pesado. |
| Frame jank evidente ao arrastar sheet, mover mapa ou receber update ao vivo | regressao de performance | Corrigir no card ou abrir follow-up antes de release com superficie, reproducao e evidencia. |
| Carregamento excessivo, skeleton infinito ou CTA que demora a responder | regressao de performance | Corrigir no card ou abrir follow-up antes de release. |
| Texto aumentado/high contrast/reduced motion deixa a tela lenta ou instavel | regressao de performance e acessibilidade | Corrigir ou abrir follow-up antes de release com referencia tambem ao smoke de acessibilidade. |

## Regra Para Cards Futuros

Cada card que muda mapa, tracking, sheet, motion ou marcador deve declarar no
`backlog/QA-Execution-Log.md`:

- superficie(s) da tabela afetadas;
- risco de performance esperado;
- comando automatizado executado;
- smoke manual/profile executado ou justificativa de nao execucao;
- path em `output/performance-smoke/` quando houver evidencia pesada;
- classificacao de qualquer falha como setup ou regressao de performance;
- follow-up antes de release quando houver jank evidente, carregamento
  excessivo ou resposta lenta que nao foi corrigida no mesmo patch.

Mudancas sem UI pesada podem registrar que o contrato de performance foi
validado pelo gate rapido e que o smoke manual nao se aplica.
