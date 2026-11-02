# VR-SEC-002-DRIVER-PASSENGER-MINIMUM-PROJECTION-001 — Remover contato do motorista dos payloads de passageiro

## Resultado esperado

Payloads de serviço, presença e tracking destinados a passageiro não carregam
e-mail/telefone do motorista sem consumidor e necessidade aprovados. Nome/ID e
demais dados operacionais também seguem allowlist por jornada.

## Estado

`analyzed` — finding `F-PRIV-018`; nenhum consumidor de e-mail/telefone foi
observado, mas o contrato campo a campo ainda precisa ser congelado.

## Dono operacional

`SEC/PRIV`, com `Produto`, `BE`, `APP` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-018`, `R-001`, `R-007`, `VR-SEC-002`, G1 e G2.

## Impact Analysis preliminar

### Mudanças técnicas

- mapear DTOs de serviço/presença/tracking que embutem DRV;
- remover contato no produtor e no parser Flutter, não apenas esconder na UI;
- preservar só identificação operacional aprovada por jornada;
- criar contract tests de ausência e atualizar fixtures/documentação.

### Contratos afetados

Payloads PAX de serviço, presença e tracking, records Dart e potenciais clientes
legados; nenhum schema precisa mudar.

### Riscos e mitigação

Cliente oculto pode usar contato. Fazer busca reprodutível, declarar quebra,
versionar se necessário e nunca manter campo “por precaução” sem owner.

### Validação mínima

Contract/WebMvc de campos, busca de consumidores, unit/widget tests, gate
backend/app e smokes PAX de presença/tracking no mesmo JAR/build.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- inventário negativo de consumidores de contato DRV concluído;
- matriz Produto/Privacidade decide a identificação operacional mínima.

## Escopo

- projeção DRV→PAX em serviços, presença e tracking.

## Fora de escopo

- comunicação direta, chat, suporte ou diretório público de motorista.

## Definition of Ready

- [ ] produtor/consumidores e campos mínimos congelados;
- [ ] versão/compatibilidade, paths, testes, smoke e rollback definidos;
- [ ] ausência de consumidor externo documentada ou tratada.

## Critérios de aceite preliminares

- [ ] e-mail/telefone de DRV não trafegam nem são parseados no fluxo PAX;
- [ ] campos operacionais remanescentes possuem consumidor e necessidade;
- [ ] jornada PAX de presença/tracking permanece funcional e acessível;
- [ ] fixtures e testes falham se contato reaparecer no payload;
- [ ] outro tenant/DRV não é enumerado por variação de resposta.

## Rollback

Desabilitar temporariamente a visualização dependente ou manter versão contratual
anterior autenticada e curta. Não reintroduzir contato sem decisão e teste.
