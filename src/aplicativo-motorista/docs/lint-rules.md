# VanRides App Lint Rules

Este documento resume as regras customizadas habilitadas alem do
`package:flutter_lints/flutter.yaml` padrao.

## Regras Ativas

- `avoid_print`: use `debugPrint`, logger dedicado ou tratamento silencioso
  quando a mensagem for apenas diagnostica.
- `unawaited_futures`: futures intencionalmente soltas devem usar
  `unawaited(...)` para deixar a decisao explicita.
- `prefer_const_constructors`: widgets/objetos imutaveis devem ser `const`
  quando possivel.
- `prefer_const_literals_to_create_immutables`: listas/mapas/sets imutaveis
  dentro de widgets devem preferir literais `const`.
- `library_private_types_in_public_api`: APIs publicas nao devem expor tipos
  privados, preservando contratos estaveis entre features.
- `use_build_context_synchronously`: qualquer uso de `BuildContext` apos
  `await` precisa validar `mounted` ou reorganizar o fluxo.
- `require_trailing_commas`: chamadas multilinha mantem virgula final para
  reduzir diffs e estabilizar `dart format`.

## Validacao

Antes de fechar card que altera Dart:

```bash
flutter analyze
flutter test
```

Para cards locais sem E2E, o gate recomendado segue sendo:

```bash
RUN_E2E=0 ./scripts/qa_gate_card.sh
```
