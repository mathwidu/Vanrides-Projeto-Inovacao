# VR-MAP-001-LOCATION-S2S-001 — Tornar Maps interno e autenticado

## Resultado esperado

O backend só envia payload mínimo de viagem ao Maps Platform por canal interno,
autenticado e criptografado. Sem essa composição ou prova de logs, o adapter
permanece desligado e o fallback seguro continua funcional.

## Estado

`analyzed` — depende do domínio trip-scoped concluído.

## Dependências

- `VR-SEC-001-LOCATION-DOMAIN-001` concluído;
- `VR-MAP-001-EVIDENCE-DATA-GUARD-001` concluído e integrado ao gate Maps;
- `VR-SEC-005-SECRET-SCOPE-ROTATION-001` concluído para metadata/escopo e
  protocolo; provisionamento/rotação live continuam em card autorizado;
- backend e Maps limpos nos SHAs revalidados;
- nenhum secret real, SSH, deploy ou DNS neste card local.

## Escopo

- auth S2S antes do parse, comparação constante e limites de body/rate/deadline;
- secret files `current/next`, overlap máximo 24h e runbook de rotação;
- overlay compartilhada criptografada; health separado do route planner interno;
- IDs opacos, sem nomes/e-mail/telefone/endereço textual;
- logs/tracing sem body, token, geometria ou coordenadas;
- provider real/OSRM desabilitado sem configuração versionada de access logs;
- cliente backend fail-closed e fallback local;
- request/response somente em memória, `no-store`, sem cache, fila, arquivo ou
  persistência, e chamada permitida apenas para viagem ainda ativa/na lease;
- testes sintéticos, validators, gate e smoke de ambos os repositórios.

## Fora de escopo

- provisionar secrets, deploy remoto, engine produtivo, dados reais ou promoção;
- mudar REST/STOMP/app de localização.

## Critérios de aceite

- [ ] chamada ausente/inválida/expirada falha antes de ler body/chamar engine;
- [ ] rotação `current/next`, overlap >24h e rede não criptografada são testados;
- [ ] route planner não é público; health não amplia acesso ao planner;
- [ ] payload/logs/evidência contêm apenas fixtures e IDs opacos;
- [ ] request/response não deixam cache, arquivo, fila ou registro persistido;
- [ ] viagem planned/completed/canceled, lease expirada ou sem escopo nunca chama
  Maps nem engine; somente os pontos mínimos da viagem ativa são enviados;
- [ ] após reassign, versão/amostra do motorista anterior nunca chama Maps; só a
  primeira amostra válida do novo `locationScopeVersion` reabre o cálculo;
- [ ] OSRM/provider real não recebe payload de produto sem prova versionada;
- [ ] adapter desabilitado ou Maps indisponível usa fallback sem bypass;
- [ ] gates backend/Maps e smokes passam em SHAs limpos, com commits separados.

## Rollback

Desabilitar o adapter no backend e manter o REST trip-scoped/migration alocada
no freeze de `ready`. Nunca expor o
route planner nem remover auth para recuperar disponibilidade.
