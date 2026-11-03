# VR-OPS-001-SENSITIVE-LOG-REDACTION-001 — Remover dados sensíveis de logs e erros

## Resultado esperado

App, backend e Maps registram somente eventos estruturados e minimizados. GPS,
endereço, coordenadas, tokens, URI de convite, segredo/chave, corpo de provider e
PII não aparecem em console, stack, erro HTTP, artifact ou evidência.

## Estado

`analyzed` — finding `F-PRIV-004` validado; catálogo de sinks, política por
ambiente e compatibilidade de diagnóstico ainda precisam ser congelados.

## Dono operacional

`OPS/SEC`, com `APP`, `BE`, `MAP`, `PRIV` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-004`, `R-007`, `R-010`, `VR-OPS-001`, G1, G5 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- inventariar sinks e substituir interpolação sensível por código/correlação;
- normalizar exceções de geocode/e-mail/deep link/STOMP sem body/URL/token;
- remover GPS de debug e configurar níveis seguros por ambiente;
- adicionar redactor central e testes de captura dos sinks;
- varrer outputs/evidências e impedir regressão no gate.

### Contratos afetados

Observabilidade, mensagens de erro, correlação e troubleshooting; wire de
produto só muda se hoje expuser detalhe bruto ao cliente.

### Riscos e mitigação

Redaction pode remover diagnóstico ou regex pode falhar. Preferir allowlist de
campos/códigos, contexto não sensível, testes positivos/negativos e métricas.

### Validação mínima

Testes de sink com canários sintéticos para cada classe proibida, scan de logs/
artifacts, gates dos três repositórios e smokes no mesmo build/JAR/container.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- catálogo de eventos/sinks e política de retenção/log por ambiente definidos.

## Escopo

- logs, stack traces, erros, métricas labels e evidências de app/backend/Maps.

## Fora de escopo

- ingerir logs remotos, alterar produção ou copiar amostra real para teste.

## Definition of Ready

- [ ] classes proibidas, sinks, campos permitidos e owners definidos;
- [ ] paths, testes, smokes, rollout e rollback por repositório congelados;
- [ ] canários são sintéticos e não confundíveis com segredo real.

## Critérios de aceite preliminares

- [ ] cada sink rejeita GPS, PII, token, URI completa, chave e provider body;
- [ ] erro mantém código/correlação útil sem valor sensível;
- [ ] produção não habilita debug sensível por configuração acidental;
- [ ] scans cobrem arquivos rastreados e outputs gerados;
- [ ] gates/smokes passam em versões manifestadas e separadas por repositório.

## Rollback

Reduzir nível/desligar o sink afetado, preservando métricas seguras. Nunca
restaurar payload bruto para recuperar capacidade diagnóstica.
