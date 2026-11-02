# VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001 — Preparar matriz de minimização, consentimento e auditoria

## Resultado esperado

O inventário técnico concluído, revisado e congelado por digest é convertido
numa matriz revisável que, por categoria/fluxo, propõe minimização, necessidade,
transparência/consentimento quando aplicável, acesso, eventos de auditoria e
lacunas. `APPROVED` continua reservado a decisão humana ou técnica específica e
identificada; nenhuma proposta vira controle de produto sem card e aceite
próprios.

## Estado

`done` — matriz e verificador congelados em 2026-07-29 no commit
`830238cccda4b089fe7ef33fff9b755544dc0acc`; blob da matriz
`dd863a45a5931a883277af73dbfc90a19c075f9e`, SHA-256
`0e4ddcb0af05158f4d3f1c6fffe7dd7c518c46b02d587b17823c62a87b14602a`.
O inventário pai continua imutável, nenhuma decisão humana foi inferida,
nenhum sucessor foi promovido e nenhum path de produto foi aberto.

## Dono operacional

`PRIV`, com `Produto`, `SEC`, `PGM` e `Jurídico/DPO` como revisores/decisores
conforme a coluna.

## Risco ou objetivo associado

`R-007`, `R-021`, `R-023`–`R-026`, `VR-PRIV-002`, G6 e G7.

## Impact Analysis

### Mudanças técnicas

- consumir somente o inventário versionado e preservar suas fontes/status;
- criar `program/privacy/MINIMIZATION_CONSENT_AUDIT_MATRIX.md`;
- classificar campos/fluxos por necessidade, minimização possível, acesso,
  transparência/consentimento pendente e evento auditável;
- separar recomendações técnicas de decisões Produto/Privacidade/Jurídico.
- endurecer o controle do programa para que a revisão ADR-005 superseded não
  possa ser aceita, promovida ou confundida com o próximo freeze.

### Contratos afetados

Documentação de decisão e contrato de governança de
`scripts/program_control.py` nesta etapa. Mudanças futuras em coleta, payload,
UI/consentimento, RBAC, logging, retenção ou provider exigem cards verticais
próprios com compatibilidade, testes e rollback.

### Riscos e mitigação

- matriz virar aprovação tácita: cada coluna carrega status e owner;
- consentimento ser usado sem validação: registrar
  `PENDING_PRIVACY + PENDING_LEGAL_DPO`;
- remover campo necessário sem análise de consumidor: exigir fonte e impacto;
- auditoria reter PII excessiva: descrever evento/categoria, não valor.

### Validação mínima

- todas as linhas do inventário mapeadas ou justificadamente não aplicáveis;
- revisão cruzada SEC/Produto/Privacidade e lista de decisões humanas;
- scan de PII/segredo, links/paths, `program check` e `git diff --check`;
- testes do controlador, evidência canônica e handoff explícito para
  ADR-002/VR-PRIV-003.

## Dependências

- `VR-PRIV-001-DATA-INVENTORY-PURPOSE-001` concluído;
- inventário imutável identificado por commit/digest;
- owners de Produto, Privacidade, Segurança e Jurídico/DPO definidos.

## Entradas obrigatórias do predecessor

O inventário de `VR-PRIV-001` encaminha `F-PRIV-001` a `F-PRIV-019`. O freeze
deste card deve mapear 100% deles a um card vertical existente ou criar um novo
card `analyzed` com owner, dependências, repositórios, compatibilidade, testes,
smoke, rollout e rollback. Em especial, não podem permanecer apenas como texto
na matriz:

- eliminação da senha bruta da sessão/fallback Basic, migração dos resíduos e
  storage seguro de sessão; refresh token deve ter necessidade, consumo,
  rotação, expiração e remoção provados ou ser removido do app;
- lifecycle verificado de deep links/convites, clipboard/cópia e logs;
- isolamento por conta/tenant e TTL da fila offline;
- DTO/projeção mínima REST, STOMP e mapas para GPS e outros passageiros,
  incluindo perfil PAX entregue a GUA e contatos DRV entregues a PAX;
- diretório público de motoristas default-deny ou removido com compatibilidade;
- redaction de GPS, endereço, coordenada, token, URI e erro de provider;
- refresh/desvínculo de device token antes de habilitar push real;
- outbox D19 e lifecycle D18 independentes, com card de provider real
  dependente de ambos;
- lifecycle, acesso e durabilidade de avatar;
- retenção/purge de banco, tokens, suporte, logs, providers e backups;
- proveniência, redaction, retenção e guard de publicação da mídia de demo;
- boundary S2S e data guard da Maps Platform;
- ratchet de evidência que prove sanitização e retenção;
- permissões mobile, chaves por escopo e configuração de release.
- lifecycles PAX/DRV/GUA/ADM com matriz D01–D34 e owner próprio para cada
  boundary; o delete GUA existente não pode continuar parcial;
