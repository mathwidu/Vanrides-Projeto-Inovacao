# APP-NAV-AUDIO-CONFIRM-001 — Confirmação humana de áudio TTS

## Resultado esperado

Uma pessoa confirma que a frase de fallback de navegação foi realmente ouvida e
compreendida no dispositivo ou simulador usado no smoke.

## Estado

`blocked` — a confirmação humana ainda não é executável. O pacote histórico de
2026-07-06 não possui o manifesto técnico obrigatório e é recusado pelo
validador atual. Primeiro deve ser concluído
`APP-NAV-AUDIO-PROVENANCE-RERUN-001`; só então este card transiciona para
`waiting_human`.

## Impact Analysis

### Por que não está ativo

A automação já consegue provar que o evento e a chamada TTS ocorreram, mas não
consegue provar a experiência auditiva humana. Além disso, o validador atual
exige `navigation-tts-device-smoke.provenance.json`; esse arquivo não existe no
pacote legado. Este item não deve ocupar o WIP nem ser marcado como concluído
por inferência ou por marker solto.

### Contratos afetados

Somente o contrato de evidência e confirmação sensorial. Não há mudança de
app, backend, API, schema, auth, Maps ou dado do produto.

### Riscos e mitigação

- ouvir bytes diferentes dos validados: usar somente o pacote cuja proveniência
  SHA/tree e hashes passaram no predecessor;
- promover marker legado como PASS: manter veredito canônico bloqueado e
  registrar a falha real do validador;
- registrar voz ou PII: a confirmação contém apenas papel, ambiente, frase,
  data e resultado, sem gravação ou nome desnecessário.

## Evidência legada suplementar

Diretório local ignorado:

`src/aplicativo-motorista/output/navigation-tts-device-smoke/20260706-tts-sim-iphone16e/`

- worksheet SHA-256:
  `571aba090b55bb07420135c492b67a722079cdcd0d7d3934e8268d25c6fdbdcc`;
- log SHA-256:
  `7bb726cb7b489bc4b5494ec1436ad2b330cf45414ac559ee26951e566d682536`;
- marker `NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY` presente;
- manifesto `navigation-tts-device-smoke.provenance.json` ausente;
- `--assert-confirmed` termina com exit `1` e
  `Evidencia tecnica invalida: manifest ausente`.

Esse diretório é histórico local, não evidência técnica fechável. A evidência
canônica do estado atual está em
`program/evidence/APP-NAV-AUDIO-CONFIRM-001.md`.

## Dependências

- `APP-NAV-AUDIO-PROVENANCE-RERUN-001` concluído;
- pacote proveniente preservado no mesmo SHA/tree do app;
- pessoa e ambiente de audição identificados somente no momento da prova.

## Escopo

- ouvir a frase proveniente em PT-BR no simulator/device do pacote;
- registrar papel, ambiente, idioma, data e resultado exato;
- executar o assert humano do pacote e atualizar a evidência canônica.

## Fora de escopo

- alterar código, copy, TTS, navegação ou manifest técnico;
- reutilizar o pacote legado como substituto da nova run;
- gravar áudio/voz ou usar dados reais;
- autorizar campo, piloto externo, produção, push ou deploy.

## Definition of Ready

- [ ] predecessor proveniente concluído e commit/evidência registrados;
- [ ] manifesto técnico, log, worksheet e hashes verificados;
- [ ] allowlist do app aponta somente para o diretório PASS exato;
- [ ] pacote está `PENDING_AUDIO_CONFIRMATION`, nunca `CONFIRMED` por inferência;
- [ ] simulator/device e papel humano disponíveis;
- [ ] nenhum dado real ou segredo está presente.

## Critérios de aceite

- [ ] Pessoa identificada apenas pelo papel, sem dado pessoal desnecessário.
- [ ] Dispositivo/simulador, versão e idioma registrados.
- [ ] A frase exata `Use o fallback para seguir com seguranca` foi ouvida e
  compreendida.
- [ ] Resultado, data e eventual problema registrados na evidência.
- [ ] `--assert-confirmed` passa somente após o valor humano exato `SIM`.

## Segurança e privacidade

Não registrar áudio, voz ou nome da pessoa sem necessidade e consentimento.

## Rollback

Se a versão, o pacote ou o ambiente divergir, invalidar a confirmação e voltar
a `blocked`/`waiting_human` conforme a etapa. Nunca copiar `SIM` para outro
pacote nem tratar a exclusão de evidência como sucesso.
