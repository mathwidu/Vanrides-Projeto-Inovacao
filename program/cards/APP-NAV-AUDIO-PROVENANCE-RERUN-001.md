# APP-NAV-AUDIO-PROVENANCE-RERUN-001 — Repetir smoke TTS com proveniência verificável

## Resultado esperado

Uma nova execução do smoke TTS produz, no mesmo diretório create-only, log,
worksheet e `navigation-tts-device-smoke.provenance.json` íntegros, vinculados a
um SHA/tree limpos do app. O pacote de revisão termina tecnicamente válido e
`PENDING_AUDIO_CONFIRMATION`, liberando a etapa humana sem alegar que o áudio
foi ouvido.

## Estado

`analyzed` — a falha do pacote legado está reproduzida, mas SHA/tree, worktree,
simulator/device e output exatos da nova run ainda precisam ser congelados
antes de `ready`.

## Risco ou objetivo associado

`R-003`, `R-005`, `VR-QA-004`, G3 e G6.

## Impact Analysis

### Mudanças técnicas

- executar o runner versionado de device em checkout limpo do app;
- gerar o manifest create-only pelo contrato atual de proveniência;
- validar SHA/tree, log, marker, hashes e ausência de dirty drift;
- gerar o pacote de revisão sem editar o valor humano para `SIM`;
- registrar evidência coordenadora reproduzível.

### Contratos afetados

Somente evidência de QA do app. Nenhum código, API, payload, schema, auth,
backend, Maps ou ambiente remoto deve mudar.

### Riscos e mitigação

- contaminar a run com os 51 dirty paths do app principal: usar worktree limpa;
- falar/validar versão diferente: congelar SHA/tree antes e depois;
- gerar falso humano: o pacote deve permanecer `PENDING_AUDIO_CONFIRMATION`;
- usar dado real: executar apenas cenário sintético e varrer outputs.

## Dependências

- runner e validador de proveniência já existentes no app;
- simulator/device compatível disponível localmente;
- nenhum card de produto ativo durante a execução.

## Escopo

- nova run TTS sintética e pacote local em `output/navigation-tts-device-smoke/`;
- validação do manifesto e marker;
- evidência deste card e atualização do handoff humano.

## Fora de escopo

- editar código do app, dependências, copy ou scripts;
- ouvir/confirmar áudio em nome de uma pessoa;
- reutilizar o pacote legado como nova run;
- push, workflow remoto, deploy, campo, piloto ou produção.

## Definition of Ready

- [ ] SHA/tree limpos e worktree isolada congelados;
- [ ] simulator/device, runtime e disponibilidade de TTS confirmados;
- [ ] diretório create-only da nova run definido e ausente;
- [ ] allowlist ampla de preparação substituída pelo diretório exato da run;
- [ ] comandos, cleanup, varredura e rollback revisados;
- [ ] dirty paths do app principal preservados fora do escopo.

## Critérios de aceite

- [ ] SHA/tree antes e depois são idênticos e a worktree permanece limpa;
- [ ] manifesto técnico existe, é create-only e passa o verificador atual;
- [ ] log amarra o marker à mesma run e fonte;
- [ ] pacote de revisão verifica hashes e termina
  `PENDING_AUDIO_CONFIRMATION`;
- [ ] `program.json` referencia somente o diretório exato do pacote produzido,
  não todo `output/navigation-tts-device-smoke/`;
- [ ] nenhum `SIM`, dado real, segredo ou promoção é inferido;
- [ ] evidência canônica registra comandos, outputs, hashes e cleanup;
- [ ] `APP-NAV-AUDIO-CONFIRM-001` passa a `waiting_human` somente após o PASS
  técnico deste predecessor.

## Rollback

Uma run falha e ainda não ancorada por commit/evidência pode ser invalidada e
removida antes de nova tentativa. Depois que um pacote técnico PASS for
referenciado pelo card humano, ele deve ser preservado imutável ou formalmente
supersedido por nova evidência; nunca é removido como rollback. O pacote legado
continua preservado como diagnóstico, e falha de run nunca é renomeada para
PASS.