- offboarding de tenant separado do offboarding ADM, com state machine, zero
  cascade e orquestração dos quatro lifecycles.

O predecessor já criou os cards verticais de `F-PRIV-001`, `002`, `004`,
`006`–`008`, `010`–`019`; este card deve congelar seu Impact Analysis e impedir
que qualquer um avance a `ready` com allowlist, contrato, testes ou rollback em
aberto. `F-PRIV-003` e `005` também permanecem ligados aos cards trip-scoped e
de adoção existentes.

Durante a revisão de `F-PRIV-010`, o contrato do ratchet revelou um predecessor
técnico obrigatório: `VR-PGM-004-EVIDENCE-V2-BOOTSTRAP-001` instala o
trust-root/scanner/staging antes da ativação, enquanto
`VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` consome esses bytes e implementa o
schema/enforcement v2. A partição é canônica, sem sobreposição, e ambos
permanecem `analyzed`; este card não os inicia nem os promove a `ready`.

Requisitos fail-closed de dois findings não podem ser reduzidos a referências
genéricas de épico:

- `F-PRIV-014` / `VR-MAP-001-EVIDENCE-DATA-GUARD-001`: o card vertical deverá varrer arquivos rastreados
  e evidências/outputs gerados, validar proveniência pública/sintética sem
  depender apenas de `publicOrSynthetic: true`, falhar fechado para padrões de
  GPS/PII/segredo e provar casos positivos e negativos no gate Maps;
- `F-PRIV-010` / `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001`: o card vertical deverá validar presença e formato de
  checksum, data, comando, retenção e confirmação de sanitização da evidência
  canônica, com testes de ausência/malformação; validar somente path/ID não
  satisfaz o aceite.

Os findings já cobertos por cards canônicos são referenciados, não duplicados.
Se uma correção exigir múltiplos repositórios, cada um mantém commit, gate,
smoke e rollback próprios.

## Escopo

- necessidade/minimização por categoria e fluxo;
- transparência, consentimento ou outra decisão marcada para validação humana;
- acessos/papéis, eventos de auditoria mínimos e lacunas;
- cards/ADRs sucessores para mudanças e lifecycle.
- sincronização do estado factual deste predecessor no DOR canônico de
  `VR-PRIV-003`, sem registrar aceite humano.
- ratchets documentais e de programa necessários para provar cobertura do DAG
  e manter ADR-005 fail-closed durante o refreeze.

## Fora de escopo

- declarar base legal, obter consentimento ou aceitar risco em nome humano;
- editar o inventário pai congelado; correções exigem reabrir/versionar o
  predecessor e produzir novo digest antes de consumir a revisão;
- mudar código, schema, coleta, logs, provider, UI ou dados;
- definir retenção/exclusão final da ADR-002;
- push, deploy, piloto ou produção.

## Definition of Ready

- [x] inventário pai concluído e digest congelado;
- [x] owners/revisores por tipo de decisão identificados;
- [x] formato, status, fontes e regras de cobertura da matriz definidos;
- [x] critérios de minimização/auditoria e limites jurídicos revisados;
- [x] dirty paths, validações, evidência e rollback congelados.

## Critérios de aceite

- [x] 100% do inventário tem decisão técnica ou lacuna/owner explícitos;
- [x] nenhuma coluna humana é preenchida por inferência;
- [x] recomendações de minimização identificam consumidores e impacto;
- [x] auditoria proposta é suficiente e minimizada, sem valores sensíveis;
- [x] mudanças necessárias viram cards dependentes, não edições ocultas;
- [x] `F-PRIV-001` a `F-PRIV-019` possuem roteamento sem duplicação e todo P1/P2
  sem cobertura anterior virou card vertical `analyzed` antes da conclusão;
- [x] D01–D34 têm aplicabilidade fail-closed para menor, exportação e GPS, e
  exportação do titular não é reduzida a D20/D32;
- [x] hold, extração forense e comunicação permanecem decisões separadas, sem
  Produto como autoridade do hold;
- [x] os cards de `F-PRIV-010` e `F-PRIV-014` contêm os critérios fail-closed
  acima, repositórios afetados e testes do próprio ratchet/guard;
- [x] ADR-002 recebe entradas preliminares coerentes e rastreáveis;
- [x] DOR canônico de VR-PRIV-003 registra conclusão e digest deste predecessor,
  sem converter worksheet em aceite ou autorização;
- [x] evidência, riscos e estados ficam sincronizados.

## Rollout

Congelar matriz/verificador em commit próprio, vincular ADR/DOR ao mesmo
commit/blob/SHA-256 e só então concluir o card por evidência canônica. Qualquer
divergência de cobertura reabre a revisão; nenhuma recomendação é promovida
automaticamente a decisão humana ou product path.

## Rollback

Reverter matriz/evidência/DOR factual/transições. Nenhuma recomendação documental autoriza
reverter ou remover dados; divergência humana mantém a linha pendente e exige
nova revisão antes de qualquer card técnico.
