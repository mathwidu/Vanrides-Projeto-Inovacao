# VR-APP-001-LOCATION-ADOPTION-001 — Adotar localização trip-scoped no app

## Resultado esperado

Passageiro, responsável, motorista e admin usam `viagemId` tipado, REST/STOMP
seguros e estados acessíveis. O app nunca mantém marcador inválido nem reconecta
com JWT expirado.

## Estado

`analyzed` — depende dos contratos backend/realtime/Maps e refresh atômico.

## Dependências

- `VR-SEC-003-LOCATION-REALTIME-001` concluído;
- `VR-MAP-001-LOCATION-S2S-001` concluído ou adapter comprovadamente desligado;
- `VR-SES-001-REFRESH-ROTATION-001` concluído;
- `VR-SES-003-APP-CREDENTIAL-AND-SECURE-STORAGE-001` concluído, herdando o
  coordinator de sessão e a remoção do fallback de senha/Basic;
- `VR-PRIV-002-MINIMIZATION-CONSENT-AUDIT-001` concluído;
- `VR-PGM-004-EVIDENCE-SEMANTIC-RATCHET-001` concluído;
- worktree app limpa no SHA congelado/revalidado.

## Escopo

- tipo/parâmetro nomeado de `viagemId` em gateways e todos os call sites;
- `200/204/404/409`, freshness e estados de janela/lease;
- `locationSharingExpiresAt`, `serverTime` e `locationScopeVersion` obrigatórios,
  com âncora no início da requisição e timer conservador que remove marcador no
  menor cutoff entre lease e freshness;
- destinos por usuário, ACK, tombstone e expiração do socket;
- provider serializado de token fresco e persistência coerente da sessão;
- remover timeline/histórico global do responsável;
- remover marker em stale terminal, revogação, reassign, lease ou sessão
  expirada;
- Semantics deduplicada, VoiceOver/TalkBack, scaling 200%, foco, contraste AA,
  48dp e redução de movimento;
- unit/widget/integration/E2E, analyze, gate e QA Log.

## Fora de escopo

- corrida visual A→B→A/respostas tardias, entregue pelo sucessor
  `VR-SEC-001-APP-DRIVER-SWITCH`;
- mudar backend/Maps, publicar loja, device real de produção ou promover.

## Critérios de aceite

- [ ] nenhum consumidor usa motorista como autorização ou tópico tenant-wide;
- [ ] ACK/tombstone/expiração não deixam marker antigo com aparência live;
- [ ] falta/invalidade de lease, `serverTime`, âncora ou versão falha fechado;
  timer local nunca corta depois do servidor mesmo offline e resposta/evento de
  `locationScopeVersion` anterior é ignorado;
- [ ] token expirado não entra em reconnect loop; token fresco refaz destinos;
- [ ] Semantics anuncia transições uma vez, nunca cada ponto;
- [ ] estados passam automação e matriz manual VoiceOver/TalkBack/scaling/foco/
  contraste/48dp/reduced motion;
- [ ] worktree limpa, analyze, gate sem E2E e E2E de impacto passam;
- [ ] QA Log/evidência registram SHA/app artifact sem token ou GPS bruto.

## Freeze PRIV-002

O inventário pai está congelado em `a3f7140e0a25fe5c440700146a42401500a87e4a`
(SHA-256 `d1dc70c65a0d03ff23950c55c6433378b4294a31c896fd095764dfe1d4be9891`).
Este card é owner do consumo/render acessível de D15/D16/F-PRIV-005 no Flutter.
REST, realtime, Maps, sessão e decisões humanas permanecem nos owners próprios;
PRIV-002 não habilita GPS, provider ou novo path de app.

## Rollback

Mostrar estado indisponível e desligar realtime. Não voltar a latest global,
histórico geral, tópico amplo ou token expirado.
