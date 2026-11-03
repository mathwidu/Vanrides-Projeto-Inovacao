# QA Navigation Audio Operator Handoff

Card: `APP-NAV-AUDIO-HANDOFF-001`
Relaciona-se com: `APP-NAV-AUDIO-CONFIRM-001`

## Objetivo

Deixar um handoff curto, versionado e verificavel para a pessoa que vai ouvir
o audio PT-BR da navegacao. Este documento nao confirma o audio; ele organiza
o que ja existe para que o operador humano consiga confirmar ou manter
`PENDING_AUDIO_CONFIRMATION` sem ambiguidade.

## Evidencia Atual

Pasta local de evidencia tecnica:

```text
output/navigation-tts-device-smoke/<timestamp>
```

Substitua `<timestamp>` pelo identificador da execucao tecnica que sera
revisada. O handoff nao aponta para uma execucao historica fixa.

Arquivos esperados nessa pasta:

- `navigation-tts-device-smoke.log`
- `navigation-tts-device-smoke.provenance.json`
- `audio-confirmation.md`
- `audio-review-package.md`
- `audio-review-package.json`

Marker tecnico esperado no log:

```text
NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY
```

Estado atual do pacote:

```text
PENDING_AUDIO_CONFIRMATION
operatorHeardPtBr=false
```

## Frase Que Deve Ser Ouvida

```text
Use o fallback para seguir com seguranca
```

Essa frase e sintetica e nao contem passageiro real, endereco privado, empresa
parceira, rota de campo, segredo ou token.

## Passo A Passo Do Operador

1. Abrir o iOS Simulator/device usado no smoke, ou rodar novamente o smoke:

```bash
./scripts/qa_navigation_tts_device_smoke.sh --run-device \
  --device <ios-device-ou-simulator> \
  --output-dir output/navigation-tts-device-smoke/<timestamp>
```

2. Gerar ou atualizar o pacote de revisao:

```bash
./scripts/qa_navigation_audio_confirmation_review.py --package \
  --evidence-dir output/navigation-tts-device-smoke/<timestamp>
```

3. Ouvir a frase PT-BR no Mac/device com volume audivel.

4. Confirmar que o pacote registra a integridade do manifesto tecnico, com
   SHA/tree/run id e digest do log.

   Para decisao de release, confirmar tambem que `manifest_sha256` foi ancorado
   fora desta pasta na evidencia versionada do programa/manifesto de release.

5. Editar somente a linha abaixo em `audio-confirmation.md`, usando o valor exato `SIM`
   e nenhum texto adicional:

```text
Operador ouviu a frase em PT-BR: SIM
```

6. Confirmar o pacote:

```bash
./scripts/qa_navigation_audio_confirmation_review.py --assert-confirmed \
  --evidence-dir output/navigation-tts-device-smoke/<timestamp>
```

Enquanto a linha continuar pendente, o comando acima deve falhar com:

```text
PENDING_AUDIO_CONFIRMATION
```

Valores ambiguos ou negativos como `sim`, `SIM - ouvi`, `NAO OUVIU`,
`não ouviu` e `NAO FOI AUDIVEL` tambem permanecem pendentes.
O checklist deve ter exatamente uma linha `Operador ouviu a frase em PT-BR:`;
linha ausente ou duplicada tambem nao confirma.

## Limites

- Este handoff nao confirma audio humano.
- Este handoff nao libera parceiro.
- Este handoff nao libera campo.
- Este handoff nao promove piloto externo.
- A evidencia nao deve conter dados reais, nomes de passageiros, empresa
  parceira, endereco privado, rota de campo, segredo ou token.

## Criterio Para Fechar `APP-NAV-AUDIO-CONFIRM-001`

O card de confirmacao so pode sair de `IN_PROGRESS` quando:

- uma pessoa ouvir a frase em PT-BR;
- `audio-confirmation.md` registrar `SIM`;
- o manifesto tecnico da mesma execucao continuar valido;
- `audio-review-package.json` registrar `operatorHeardPtBr=true`;
- `--assert-confirmed` passar;
- `./scripts/qa_gate_card.sh --no-e2e` continuar verde.
