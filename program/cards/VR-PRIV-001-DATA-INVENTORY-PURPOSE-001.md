# VR-PRIV-001-DATA-INVENTORY-PURPOSE-001 — Inventariar dados, fluxos, finalidade declarada e lacunas

## Resultado esperado

Existe um inventário versionado das categorias e fluxos de dados do VanRides,
ligando fonte, campos/tabelas, titular, produtor, consumidor, armazenamento,
logs/cópias, finalidade declarada, aprovação e lacunas. O artefato diferencia
fato observado, finalidade apenas declarada e finalidade efetivamente aprovada;
campos sem decisão ficam `UNVALIDATED`, nunca recebem justificativa inventada.

## Estado

`done` — inventário, findings e cards verticais foram congelados no commit
`a3f7140e0a25fe5c440700146a42401500a87e4a`, com SHA-256 do inventário
`d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891` e
evidência canônica. Nenhum dado real, produto ou decisão humana foi alterado.

## Dono operacional

`PRIV/PGM`, com revisão técnica de `SEC`, `BE`, `APP`, `MAP` e `OPS`.

## Risco ou objetivo associado

`R-007`, `R-010`, `VR-PRIV-001`, G0 e G6.

## Impact Analysis

### Mudanças técnicas

- inventariar schemas/migrations, modelos/DTOs, armazenamento local, endpoints,
  eventos, logs, evidências, integrações e documentos existentes;
- mapear cada categoria a produtor/consumidor, titular, localização, fluxo,
  cópias, retenção conhecida e controles atuais;
- marcar origem de cada afirmação com path/SHA e distinguir observado,
  declarado, aprovado, desconhecido e fora do escopo;
- produzir `program/privacy/DATA_INVENTORY.md` sem copiar valores reais.
- criar contratos `analyzed` para todo finding P1/P2 que ainda não possua card
  vertical específico, sem editar produto nem antecipar decisão humana.

### Contratos afetados

Nenhum contrato de produto muda. O inventário descreve contratos existentes e
lacunas; qualquer correção vira card separado antes de editar código, schema,
auth, telemetria ou UX.

### Riscos e mitigação

- omitir uma superfície: cruzar backend, app, Maps, coordenação, infra e
  provedores/documentos;
- confundir nome de campo com finalidade: exigir status e fonte da afirmação;
- vazar PII/segredo/GPS: registrar somente categorias e paths, nunca valores;
- transformar auditoria em parecer legal: marcar decisões humanas
  `UNVALIDATED` e encaminhar ao sucessor.

### Validação mínima

- cobertura dos repositórios e famílias de dados com contagens;
- checagem de links/paths e revisão independente de omissões P1/P2;
- `program check`, JSON, `git diff --check` e secret/PII scan do artefato;
- evidência canônica com SHA, comandos e limites read-only.

## Dependências

- `GOV-PIPELINE-001` concluído;
- fontes locais disponíveis somente para leitura;
- nenhum card de produto precisa ser alterado.

## Escopo

- dados de conta, identidade, sessão/auth, empresa, motorista, passageiro,
  responsável, viagens, presença, localização/mapas, suporte e telemetria;
- banco, app/local storage, logs, outputs/evidências, backups descritos,
  exportações e integrações/provedores conhecidos;
- finalidades documentadas e lacunas de aprovação.
- cards verticais de remediação com owner, dependências, contratos, DoR,
  validação e rollback preliminares;
- sincronização do estado factual deste predecessor no DOR e card canônicos de
  `VR-PRIV-003`, sem preencher decisões humanas.

## Fora de escopo

- escolher base legal, consentimento, prazo, retenção ou destino final;
- alterar API, schema, código, log, backup, dado, provider ou configuração;
- consultar banco remoto, mailbox, analytics, produção ou dado real;
- aceitar risco, ADR, piloto ou produção.

## Definition of Ready

- [x] resultado, escopo, não-escopo e taxonomia de estados definidos;
- [x] fontes/repositórios e método read-only identificados;
- [x] artefato, owner, revisão, validações e evidência definidos;
- [x] risco de PII/segredo/GPS e mitigação explícitos;
- [x] dependência concluída e nenhum blocker humano/externo para iniciar;
- [x] dirty paths de produto permanecem fora da allowlist.

## Critérios de aceite

- [x] todas as famílias e superfícies em escopo possuem fonte/path verificável;
- [x] cada linha distingue `OBSERVED`, `DECLARED`, `APPROVED`, `UNVALIDATED` ou
  `NOT_APPLICABLE`;
- [x] produtores, consumidores, titulares, armazenamentos e cópias são
  identificados sem valores reais;
- [x] finalidade, retenção e decisão ausentes permanecem lacunas explícitas;
- [x] itens acionáveis alimentam o card VR-PRIV-002 sem implementar correções;
- [x] DOR e card canônicos de VR-PRIV-003 registram conclusão/digest e estado
  factual deste predecessor,
  mantendo lifecycle `NOT_READY` enquanto as demais dependências faltarem;
- [x] revisão independente retorna zero P1/P2 ou os achados viram cards;
- [x] evidência canônica, risco e programa ficam sincronizados.

## Rollback

Reverter inventário, cards derivados, evidência, DOR factual e transições
coordenadoras. Como o card não altera produto nem dados, rollback não exige
migration; uma afirmação incorreta é removida/corrigida com sua fonte, nunca
substituída por finalidade presumida.
