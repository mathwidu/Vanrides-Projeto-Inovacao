# VR-SEC-002-GUARDIAN-PASSENGER-MINIMUM-PROJECTION-001 — Minimizar perfil de passageiro entregue ao responsável

## Resultado esperado

Responsável recebe somente campos do passageiro vinculado necessários à jornada
aprovada. E-mail, telefone, endereço, CEP, coordenadas, nascimento e indicador
de menor são allowlisted individualmente; UI e API não recebem campos apenas
porque existem na entidade.

## Estado

`analyzed` — finding `F-PRIV-017`; a necessidade campo a campo e as jornadas de
contato/endereço ainda dependem de Produto e Privacidade.

## Dono operacional

`SEC/PRIV`, com `Produto`, `BE`, `APP`, `UX` e `QA` como revisores.

## Risco ou objetivo associado

`F-PRIV-017`, `R-001`, `R-007`, `VR-SEC-002`, G1, G2 e G6.

## Impact Analysis preliminar

### Mudanças técnicas

- congelar matriz campo × jornada para GUA vinculado;
- criar DTO backend e record Flutter específicos, sem parse de excedentes;
- adaptar lista, detalhe, localização e presença sem expor fallback oculto;
- tratar menor/coordenada residencial com regras mais restritivas;
- provar vínculo/tenant em toda entrada de detalhe.

### Contratos afetados

`GET /passageiros`/detalhe e consumidores guardian, payload, copy e estados
vazios; potencial acessibilidade da informação remanescente.

### Riscos e mitigação

Remover campo pode quebrar suporte familiar; mantê-lo pode expor dado sem
necessidade. Exigir decisão por campo, consumidor concreto e testes negativos.

### Validação mínima

WebMvc/contract por campo, widget tests sem dados excedentes, vínculos válido/
revogado/outro tenant, gate backend/app e smoke GUA com fixture sintética.

## Dependências

- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- contrato de vínculo GUA↔PAX existente e endpoints legados inventariados;
- decisão Produto/Privacidade campo a campo registrada sem base legal inferida.

## Escopo

- projeções e telas GUA de passageiro vinculado.

## Fora de escopo

- permitir acesso a passageiro não vinculado ou definir lifecycle final.

## Definition of Ready

- [ ] finalidade/necessidade/audiência de cada campo decidida pelo owner;
- [ ] payload/UI/compatibilidade, paths, testes, smoke e rollback congelados;
- [ ] comportamento de vínculo revogado e menor definido.

## Critérios de aceite preliminares

- [ ] GUA recebe apenas campos allowlisted do PAX vinculado no tenant;
- [ ] campos removidos não são parseados, logados nem mantidos em memória;
- [ ] vínculo revogado/outro tenant/ID trocado falham sem enumeração;
- [ ] lista/detalhe/localização/presença funcionam com projeção mínima;
- [ ] testes provam ausência explícita de cada campo proibido.

## Rollback

Desabilitar a tela/jornada que exige campo não aprovado e solicitar nova decisão.
Nunca restaurar a entidade inteira como resposta de compatibilidade.
