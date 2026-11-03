# VR-MAP-001-EVIDENCE-DATA-GUARD-001 — Impedir PII, GPS e segredo em evidências Maps

## Resultado esperado

Gate Maps varre todos os arquivos rastreados e outputs/evidências gerados,
valida proveniência pública/sintética por prova independente e falha fechado para
GPS, PII e segredo. `publicOrSynthetic: true` isolado não autoriza fixture.

## Estado

`analyzed` — finding `F-PRIV-014`; corpus, padrões, exceções e integration points
do gate ainda precisam ser congelados.

## Dono operacional

`MAP/QA`, com `SEC`, `PRIV`, `PGM` e `OPS` como revisores.

## Risco ou objetivo associado

`F-PRIV-014`, `R-007`, `R-010`, `VR-MAP-001`, G0, G1 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- enumerar tracked files e outputs create-only do gate Maps;
- exigir origem/licença/gerador/digest para fixture pública/sintética;
- detectar padrões de GPS, contato, token, chave e PII com allowlist mínima;
- validar manifest contra arquivo real, commit e checksum;
- adicionar casos positivos/negativos e impedir bypass por extensão/binário.

### Contratos afetados

Gate local/CI Maps, fixtures, manifest/evidence e readiness de promoção; não
muda API de route planning.

### Riscos e mitigação

Falso positivo pode bloquear fixture; autodeclaração pode mascarar dado real.
Usar proveniência verificável, fixtures canário e waiver humano explícito/curto.

### Validação mínima

Testes com PII/GPS/segredo em tracked/output, manifest falso, arquivo sem origem,
caso sintético válido, symlink/binário e gate Maps completo no mesmo SHA.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- política de fixture/evidência e classes proibidas revisadas;
- composição com `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` definida.

## Escopo

- repo Maps, fixtures, outputs/evidências e integração do gate.

## Fora de escopo

- inspecionar dado real, autorizar provider/produção ou auto-waivar achado.

## Definition of Ready

- [ ] corpus, roots, padrões, origem e formato do manifest definidos;
- [ ] paths/scripts/testes/CI/rollback congelados;
- [ ] canários não se parecem com credenciais reais válidas.

## Critérios de aceite preliminares

- [ ] tracked files e outputs gerados são enumerados sem diretório omitido;
- [ ] flag booleana sem prova de origem/digest é rejeitada;
- [ ] GPS/PII/segredo canário falham em source, fixture e evidence;
- [ ] fixture pública/sintética válida passa com proveniência verificável;
- [ ] symlink, extensão desconhecida, binário e manifest divergente falham fechado;
- [ ] gate Maps e ratchet coordenador passam no mesmo SHA/digest.

## Rollback

Reverter a versão do guard mantendo promoção `HOLD` e evidência suspeita
quarentenada. Nunca desabilitar scan/proveniência para liberar um candidato.
