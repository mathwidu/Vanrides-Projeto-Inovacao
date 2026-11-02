# QA Navigation TTS Device Smoke

## Objetivo

Provar o caminho nativo de voz da navegacao do motorista em iOS
Simulator/device sem envolver backend, dados reais, parceiro, campo real,
push, deploy ou VPS.

## Comando Rapido

```bash
./scripts/qa_navigation_tts_device_smoke.sh --check-only
```

Resultado esperado:

```text
NAVIGATION_TTS_DEVICE_SMOKE_OK scenarios=2 evidence_root=output/navigation-tts-device-smoke
```

## Comando Em Device

```bash
./scripts/qa_navigation_tts_device_smoke.sh \
  --run-device \
  --device <ios-device-ou-simulator> \
  --output-dir output/navigation-tts-device-smoke/<timestamp>
```

O comando roda:

```bash
flutter drive \
  --driver=test_driver/navigation_tts_device_smoke_driver.dart \
  --target=integration_test/navigation_tts_device_smoke_test.dart \
  -d <device> \
  --dart-define=VANRIDES_NAV_TTS_DEVICE_SMOKE=true \
  --dart-define=VANRIDES_NAV_TTS_SOURCE_SHA=<commit> \
  --dart-define=VANRIDES_NAV_TTS_SOURCE_TREE=<tree> \
  --dart-define=VANRIDES_NAV_TTS_RUN_ID=<run-id>
```

O produtor calcula `source_sha`, `source_tree` e `run_id` antes de iniciar o
Flutter e os fornece ao teste por `dart-define`. O marker emitido dentro do
`flutter drive` precisa repetir exatamente os tres valores:

```text
NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY source_sha=<commit> source_tree=<tree> run_id=<run-id> scenario=driver-navigation-repeat
```

## Evidencia

Salvar tudo em:

```text
output/navigation-tts-device-smoke/
```

Evidencias esperadas:

- `navigation-tts-device-smoke.log`;
- `audio-confirmation.md`;
- `navigation-tts-device-smoke.provenance.json`;
- linha `NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY` vinculada ao commit, tree e
  run id da execucao;
- confirmacao manual de que a frase foi audivel no device.

Cada `--output-dir` representa uma unica execucao. Ele e criado de forma
exclusiva e nao e sobrescrito: repetir o comando com o mesmo caminho falha antes
de tocar no log, checklist ou manifesto existentes.

O manifesto de proveniencia e estritamente tecnico. Ele registra commit, tree,
run id, blobs Git e SHA-256 do produtor, validador, teste de integracao e log,
alem de um digest do payload inteiro, incluindo `generatedAtUtc`. Ele nao
registra nem decide se um operador ouviu o audio. A confirmacao humana permanece
no fluxo separado de revisao.

`--verify` valida os objetos Git do commit gravado e continua verificavel quando
o HEAD avanca. Quando um consumidor precisar provar a versao atual, deve usar
`--require-current-source`; essa exigencia e separada da integridade historica.

O produtor tambem imprime `manifest_sha256`. Para uma decisao de release, esse
hash deve ser registrado fora da pasta de output, no manifesto de release ou na
evidencia versionada do programa. O digest interno e o modo somente leitura
detectam alteracao acidental; sem esse anchor externo ou uma assinatura, eles
nao sao um selo contra alguem capaz de reescrever o log e recalcular os hashes.

## Classificacao

- `NAVIGATION_TTS_DEVICE_SMOKE_OK`: contrato rapido passou, sem device.
- `NAVIGATION_TTS_DEVICE_SMOKE_DEVICE_OK`: app abriu no device e acionou o
  fluxo TTS sem crash.
- `BLOCKED_VALIDATION_DEVICE`: device, provisioning, trust, Developer Mode ou
  conexao iOS impediram a prova.

## Limites

- O check-only nao prova audio real.
- O run-device automatizado prova wiring nativo, mas a prova audivel depende do
  operador ouvir a frase e registrar a evidencia local.
- Um marker antigo, de outro SHA/tree ou de outro run id e invalido, mesmo que o
  texto generico `NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY` esteja presente.
- O manifesto local e create-only e tamper-evident para alteracao acidental,
  nao uma attestation assinada de device.
- Este smoke nao promove campo real, nao promove parceiro, nao libera dados
  reais e nao substitui piloto controlado.
- Este smoke nao deve conter nome real de passageiro, endereco privado,
  empresa parceira, rota de campo, segredo ou token.

## Criterio Para Promocao

So considerar voz pronta para piloto interno quando:

- `./scripts/qa_navigation_tts_device_smoke.sh --check-only` passar;
- `--run-device` passar em iPhone ou iOS Simulator visivel ao Flutter;
- o operador registrar que a frase foi audivel;
- `./scripts/qa_gate_card.sh --no-e2e` continuar verde;
- campo real e contato externo continuarem bloqueados ate aprovacao separada.
