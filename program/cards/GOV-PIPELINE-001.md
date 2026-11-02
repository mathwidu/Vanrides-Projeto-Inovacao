# GOV-PIPELINE-001 — Estabelecer programa e esteira de produção

## Resultado esperado

O VanRides passa a ter uma fonte única de verdade, uma topologia correta dos
repositórios, gates cumulativos, limite de WIP e validação automática suficiente
para iniciar as correções sem perder rastreabilidade.

## Estado

`done`

## Contexto

Planos, backlogs, dashboard, vault e scripts já contêm material valioso, mas
divergem em topologia, prioridade e significado de “verde”. O workspace também
passou a coordenar a maps platform, enquanto documentos antigos ainda descrevem
apenas backend e app.

## Impact Analysis

### Mudanças técnicas

- criar `program/` como autoridade operacional;
- modelar frentes, marcos, épicos, cards, riscos e repositórios;
- integrar workspace e maps platform ao harness;
- criar validador de referências, WIP, estado e dirty paths;
- reduzir duplicação em adapters de agentes;
- preservar e classificar fontes legadas.

### Contratos afetados

- contrato de execução dos cards;
- contrato de conclusão e promoção;
- configuração do harness;
- instruções runtime do workspace.

Nenhum contrato de API ou comportamento de produto é alterado.

### Riscos

- criar burocracia sem automação;
- ocultar trabalho preexistente como parte deste card;
- classificar plano antigo incorretamente;
- fazer o dashboard parecer mais confiável do que a evidência real.

### Mitigação

- modelo pequeno e legível por máquina;
- validador com mensagens acionáveis;
- dirty paths conhecidos registrados, não apagados;
- dashboard tratado explicitamente como projeção;
- fontes legadas preservadas.

### Validação mínima

- JSON válido;
- testes do validador;
- `program_control.py check`;
- harness com workspace, backend, app e mapas;
- dashboard regenerado;
- `git diff --check`;
- revisão de segredos.

### Rollback

Reverter os commits deste card. Nenhuma migração de dados ou alteração de
produto será necessária.

## Escopo

- documentação e modelo operacional;
- templates;
- validador local;
- configuração do harness;
- adapters `AGENTS.md` e `CLAUDE.md`;
- baseline do programa.

## Fora de escopo

- corrigir achados de produto;
- alterar API, app ou maps platform;
- executar deploy;
- publicar commits;
- migrar todos os cards legados neste mesmo passo.

## Critérios de aceite

- [x] `program/` documenta autoridade, pipeline, gates, roadmap e riscos.
- [x] Os três repositórios de código e o workspace estão inventariados.
- [x] Exatamente um card pode estar ativo.
- [x] Dependências e referências inválidas falham automaticamente.
- [x] Dirty paths preexistentes são diferenciados do escopo do card.
- [x] Harness e programa concordam sobre o card ativo.
- [x] O dashboard inclui todas as superfícies.
- [x] O primeiro card de segurança está `ready` após a conclusão.
- [x] Evidências e commits estão registrados.

## Evidências

Ver `program/evidence/GOV-PIPELINE-001.md`.

Commits:

- `7f6e979cf72a0acd46d10a99cdaf02a800eebc42`
- `5040e0034bc44eeb4c6bed8c19e18306424c0888`
