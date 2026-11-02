# QA Navigation TTS Audio Confirmation

## Objetivo

Registrar a confirmacao humana do audio PT-BR da navegacao sem confundir prova
tecnica automatizada com audio realmente ouvido por uma pessoa.

Card: `APP-NAV-AUDIO-CONFIRM-001`.

## Comando Rapido

```bash
./scripts/qa_navigation_audio_confirmation_review.py --check-only
```

Resultado esperado:

```text
NAVIGATION_AUDIO_CONFIRMATION_REVIEW_OK modes=3 requires_human_operator=true
```

## Gerar Pacote De Revisao

Use uma pasta criada pelo smoke de TTS em device/simulador:

```bash
./scripts/qa_navigation_audio_confirmation_review.py \
  --package \
  --evidence-dir output/navigation-tts-device-smoke/<timestamp>
```

Saidas esperadas:

- `navigation-tts-device-smoke.provenance.json` tecnico, valido, create-only e
  com hash ancorado fora do output para qualquer decisao de release;
- `audio-review-package.md`;
- `audio-review-package.json`;
- SHA/tree/run id, digest do manifesto tecnico e digest do checklist humano
  copiados para o pacote;
- `Frase esperada: Use o fallback para seguir com seguranca`;
- status `PENDING_AUDIO_CONFIRMATION` enquanto o operador humano nao marcar
  que ouviu a frase em PT-BR.

## Frase Esperada Pelo Operador

Frase esperada:

```text
Use o fallback para seguir com seguranca
```

Essa frase vem do smoke `driver-navigation-repeat`, que abre o painel de
navegacao do motorista em modo fallback tecnico. Ela nao contem passageiro real,
endereco privado, empresa parceira ou rota de campo.

## Comandos Do Operador

```bash
./scripts/qa_navigation_tts_device_smoke.sh --run-device \
  --device <ios-device-ou-simulator> \
  --output-dir output/navigation-tts-device-smoke/<timestamp>

./scripts/qa_navigation_audio_confirmation_review.py --package \
  --evidence-dir output/navigation-tts-device-smoke/<timestamp>

./scripts/qa_navigation_audio_confirmation_review.py --assert-confirmed \
  --evidence-dir output/navigation-tts-device-smoke/<timestamp>
```

## Confirmar Audio Humano

So rode este comando depois de uma pessoa realmente ouvir a frase:

```bash
./scripts/qa_navigation_audio_confirmation_review.py \
  --assert-confirmed \
  --evidence-dir output/navigation-tts-device-smoke/<timestamp>
```

O checklist deve conter exatamente uma linha canonica de confirmacao. O unico
valor afirmativo aceito nessa linha e o valor exato `SIM`:

```text
- Operador ouviu a frase em PT-BR: SIM
```

Qualquer outro texto, incluindo `sim`, `SIM - ouvi`, `NAO OUVIU`,
`não ouviu`, `NAO FOI AUDIVEL`, `confirmado` ou `true`, deve permanecer
pendente. Linha ausente ou duplicada tambem falha de forma fechada. O comando
deve retornar:

```text
PENDING_AUDIO_CONFIRMATION
```

## Politica De Evidencia

Este pacote nao deve conter dados reais, nomes de passageiro, empresa parceira,
endereco privado, rota de campo, segredo ou token.

Mesmo com audio confirmado, este card nao promove piloto externo, nao libera
campo real, nao libera parceiro e nao substitui aprovacao separada de release.

## Criterio Para Avancar

- `qa_navigation_tts_device_smoke.sh --run-device` passou no device/simulador.
- `navigation-tts-device-smoke.provenance.json` foi validado contra os objetos
  Git e o digest do log daquela execucao.
- O log contem `NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY`.
- Um operador humano ouviu a frase em PT-BR.
- `audio-confirmation.md` contem somente o valor exato `SIM` no campo indicado.
- `audio-review-package.json` registra `operatorHeardPtBr=true`.
- `./scripts/qa_gate_card.sh --no-e2e` continua verde.
