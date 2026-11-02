# Tracking Premium Map Visual Acceptance

Origem: `TRACKING-APP-0008`
Data: 2026-06-14

Este handoff define a aceitacao visual minima do mapa premium antes de liberar
`PHASE-0502` para estabilizacao E2E completa por papel.

## Viewports

Executar a aceitacao automatizada e, quando houver device disponivel, smoke
manual nos seguintes tamanhos:

- 375x667
- 390x844
- 430x932
- 768x1024

## Estados Obrigatorios

Os cenarios abaixo precisam renderizar sem overflow, sem card branco indevido,
sem depender apenas de cor e com labels humanos para sinal e rota:

- passageiro live
  - Deve mostrar distancia da van em paradas.
  - Deve mostrar `Sinal ao vivo`.
  - Deve manter bottom sheet premium escuro.
- passageiro stale
  - Deve mostrar `Sinal antigo`.
  - A van nao pode comunicar movimento ao vivo.
  - A rota stale deve usar linguagem degradada.
- responsavel selected passenger
  - Deve destacar `Passageiro selecionado`.
  - Deve preservar troca de passageiro sem quebrar layout.
  - Deve exibir status humano do sinal.
- motorista planned
  - Deve mostrar status do sinal e progresso planejado.
  - Deve manter CTA de inicio e acesso ao mapa.
- motorista in progress
  - Deve mostrar proxima parada, ETA, sinal e paradas restantes.
  - Deve preservar acoes de concluir parada, Maps, nao embarcou e encerrar.
- no route
  - Nao deve desenhar linha falsa.
  - Deve explicar que a rota ainda nao foi publicada ou materializada.
- no signal
  - Deve mostrar estado de aguardando localizacao ao vivo.
  - Nao deve parecer erro fatal nem sinal em tempo real.

## Entry Points E2E

Quando `PHASE-0502` iniciar, validar os fluxos completos a partir destes
entrypoints:

- `integration_test/passageiro/all.dart`
- `integration_test/guardian/all.dart`
- `integration_test/motorista/all.dart`

Comandos base:

```bash
flutter test integration_test/passageiro/all.dart -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true
flutter test integration_test/guardian/all.dart -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true
flutter test integration_test/motorista/all.dart -d "<device>" --dart-define=VANRIDES_API_BASE_URL=http://127.0.0.1:8080 --dart-define=VANRIDES_E2E_TEST_MODE=true
```

## Gate de Liberacao

`PHASE-0502` permanece bloqueada se qualquer item abaixo falhar:

- Widget tests de aceitacao visual do mapa premium.
- `flutter analyze`.
- `flutter test`.
- `./scripts/qa_gate_card.sh --no-e2e`.
- QA log com evidencia do card `TRACKING-APP-0008`.

## Observacoes

- E2E real em device fisico nao faz parte deste card.
- O objetivo aqui e garantir que a experiencia premium esta suficientemente
  estavel para a proxima fase de campanha manual/E2E por papel.
